package dev.intelisystems.potrosackakorpa

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Filter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Button
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

private val settlementLetters = "абвгдежзијклмнопрстћуфхцчш"
    .zip("abvgdežzijklmnoprstćufhcčš").associate { it.first to it.second.toString() } +
    mapOf('ђ' to "đ", 'љ' to "lj", 'њ' to "nj", 'џ' to "dž")

internal fun placeKey(value: String) = searchKey(value.lowercase(Locale.ROOT)
    .map { settlementLetters[it] ?: it.toString() }.joinToString(""))

data class Settlement(val id: String, val name: String, val municipality: String, val population: Int) {
    val label get() = if (name == municipality) name else "$name · $municipality"
}

data class ShoppingArea(val settlement: Settlement, val latitude: Double? = null,
                        val longitude: Double? = null, val radius: Int? = null) {
    val label get() = if (settlement.population < 5000) settlement.name else "${settlement.name} · $radius km"
    fun json() = JSONObject().put("settlement_id", settlement.id).apply {
        if (latitude != null && longitude != null) {
            put("latitude", latitude)
            put("longitude", longitude)
        }
        if (radius != null) put("radius_km", radius)
    }
}

/** Only a confirmed search area is saved locally for the next visit. */
class NearbyPicker(private val activity: Activity, private val onSelected: (ShoppingArea) -> Unit) {
    companion object { const val PERMISSION_REQUEST = 401 }

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val manager = activity.getSystemService(LocationManager::class.java)
    private val place = AutoCompleteTextView(activity)
    private val street = EditText(activity)
    private val house = EditText(activity)
    private val preferences = activity.getSharedPreferences("shopping-area", Activity.MODE_PRIVATE)
    private var savedArea: ShoppingArea? = null
    private var savedStreet = ""
    private var savedHouse = ""
    private val radius = Spinner(activity)
    private val status = TextView(activity)
    private val streetFields = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val radii = listOf(1, 2, 5, 10, 20)
    private var places = emptyList<Settlement>()
    private var selected: Settlement? = null
    private var current: Location? = null
    private var currentStreet = ""
    private var currentHouse = ""
    private var currentSettlement: String? = null
    private var listener: LocationListener? = null
    private var alive = true
    private var locating = false
    private var resolving = false
    private val timeout = Runnable {
        stopLocation()
        status.text = "Lokacija nije pronađena. Proveri da li je uključena ili unesi mesto i ulicu."
    }
    private val dialog: AlertDialog

    init {
        val padding = (20 * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, padding)
        }
        content.addView(TextView(activity).apply {
            text = "Za naselja sa manje od 5.000 stanovnika pretražujemo celo mesto. Za veća mesta izaberi udaljenost od svoje ulice."
        })
        content.addView(Button(activity).apply {
            text = "Moja lokacija"
            setOnClickListener { requestLocation() }
        })
        place.hint = "Mesto"
        place.threshold = 2
        place.setSingleLine()
        content.addView(place)
        street.hint = "Naziv ulice"
        street.setSingleLine()
        streetFields.addView(street)
        house.hint = "Kućni broj (npr. 73 ili 73a)"
        house.setSingleLine()
        streetFields.addView(house)
        streetFields.addView(TextView(activity).apply { text = "Bez broja stana, sprata i ulaza." })
        streetFields.addView(TextView(activity).apply { text = "Udaljenost vazdušnom linijom" })
        radius.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, radii.map { "$it km" })
        radius.setSelection(1)
        streetFields.addView(radius)
        content.addView(streetFields)
        content.addView(status)
        status.text = "Učitavamo spisak naselja…"
        dialog = AlertDialog.Builder(activity).setTitle("Kupovina u okolini")
            .setView(ScrollView(activity).apply { addView(content) })
            .setPositiveButton("Pretraži", null).setNegativeButton("Otkaži", null).create()
        place.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != selected?.label) {
                    selected = null
                    streetFields.visibility = View.VISIBLE
                }
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
        dialog.setOnDismissListener {
            alive = false
            addressDialog?.dismiss()
            stopLocation()
            executor.shutdownNow()
        }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { search() }
        executor.execute {
            val loaded = runCatching {
                val data = activity.assets.open("settlements.json").bufferedReader().use { JSONObject(it.readText()) }
                    .getJSONArray("settlements")
                List(data.length()) {
                    val row = data.getJSONObject(it)
                    Settlement(row.getString("id"), row.getString("name"), row.getString("municipality"), row.getInt("population"))
                }.sortedBy { it.label }
            }
            ui {
                loaded.fold(onSuccess = {
                    places = it
                    val adapter = PlaceAdapter(it)
                    place.setAdapter(adapter)
                    place.setOnItemClickListener { _, _, position, _ -> select(adapter.getItem(position)!!) }
                    status.text = "Izaberi naselje iz liste. Broj stanovnika prema Popisu 2022."
                    restoreArea()
                }, onFailure = { status.text = "Spisak naselja nije mogao da se učita." })
            }
        }
    }

    fun dismiss() = dialog.dismiss()

    private fun ui(action: () -> Unit) = activity.runOnUiThread {
        if (alive && !activity.isDestroyed) action()
    }

    private fun select(value: Settlement) {
        selected = value
        place.setText(value.label, false)
        streetFields.visibility = if (value.population < 5000) View.GONE else View.VISIBLE
        status.text = if (value.population < 5000) "Pretražujemo celo naselje ${value.name}."
        else "Izaberi ulicu i udaljenost za pretragu."
    }

    fun onPermissionsResult() {
        if (!alive) return
        if (permitted()) locate()
        else status.text = "Lokacija nije odobrena. Možeš ručno izabrati mesto i ulicu."
    }

    private fun permitted() = activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED || activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (places.isEmpty() || resolving || locating) return
        if (permitted()) locate()
        else activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun locate() {
        stopLocation()
        if (!permitted()) return
        try {
            val precise = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .filter { it in manager.allProviders && (precise || it != LocationManager.GPS_PROVIDER) && manager.isProviderEnabled(it) }
            if (providers.isEmpty()) {
                status.text = "Uključi lokaciju na telefonu ili unesi mesto i ulicu."
                return
            }
            locating = true
            status.text = "Određujemo trenutnu lokaciju…"
            val recent = providers.mapNotNull { manager.getLastKnownLocation(it) }
                .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos in 0..30_000_000_000L }
                .minByOrNull { it.accuracy }
            if (recent != null) {
                foundLocation(recent)
                return
            }
            val updates = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos in 0..90_000_000_000L) {
                        foundLocation(location)
                    }
                }
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                @Deprecated("Legacy Android callback")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            listener = updates
            providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, updates, Looper.getMainLooper()) }
            handler.postDelayed(timeout, 20_000)
        } catch (_: SecurityException) {
            stopLocation()
            status.text = "Dozvola za lokaciju nije dostupna. Unesi mesto i ulicu."
        }
    }

    fun stopLocation() {
        listener?.let { runCatching { manager.removeUpdates(it) } }
        listener = null
        locating = false
        handler.removeCallbacks(timeout)
    }

    @Suppress("DEPRECATION")
    private fun foundLocation(location: Location) {
        stopLocation()
        current = location
        resolving = true
        status.text = "Prepoznajemo mesto i ulicu…"
        executor.execute {
            val address = runCatching {
                Geocoder(activity, Locale.forLanguageTag("sr-Latn-RS"))
                    .getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
            }.getOrNull()
            ui {
                resolving = false
                val city = address?.locality.orEmpty()
                val matches = places.filter { placeKey(it.name) == placeKey(city) }
                if (matches.size == 1) {
                    select(matches.single())
                    currentSettlement = matches.single().id
                } else {
                    currentSettlement = null
                    place.setText(city)
                    place.showDropDown()
                }
                currentStreet = address?.thoroughfare.orEmpty()
                street.setText(currentStreet)
                house.setText(address?.subThoroughfare.orEmpty())
                currentHouse = house.text.toString()
                status.text = "Potvrdi mesto i ulicu. Preciznost lokacije je približno ${location.accuracy.toInt()} m."
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun search() {
        if (resolving || locating) return
        val settlement = selected
        if (settlement == null) {
            status.text = "Izaberi mesto iz ponuđene liste."
            return
        }
        val point = current
        val recent = point != null &&
            SystemClock.elapsedRealtimeNanos() - point.elapsedRealtimeNanos in 0..90_000_000_000L
        if (settlement.population < 5000) {
            val trusted = if (recent && currentSettlement == settlement.id) point else null
            complete(ShoppingArea(settlement, trusted?.latitude, trusted?.longitude))
            return
        }
        val distance = radii[radius.selectedItemPosition]
        if (recent && currentSettlement == settlement.id && street.text.toString() == currentStreet && house.text.toString() == currentHouse) {
            complete(ShoppingArea(settlement, point!!.latitude, point.longitude, distance))
            return
        }
        val streetText = street.text.toString().trim()
        val houseText = house.text.toString().trim()
        val saved = savedArea
        if (saved != null && saved.settlement.id == settlement.id &&
            streetText == savedStreet && houseText == savedHouse) {
            complete(saved.copy(radius = distance))
            return
        }
        val validation = addressInputError(streetText, houseText)
        if (validation != null) { status.text = validation; return }
        resolving = true
        status.text = "Proveravamo ulicu i kućni broj…"
        val query = "$streetText $houseText, ${settlement.name}, Srbija"
        executor.execute {
            val outcome = runCatching {
                Geocoder(activity, Locale.forLanguageTag("sr-Latn-RS"))
                    .getFromLocationName(query, 10).orEmpty().filter {
                        it.countryCode == "RS" && it.hasLatitude() && it.hasLongitude() &&
                        placeKey(it.locality.orEmpty()) == placeKey(settlement.name) &&
                        placeKey(it.thoroughfare.orEmpty()) == placeKey(streetText) &&
                        placeKey(it.subThoroughfare.orEmpty()) == placeKey(houseText)
                    }
            }
            ui {
                resolving = false
                // Ignore an answer for fields the user changed during the request.
                if (selected?.id != settlement.id || street.text.toString().trim() != streetText ||
                    house.text.toString().trim() != houseText) return@ui
                outcome.fold(onSuccess = { found ->
                    if (found.isEmpty()) status.text = "Ne možemo da potvrdimo ovu adresu. Proveri ulicu i kućni broj ili koristi „Moja lokacija“. Adresa možda nije evidentirana u servisu."
                    else confirmAddress(found, settlement, distance)
                }, onFailure = {
                    status.text = "Servis za proveru adrese nije dostupan. Proveri internet ili koristi „Moja lokacija“."
                })
            }
        }
    }

    private var addressDialog: AlertDialog? = null

    private fun confirmAddress(addresses: List<Address>, settlement: Settlement, radius: Int) {
        addressDialog = AlertDialog.Builder(activity).setTitle("Potvrdi lokaciju za pretragu")
            .setItems(addresses.map { "${it.thoroughfare} ${it.subThoroughfare}, ${settlement.name}" }.toTypedArray()) { _, index ->
                if (alive) {
                    val address = addresses[index]
                    complete(ShoppingArea(settlement, address.latitude, address.longitude, radius))
                }
            }.setNegativeButton("Nazad", null).show()
    }

    private fun restoreArea() {
        runCatching {
            val json = JSONObject(preferences.getString("confirmed", null) ?: return)
            val settlement = places.firstOrNull { it.id == json.getString("settlement_id") } ?: return
            savedStreet = json.optString("street")
            savedHouse = json.optString("house")
            savedArea = ShoppingArea(settlement,
                if (json.isNull("latitude")) null else json.getDouble("latitude"),
                if (json.isNull("longitude")) null else json.getDouble("longitude"),
                if (json.isNull("radius_km")) null else json.getInt("radius_km"))
            select(settlement)
            street.setText(savedStreet)
            house.setText(savedHouse)
            radius.setSelection(radii.indexOf(savedArea?.radius).takeIf { it >= 0 } ?: 1)
            status.text = "Sačuvana lokacija prethodne pretrage. Za novu trenutnu lokaciju izaberi „Moja lokacija“."
        }
    }

    private fun complete(area: ShoppingArea) {
        preferences.edit().putString("confirmed", area.json()
            .put("street", street.text.toString().trim()).put("house", house.text.toString().trim()).toString()).apply()
        dialog.dismiss()
        addressDialog?.dismiss()
        onSelected(area)
    }

    private inner class PlaceAdapter(private val all: List<Settlement>) :
        ArrayAdapter<Settlement>(activity, android.R.layout.simple_dropdown_item_1line) {
        private var filtered = emptyList<Settlement>()
        override fun getCount() = filtered.size
        override fun getItem(position: Int) = filtered[position]
        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
            (super.getView(position, convertView, parent) as TextView).apply { text = getItem(position).label }
        override fun getFilter() = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val term = placeKey(constraint?.toString().orEmpty().trim())
                val matches = if (term.length < 2) emptyList() else rankSettlements(all, term)
                return FilterResults().apply { values = matches; count = matches.size }
            }
            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                filtered = results.values as List<Settlement>
                notifyDataSetChanged()
            }
            override fun convertResultToString(resultValue: Any?) = (resultValue as Settlement).label
        }
    }
}

internal fun rankSettlements(all: List<Settlement>, term: String): List<Settlement> =
    all.filter { placeKey(it.label).contains(placeKey(term)) }
        .sortedWith(compareBy<Settlement> { placeKey(it.name) != placeKey(term) }
            .thenBy { !placeKey(it.name).startsWith(placeKey(term)) }
            .thenBy { placeKey(it.name) }.thenBy { placeKey(it.municipality) }).take(50)

internal fun addressInputError(street: String, house: String): String? = when {
    street.isBlank() -> "Unesi naziv ulice ili koristi „Moja lokacija“."
    '/' in street || '/' in house -> "Unesi samo ulicu i kućni broj, bez broja stana (npr. Cara Dušana, 73)."
    !Regex("[0-9]+[a-zA-Zа-яА-ЯčćšžđČĆŠŽĐ]?").matches(house) ->
        "Unesi kućni broj, npr. 73 ili 73a, bez stana, sprata i ulaza."
    else -> null
}
