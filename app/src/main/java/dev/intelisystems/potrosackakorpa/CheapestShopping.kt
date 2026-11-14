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
    val priceBasis: String? = null
)

data class ShoppingComparison(val items: List<CheapestItem>, val nearbyStores: Int)

fun cheapestShopping(items: List<ShoppingItem>, area: ShoppingArea): ShoppingComparison {
    check(BuildConfig.API_URL.isNotEmpty()) { "Adresa servera nije podešena." }
    val connection = URI("${BuildConfig.API_URL}/api/mobile/cheapest").toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 12_000
    connection.readTimeout = 25_000
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
    try {
        val request = JSONObject().put("ids", JSONArray(items.map { it.offer.id })).put("area", area.json())
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(request.toString()) }
        val status = connection.responseCode
        Log.i("KorpaSync", "Cheapest shopping HTTP=$status items=${items.size}")
        if (status != 200) throw CatalogHttpError(status)
        val response = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
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
                val branch = store.getString("name")
                val branchLabel = if (branch == chain) chain else "$chain · poslovnica $branch"
                val label = "$branchLabel · ${store.getString("address")}, ${store.getString("city")}$distance"
                CheapestItem(item, label, row.getLong("para"), row.getLong("original_para"),
                    row.getBoolean("comparable"), row.getInt("offer_count"), row.getString("price_basis"))
            }
        }
        return ShoppingComparison(compared, response.getJSONObject("area").getInt("nearby_stores"))
    } finally {
        connection.disconnect()
    }
}
