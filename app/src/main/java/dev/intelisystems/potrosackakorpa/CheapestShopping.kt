package dev.intelisystems.potrosackakorpa

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

data class CheapestItem(
    val item: ShoppingItem,
    val retailer: String?,
    val price: Long?,
    val originalPrice: Long?,
    val comparable: Boolean,
    val offerCount: Int,
    val priceBasis: String? = null,
    val outsideArea: Boolean = false,
    val destination: StoreDestination? = null
)

data class ShoppingComparison(val items: List<CheapestItem>, val nearbyStores: Int, val fallbackStatus: String = "not_requested")

fun cheapestShopping(items: List<ShoppingItem>, area: ShoppingArea): ShoppingComparison {
    check(BuildConfig.API_URL.isNotEmpty()) { "Adresa servera nije podešena." }
    val connection = URI("${BuildConfig.API_URL}/api/mobile/cheapest").toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 12_000
    connection.readTimeout = 25_000
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
    try {
        val request = JSONObject().put("ids", JSONArray(items.map { it.offer.id })).put("area", area.json()).put("nearest_fallback", true)
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(request.toString()) }
        val status = connection.responseCode
        Log.i("KorpaSync", "Cheapest shopping HTTP=$status items=${items.size}")
        if (status != 200) throw CatalogHttpError(status)
        val response = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        if (area.settlement.population < 5000 && area.radius != null) {
            check(response.getJSONObject("area").getString("mode") == "town_nearby") {
                "Server još ne podržava pretragu okoline malog mesta."
            }
        }
        return parseShoppingComparison(items, response)
    } finally {
        connection.disconnect()
    }
}

internal fun parseShoppingComparison(items: List<ShoppingItem>, response: JSONObject): ShoppingComparison {
    check(response.optString("fallback_status") in listOf("searched", "location_required")) {
        "Server još ne podržava pronalaženje najbliže ponude van oblasti."
    }
    val rows = response.getJSONArray("rows")
    val byId = (0 until rows.length()).associate { index ->
        val row = rows.getJSONObject(index)
        row.getString("id") to row
    }
    val compared = items.map { item ->
        val row = requireNotNull(byId[item.offer.id])
        if (row.optBoolean("unavailable")) {
            CheapestItem(item, null, null, null, false, 0)
        } else {
            val store = row.getJSONObject("store")
            val distance = if (store.isNull("distance_km")) "" else
                " · " + String.format(Locale.forLanguageTag("sr-Latn-RS"), "%.1f km", store.getDouble("distance_km"))
            val chain = row.getString("chain")
            val branch = store.optString("name", "").trim()
            val destination = StoreDestination(store.optString("source", ""), branch, chain,
                store.optString("address", "").trim(), store.optString("city", "").trim())
            val branchLabel = if (branch == chain) chain else "$chain · poslovnica $branch"
            val location = listOf(destination.address, destination.city).filter { it.isNotBlank() }.joinToString(", ")
            val label = listOf(branchLabel, location).filter { it.isNotBlank() }.joinToString(" · ") + distance
            CheapestItem(item, label, row.getLong("para"), row.getLong("original_para"),
                row.getBoolean("comparable"), row.getInt("offer_count"), row.getString("price_basis"),
                row.getBoolean("outside_area"), destination)
        }
    }
    return ShoppingComparison(compared, response.getJSONObject("area").getInt("nearby_stores"),
        response.getString("fallback_status"))
}
