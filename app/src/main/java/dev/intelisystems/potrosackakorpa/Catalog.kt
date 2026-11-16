package dev.intelisystems.potrosackakorpa

import android.util.Log
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream

data class Category(val key: String, val name: String, val icon: String = "🛒")

private data class CategoryGroup(val category: Category, val members: List<String>)
data class Offer(val id: String, val name: String, val retailer: String, val unit: String?, val price: Long, val minPrice: Long = price, val maxPrice: Long = price, val barcode: String? = null)
data class ShoppingItem(val offer: Offer, val quantity: Int, val available: Boolean)

class CatalogHttpError(val status: Int) : IOException("Catalog HTTP $status")

private val accents = Regex("\\p{M}+")
fun searchKey(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(accents, "").replace('đ', 'd')

class KorpaApplication : Application() {
    val catalog by lazy { Catalog(this) }
}

class Catalog(private val context: Context) : SQLiteOpenHelper(context, prepareDatabase(context), null, 2) {
    private companion object {
        fun unpackSeed(context: Context): File? {
            if (context.assets.list("")?.contains("catalog.db.gzip") != true) return null
            val file = File.createTempFile("catalog-seed-", ".db", context.cacheDir)
            try {
                Log.i("KorpaSync", "Unpacking bundled catalog")
                GZIPInputStream(context.assets.open("catalog.db.gzip")).use { input ->
                    file.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                Log.i("KorpaSync", "Bundled catalog unpacked bytes=${file.length()}")
                return file
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        }

        fun prepareDatabase(context: Context): String {
            val database = context.getDatabasePath("korpa.db")
            if (database.exists()) return database.absolutePath
            val seed = unpackSeed(context) ?: return database.absolutePath
            try {
                check(database.parentFile!!.isDirectory || database.parentFile!!.mkdirs())
                // Both paths are on the application's internal filesystem.
                check(seed.renameTo(database)) { "Početni katalog nije mogao da se sačuva." }
                Log.i("KorpaSync", "Bundled catalog installed; checking only newer server changes")
            } finally {
                seed.delete()
            }
            return database.absolutePath
        }
    }

    private val categoryGroups by lazy {
        val json = context.assets.open("category-groups.json").bufferedReader().use { JSONArray(it.readText()) }
        List(json.length()) { index ->
            val group = json.getJSONObject(index)
            val members = group.getJSONArray("categories")
            CategoryGroup(Category(group.getString("id"), group.getString("name"), group.getString("icon")),
                List(members.length()) { members.getString(it) })
        }
    }

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        context.assets.open("catalog-schema.sql").bufferedReader().use { reader ->
            reader.readText().split(';').filter { it.isNotBlank() }.forEach(db::execSQL)
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        val ready = db.rawQuery("SELECT version FROM catalog_version WHERE id = 1", null).use { it.moveToFirst() }
        // Also bootstrap installations whose first network download never completed.
        if (ready) return
        val seed = unpackSeed(context) ?: return
        try {
            db.execSQL("ATTACH DATABASE ? AS seed", arrayOf(seed.absolutePath))
            try {
                db.transaction {
                    execSQL("INSERT OR REPLACE INTO offers SELECT * FROM seed.offers")
                    execSQL("INSERT OR REPLACE INTO catalog_version SELECT * FROM seed.catalog_version")
                }
                Log.i("KorpaSync", "Bundled catalog imported into existing database; shopping list retained")
            } finally {
                db.execSQL("DETACH DATABASE seed")
            }
        } finally {
            seed.delete()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL("ALTER TABLE offers ADD COLUMN product_key TEXT")
            db.execSQL("CREATE INDEX offers_product_price ON offers(product_key, price)")
            // Reload the upgraded bundled cache. The separate shopping table stays intact.
            db.execSQL("DELETE FROM offers")
            db.execSQL("DELETE FROM catalog_version")
        } else error("Nedostaje migracija baze sa verzije $oldVersion na $newVersion.")
    }

    fun version(): String? = readableDatabase.rawQuery(
        "SELECT version FROM catalog_version WHERE id = 1", null
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun categories(): List<Category> {
        val available = readableDatabase.rawQuery("SELECT DISTINCT category FROM offers", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val recognized = categoryGroups.flatMap { it.members }.toSet()
        return buildList {
            categoryGroups.filter { group -> group.members.any { it in available } }.forEach { add(it.category) }
            if ((available - recognized).isNotEmpty()) add(Category("group-other", "Ostalo"))
        }
    }

    fun subcategories(group: Category): List<Category> {
        if (group.key == "group-2") return listOf(
            Category("drink-coffee", "Kafa"), Category("drink-tea", "Čaj"),
            Category("drink-soft", "Bezalkoholna pića"), Category("drink-alcohol", "Alkoholna pića"))
        val members = categoryGroups.firstOrNull { it.category.key == group.key }?.members ?: return emptyList()
        return members.map { key -> Category("category:$key", key.replaceFirstChar { it.titlecase(Locale.forLanguageTag("sr-Latn-RS")) }) }
    }

    private fun searchScope(category: String?): Triple<String, String, List<String>> {
        val beverage = category?.startsWith("drink-") == true
        val group = categoryGroups.firstOrNull { it.category.key == category || (beverage && it.category.key == "group-2") }
        val members = when {
            category == "group-other" -> categoryGroups.flatMap { it.members }
            group != null -> group.members
            else -> listOf(category.orEmpty().removePrefix("category:"))
        }
        val operator = if (category == "group-other") "NOT IN" else "IN"
        val placeholders = members.joinToString(",") { "?" }
        val beverageFilter = if (category == null) "1=1" else beverageSql(category)
        val categoryFilter = if (category == null) "1=1" else "category $operator ($placeholders)"
        return Triple(categoryFilter, beverageFilter, if (category == null) emptyList() else members)
    }

    data class SearchResults(val total: Long, val matched: Long, val offers: List<Offer>)

    fun searchOffers(category: String?, query: String, offset: Int,
                     cancellation: android.os.CancellationSignal, onTotal: (Long) -> Unit): SearchResults {
        val (categoryFilter, beverageFilter, members) = searchScope(category)
        val (matchFilter, matchArguments) = searchTerms(query)
        val db = readableDatabase
        // Counts and page refer to the same catalog even if a sync finishes during search.
        db.beginTransactionNonExclusive()
        try {
            val total = db.rawQuery("SELECT count(*) FROM offers WHERE $categoryFilter AND ($beverageFilter)",
                members.toTypedArray(), cancellation).use { it.moveToFirst(); it.getLong(0) }
            onTotal(total)
            val matched = db.rawQuery("""
                SELECT count(*) FROM offers WHERE $categoryFilter AND ($beverageFilter)
                AND ($matchFilter)
            """.trimIndent(), (members + matchArguments).toTypedArray(), cancellation).use {
                it.moveToFirst(); it.getLong(0)
            }
            val result = SearchResults(total, matched, offers(category, query, offset, cancellation))
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }

    fun offers(category: String?, query: String, offset: Int, cancellation: android.os.CancellationSignal? = null): List<Offer> {
        val (matchFilter, matchArguments) = searchTerms(query)
        val (categoryFilter, beverageFilter, members) = searchScope(category)
        return readableDatabase.rawQuery("""
            SELECT o.id, o.name, o.retailer, o.unit, o.price,
                COALESCE((SELECT MIN(p.price) FROM offers p WHERE p.product_key = o.product_key), o.price),
                COALESCE((SELECT MAX(p.price) FROM offers p WHERE p.product_key = o.product_key), o.price), o.product_key
            FROM (
                SELECT * FROM offers WHERE $categoryFilter
                AND ($beverageFilter)
                AND ($matchFilter)
                ORDER BY name_key, id LIMIT 31 OFFSET ?
            ) o ORDER BY o.name_key, o.id
        """.trimIndent(), (members + matchArguments + listOf(offset.toString())).toTypedArray(), cancellation).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(Offer(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getLong(4), cursor.getLong(5), cursor.getLong(6), cursor.getString(7)?.removePrefix("gtin:")))
                }
            }
        }
    }

    fun offersForBarcode(code: String): List<Offer> {
        if (!validProductCode(code)) return emptyList()
        return readableDatabase.rawQuery("""
            SELECT id,name,retailer,unit,price FROM offers
            WHERE product_key = ? ORDER BY price,retailer,id
        """.trimIndent(), arrayOf("gtin:" + code.padStart(14, '0'))).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Offer(cursor.getString(0), cursor.getString(1),
                    cursor.getString(2), cursor.getString(3), cursor.getLong(4), barcode = code))
            }
        }
    }

    fun shoppingList(): List<ShoppingItem> = readableDatabase.rawQuery("""
        SELECT s.id, COALESCE(o.name, s.name), COALESCE(o.retailer, s.retailer),
               CASE WHEN o.id IS NULL THEN s.unit ELSE o.unit END, o.price, s.quantity
        FROM shopping s LEFT JOIN offers o ON o.id = s.id ORDER BY s.rowid
    """.trimIndent(), null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val offer = Offer(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getLong(4))
                add(ShoppingItem(offer, cursor.getInt(5), !cursor.isNull(4)))
            }
        }
    }

    fun add(offer: Offer) = writableDatabase.transaction {
        execSQL("""
            INSERT OR IGNORE INTO shopping (id, name, retailer, unit, quantity) VALUES (?, ?, ?, ?, 0)
        """.trimIndent(), arrayOf<Any?>(offer.id, offer.name, offer.retailer, offer.unit))
        execSQL("UPDATE shopping SET quantity = MIN(quantity + 1, 999) WHERE id = ?", arrayOf(offer.id))
    }

    fun setQuantity(id: String, quantity: Int) {
        if (quantity <= 0) writableDatabase.delete("shopping", "id = ?", arrayOf(id))
        else writableDatabase.execSQL("UPDATE shopping SET quantity = ? WHERE id = ?", arrayOf<Any>(quantity.coerceAtMost(999), id))
    }

    @Synchronized
    fun sync(onProgress: (String) -> Unit = {}): Boolean {
        val baseUrl = BuildConfig.API_URL
        check(baseUrl.isNotEmpty()) { "Adresa servera nije podešena." }
        val since = version().orEmpty()
        Log.i("KorpaSync", "Sync started server=$baseUrl savedVersion=$since")
        onProgress("Povezujemo se sa serverom…")
        repeat(3) {
            try {
                return download(baseUrl, since, onProgress)
            } catch (_: CatalogChanged) {
                Log.w("KorpaSync", "Server catalog changed; restarting download")
                onProgress("Katalog je promenjen. Ponavljamo preuzimanje…")
            }
        }
        error("Katalog se trenutno osvežava. Pokušaj ponovo.")
    }

    private fun download(baseUrl: String, since: String, onProgress: (String) -> Unit): Boolean {
        var pages = 0
        var received = 0
        val staging = File.createTempFile("catalog-", ".jsonl", context.cacheDir)
        try {
            val state = CatalogPageState(since)
            var after: String?
            staging.bufferedWriter().use { file ->
                do {
                    val params = mapOf("since" to since, "target" to state.version, "after" to state.cursor)
                        .filterValues { it.isNotEmpty() }
                        .entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
                    val connection = URI("$baseUrl/api/mobile/catalog?$params").toURL().openConnection() as HttpURLConnection
                    connection.connectTimeout = 12_000
                    connection.readTimeout = 25_000
                    val page = try {
                        val status = connection.responseCode
                        Log.i("KorpaSync", "GET catalog page=${pages + 1} HTTP=$status")
                        when (status) {
                            304 -> {
                                state.unchanged()
                                Log.i("KorpaSync", "Catalog unchanged")
                                return false
                            }
                            409 -> throw CatalogChanged()
                            200 -> JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                            else -> throw CatalogHttpError(connection.responseCode)
                        }
                    } finally {
                        connection.disconnect()
                    }
                    after = if (page.isNull("next")) null else page.getString("next")
                    state.accept(page.getString("version"), page.getBoolean("reset"), after, page.getJSONArray("removed").length())
                    file.appendLine(page.toString())
                    pages++
                    received += page.getJSONArray("updates").length()
                    Log.i("KorpaSync", "Received page=$pages updates=${page.getJSONArray("updates").length()} removed=${page.getJSONArray("removed").length()} totalUpdates=$received")
                    onProgress("Preuzeto $received ponuda · stranica $pages")
                } while (after != null)
            }
            Log.i("KorpaSync", "Saving catalog reset=${state.reset} pages=$pages updates=$received bytes=${staging.length()}")
            onProgress("Čuvamo $received ponuda na telefonu…")
            apply(staging, state.version, state.reset)
            Log.i("KorpaSync", "Catalog committed version=${state.version}")
            return true
        } finally {
            staging.delete()
        }
    }

    private fun apply(staging: File, version: String, reset: Boolean) = writableDatabase.transaction {
        // Save the last visible label before removing any saved offer.
        execSQL("""
            UPDATE shopping SET
                name = (SELECT name FROM offers WHERE offers.id = shopping.id),
                retailer = (SELECT retailer FROM offers WHERE offers.id = shopping.id),
                unit = (SELECT unit FROM offers WHERE offers.id = shopping.id)
            WHERE EXISTS (SELECT 1 FROM offers WHERE offers.id = shopping.id)
        """.trimIndent())
        if (reset) delete("offers", null, null)
        staging.forEachLine { line ->
            val page = JSONObject(line)
            val removed = page.getJSONArray("removed")
            for (i in 0 until removed.length()) delete("offers", "id = ?", arrayOf(removed.getString(i)))
            val updated = page.getJSONArray("updates")
            for (i in 0 until updated.length()) {
                val offer = updated.getJSONObject(i)
                val name = offer.getString("name")
                val category = offer.getString("category")
                val retailer = offer.getString("retailer")
                val values = ContentValues().apply {
                    put("id", offer.getString("id"))
                    val product = offer.optString("product_key").takeIf { it.startsWith("gtin:") }
                    put("product_key", product?.let { "gtin:" + it.removePrefix("gtin:").padStart(14, '0') })
                    put("name", name)
                    put("retailer", retailer)
                    put("unit", if (offer.isNull("unit")) null else offer.getString("unit"))
                    put("price", offer.getLong("para"))
                    put("category", searchKey(category))
                    put("category_label", category.replaceFirstChar { it.titlecase(Locale.forLanguageTag("sr-Latn-RS")) })
                    put("name_key", searchKey(name))
                    put("retailer_key", searchKey("$retailer ${offer.getString("format")} ${offer.getString("chain")}"))
                }
                insertWithOnConflict("offers", null, values, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
            }
        }
        execSQL("INSERT OR REPLACE INTO catalog_version VALUES (1, ?)", arrayOf(version))
    }

    private inline fun <T> SQLiteDatabase.transaction(action: SQLiteDatabase.() -> T): T {
        beginTransactionNonExclusive()
        try {
            val result = action()
            setTransactionSuccessful()
            return result
        } finally {
            endTransaction()
        }
    }

    private class CatalogChanged : Exception()
}

// Source categories combine coffee, tea and soft drinks; split only those known beverage categories.
internal fun beverageSql(category: String): String {
    val nonAlcoholic = "category IN ('bezalkoholna pica, kafa, caj', 'безалкохолна пића, кафа, чај')"
    val coffee = "(name_key LIKE '%kafa%' OR name_key LIKE '%kafe%' OR name_key LIKE '%coffee%' OR name_key LIKE '%espresso%' OR name_key LIKE '%nescafe%' OR name_key LIKE '%cappuccino%' OR name_key LIKE '%кафа%' OR name_key LIKE '%кафе%')"
    val tea = "(name_key LIKE '%caj%' OR name_key LIKE '%tea%' OR name_key LIKE '%чај%')"
    val liquor = "(name_key LIKE '%liker%' OR name_key LIKE '%liqueur%' OR name_key LIKE '%ликер%')"
    return when (category) {
        "drink-coffee" -> "$nonAlcoholic AND $coffee AND NOT $tea AND NOT $liquor"
        "drink-tea" -> "$nonAlcoholic AND $tea AND NOT $liquor"
        "drink-soft" -> "$nonAlcoholic AND NOT $coffee AND NOT $tea AND NOT $liquor"
        "drink-alcohol" -> "category = 'alkoholna pica'"
        else -> "1=1"
    }
}
