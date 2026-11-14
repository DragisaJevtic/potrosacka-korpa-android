package dev.intelisystems.potrosackakorpa

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.widget.*
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.ScanIntentResult
import com.google.zxing.oned.UPCEReader
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors

/** Camera images stay with the scanner; only numeric product codes reach our API. */
class BarcodeLookup(private val activity: Activity, private val launchScanner: (ScanOptions) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor()
    private var dialog: AlertDialog? = null
    private var scanning = false
    private var closed = false
    private var request = 0
    private val money = NumberFormat.getNumberInstance(Locale.forLanguageTag("sr-Latn-RS")).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }

    fun show() {
        if (closed || scanning) return
        dialog?.dismiss()
        scan()
    }

    private fun scan() {
        if (scanning) return
        scanning = true
        val options = ScanOptions()
            .setDesiredBarcodeFormats("EAN_13", "EAN_8", "UPC_A", "UPC_E", "ITF", "CODE_128", "QR_CODE")
            .setPrompt("Usmeri kameru ka bar-kodu proizvoda")
            .setCaptureActivity(PortraitScannerActivity::class.java)
            .setOrientationLocked(true)
            .setBeepEnabled(false)
            .setBarcodeImageEnabled(false)
        try {
            launchScanner(options)
        } catch (_: Exception) {
            scanning = false
            message("Kamera nije dostupna. Proveri dozvolu za kameru i pokušaj ponovo.")
        }
    }

    fun onScanResult(result: ScanIntentResult) {
        scanning = false
        if (closed) return
        val contents = result.contents
        if (contents == null) {
            if (result.originalIntent?.getBooleanExtra("MISSING_CAMERA_PERMISSION", false) == true) {
                message("Kamera nije dozvoljena. Dozvoli kameru u podešavanjima aplikacije.")
            }
            return
        }
        val code = if (result.formatName == "UPC_E" && contents.length == 8 && contents.all { it in '0'..'9' })
            UPCEReader.convertUPCEtoUPCA(contents) else contents
        lookup(code)
    }

    private fun lookup(raw: String) {
        val code = raw.trim()
        if (!validProductCode(code)) {
            message("Skenirani kod nije ispravan EAN/UPC broj. QR linkovi i interni kodovi prodavnica nisu podržani.")
            return
        }
        val token = ++request
        dialog?.dismiss()
        val content = column().apply { addView(ProgressBar(activity)); addView(text("Tražimo cene za $code…")) }
        val resultDialog = AlertDialog.Builder(activity).setTitle("Cene proizvoda").setView(content)
            .setPositiveButton("Novi kod") { _, _ -> show() }.setNegativeButton("Zatvori", null).create()
        dialog = resultDialog
        resultDialog.show()
        worker.execute {
            val outcome = runCatching { fetch(code) }
            activity.runOnUiThread {
                if (closed || token != request || !resultDialog.isShowing) return@runOnUiThread
                content.removeAllViews()
                outcome.fold(onSuccess = { offers ->
                    val rows = column()
                    rows.addView(text("Bar-kod: $code"))
                    if (offers.isEmpty()) rows.addView(text("Za ovaj bar-kod trenutno nemamo cenu u katalogu. Pokušaj pretragu po nazivu u kategorijama."))
                    else {
                        rows.addView(text("Najniže objavljene cene po prodajnom formatu. Prikaz nije ograničen na tvoju lokaciju; dostupnost u poslovnici nije potvrđena."))
                        offers.groupBy { it.retailer }.values.map { group -> group.minBy { it.para } }
                            .sortedBy { it.para }.forEach { offer ->
                                rows.addView(text(offer.retailer, true))
                                rows.addView(text(offer.name))
                                rows.addView(text("${money.format(java.math.BigDecimal.valueOf(offer.para, 2))} RSD", true))
                            }
                    }
                    content.addView(ScrollView(activity).apply { addView(rows) })
                }, onFailure = {
                    content.addView(text("Cene nisu učitane. Proveri internet i pokušaj ponovo."))
                    content.addView(Button(activity).apply { text = "Pokušaj ponovo"; setOnClickListener { lookup(code) } })
                })
            }
        }
    }

    private data class Price(val name: String, val retailer: String, val para: Long)

    private fun fetch(code: String): List<Price> {
        check(BuildConfig.API_URL.isNotEmpty())
        // GTIN may be published with leading zeroes or as UPC-A / EAN-13.
        val canonical = code.padStart(14, '0')
        val variants = listOf(8, 12, 13, 14).filter { length -> canonical.take(14 - length).all { it == '0' } }
            .map { canonical.takeLast(it) }.distinct()
        return variants.flatMap { variant ->
            val connection = URI("${BuildConfig.API_URL}/api/compare/$variant").toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 25_000
            try {
                check(connection.responseCode == 200)
                val rows = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("rows")
                List(rows.length()) { i ->
                    val row = rows.getJSONObject(i)
                    Price(row.getString("name"), row.optString("retailer_label").takeUnless { it.isBlank() || it == "null" }
                        ?: row.getString("retailer_format"), row.getLong("effective_para"))
                }
            } finally { connection.disconnect() }
        }
    }

    private fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
    }
    private fun text(value: String, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = if (bold) 18f else 15f
        setTextColor(Color.rgb(26, 42, 34))
        setPadding(0, 8, 0, 8)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
    }
    private fun message(value: String) {
        if (!closed) Toast.makeText(activity, value, Toast.LENGTH_LONG).show()
    }
    fun close() { closed = true; request++; dialog?.dismiss(); worker.shutdownNow() }
}

internal fun validProductCode(code: String): Boolean {
    if (code.length !in listOf(8, 12, 13, 14) || code.any { it !in '0'..'9' } || code.all { it == '0' }) return false
    if (code.padStart(14, '0').substring(1, 3).toInt() in 20..29) return false
    return code.reversed().mapIndexed { index, c -> (c - '0') * if (index % 2 == 0) 1 else 3 }.sum() % 10 == 0
}
