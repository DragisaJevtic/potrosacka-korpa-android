package dev.intelisystems.potrosackakorpa

import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.WindowInsets
import android.widget.*
import org.json.JSONObject
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors

class CheapestActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var body: LinearLayout
    private var picker: NearbyPicker? = null
    private var area: ShoppingArea? = null
    private var generation = 0
    private val forest = KorpaUi.forest
    private val ink = KorpaUi.ink
    private val muted = KorpaUi.muted
    private val warning = KorpaUi.warning
    private val money = NumberFormat.getNumberInstance(Locale.forLanguageTag("sr-Latn-RS")).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = column().apply { setBackgroundColor(KorpaUi.paper) }
        val toolbar = column().apply {
            setPadding(dp(20), dp(13), dp(20), dp(10))
            addView(KorpaUi.button(this@CheapestActivity, "← Spisak") { finish() })
            addView(label("Najjeftinija kupovina", 28, ink, true).apply {
                if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            }, margins(12))
        }
        root.addView(toolbar)
        body = column()
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
                insets
            }
        } else {
            window.statusBarColor = KorpaUi.paper
            window.navigationBarColor = if (Build.VERSION.SDK_INT >= 27) KorpaUi.paper else forest
        }
        val restored = state?.getString("area")?.let { value -> runCatching {
            val json = JSONObject(value)
            ShoppingArea(Settlement(json.getString("settlement_id"), json.getString("name"),
                json.getString("municipality"), json.getInt("population")),
                if (json.isNull("latitude")) null else json.getDouble("latitude"),
                if (json.isNull("longitude")) null else json.getDouble("longitude"),
                if (json.isNull("radius_km")) null else json.getInt("radius_km"))
        }.getOrNull() }
        if (restored == null) chooseArea() else load(restored)
    }
    private fun chooseArea() {
        generation++; area = null; picker?.dismiss(); body.removeAllViews()
        picker = NearbyPicker(this, body) { selected -> load(selected) }
    }
    private fun load(selected: ShoppingArea) {
        area = selected
        picker?.dismiss(); picker = null
        val token = ++generation
        body.removeAllViews()
        body.addView(KorpaUi.button(this, "Promeni mesto ili udaljenost") { chooseArea() }, margins(4).apply { marginStart = dp(20); marginEnd = dp(20) })
        val content = column().apply { setPadding(dp(20), dp(12), dp(20), dp(20)) }
        body.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(ProgressBar(this)); content.addView(label("Tražimo najniže cene za tvoj spisak…", 16, muted))
        worker.execute {
            val outcome = runCatching {
                val items = (application as KorpaApplication).catalog.shoppingList()
                if (items.isEmpty()) ShoppingComparison(emptyList(), 0) else cheapestShopping(items, selected)
            }
            runOnUiThread {
                if (isDestroyed || isFinishing || token != generation) return@runOnUiThread
                content.removeAllViews()
                outcome.fold(onSuccess = { result ->
                    if (result.items.isEmpty()) content.addView(label("Spisak je prazan. Vrati se i dodaj proizvode.", 16, ink))
                    else content.addView(results(result, selected))
                }, onFailure = {
                    content.addView(label("Poređenje nije učitano. Proveri internet i pokušaj ponovo.", 16, warning))
                    content.addView(KorpaUi.button(this, "Pokušaj ponovo", true) { load(selected) }, margins(12))
                })
            }
        }
    }
    private fun results(comparison: ShoppingComparison, area: ShoppingArea): LinearLayout {
        val offers = comparison.items
        val available = offers.filter { it.price != null }
        val total = available.sumOf { requireNotNull(it.price) * it.item.quantity }
        val original = available.sumOf { requireNotNull(it.originalPrice) * it.item.quantity }
        val content = column()
        if (comparison.nearbyStores == 0) {
            content.addView(label("Za izabranu oblast još nemamo poslovnice sa potvrđenom lokacijom.", 15, warning), margins(6))
        }
        content.addView(label(if (available.isEmpty()) "Nema pronađenih ponuda"
                else if (available.size == offers.size) "Ukupno: ${rsd(total)}"
            else "Dostupni artikli: ${rsd(total)}", 21, forest, true))
        content.addView(label(area.label, 15, ink, true), margins(5))
        val difference = original - total
        if (available.isNotEmpty()) content.addView(label(if (difference >= 0) "Ušteda za pronađene artikle: ${rsd(difference)}"
            else "Skuplje od izabranih ponuda: ${rsd(-difference)}", 17, forest, true), margins(5))
        content.addView(label("Procena po cenovnicima, bez troškova prevoza. " +
            if (area.settlement.population < 5000 && area.radius != null) "Celo izabrano mesto i okolina sa poznatim koordinatama. Udaljenost je vazdušna linija."
            else if (area.settlement.population < 5000) "Pretraga samo izabranog mesta."
            else "Poslovnice u izabranom radijusu sa poznatim koordinatama. Udaljenost je vazdušna linija.", 13, muted), margins(6))
        if (available.any { it.outsideArea }) {
            content.addView(label("Ukupan iznos uključuje i ponude van izabrane oblasti. Za te artikle biramo najbližu poznatu poslovnicu, bez obzira na cenu.", 14, warning), margins(8))
        }
        if (comparison.fallbackStatus == "location_required") {
            content.addView(label("Potvrdi lokaciju da bismo za artikle bez ponude u mestu pronašli najbližu ponudu van mesta.", 14, warning), margins(8))
        }
        content.addView(label("Objavljen cenovnik ne potvrđuje da je artikal trenutno na stanju.", 13, muted), margins(6))
        available.sortedBy { it.outsideArea }.groupBy { it.outsideArea to it.destination }.forEach { (group, products) ->
            val (outsideArea, destination) = group
            val card = KorpaUi.card(this)
            content.addView(card, margins(16))
            val retailer = products.first().retailer
            if (outsideArea) card.addView(label("Najbliža poznata ponuda van izabrane oblasti", 16, warning, true), margins(0))
            card.addView(label(retailer.orEmpty(), 18, ink, true), margins(if (outsideArea) 8 else 0))
            card.addView(label("Ukupno u prodavnici: ${rsd(products.sumOf { requireNotNull(it.price) * it.item.quantity })}", 16, forest, true), margins(8))
            destination?.let { store ->
                card.addView(KorpaUi.button(this, "Navigacija", true) { openStoreMap(store) }.apply {
                    contentDescription = "Navigacija do ${store.name}, ${store.city}"
                }, margins(8))
                if (!store.hasAddress) card.addView(label("Adresa nije dostupna — otvara se pretraga prodavnice u mestu.", 12, muted), margins(3))
            }
            products.forEach { result ->
                card.addView(View(this).apply { setBackgroundColor(Color.rgb(228, 233, 226)) },
                    LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12) })
                card.addView(label(result.item.offer.name, 15, ink, true), margins(9))
                card.addView(label("${result.item.quantity} × ${rsd(requireNotNull(result.price))} = ${rsd(result.price * result.item.quantity)}",
                    14, forest), margins(3))
                card.addView(label(if (result.priceBasis == "chain") "Cena lanca" else "Cenovnik poslovnice", 12, muted), margins(3))
                if (!result.comparable) {
                    card.addView(label("Prikazana cena izabrane ponude. Nema pouzdanog barkoda za poređenje.", 12, muted), margins(3))
                } else if (result.offerCount == 1) {
                    card.addView(label(if (result.outsideArea) "Jedina poznata ponuda van oblasti." else "Jedina poznata ponuda u oblasti.", 12, muted), margins(3))
                }
            }
        }
        offers.filter { it.price == null }.forEach { result ->
            val explanation = if (comparison.fallbackStatus == "searched")
                "nema poznate ponude u oblasti niti u drugim poslovnicama sa poznatom lokacijom."
                else "nema potvrđene ponude u izabranoj okolini."
            content.addView(label("${result.item.offer.name} — $explanation", 14, warning), margins(12))
        }
        return content
    }
    private fun openStoreMap(store: StoreDestination) {
        val uri = Uri.parse(store.mapsUrl())
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Nije pronađena aplikacija za otvaranje mape.", Toast.LENGTH_LONG).show()
            }
        }
    }
    override fun onSaveInstanceState(state: Bundle) {
        area?.let { selected -> state.putString("area", selected.json().put("name", selected.settlement.name)
            .put("municipality", selected.settlement.municipality).put("population", selected.settlement.population).toString()) }
        super.onSaveInstanceState(state)
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == NearbyPicker.PERMISSION_REQUEST) picker?.onPermissionsResult()
    }
    override fun onStop() { picker?.stopLocation(); super.onStop() }
    override fun onDestroy() { generation++; picker?.dismiss(); worker.shutdownNow(); super.onDestroy() }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun margins(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun rsd(value: Long) = money.format(BigDecimal.valueOf(value, 2)) + " RSD"
    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) =
        KorpaUi.label(this, value, size, color, bold)
}
