package dev.intelisystems.potrosackakorpa

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

internal data class HistoryIdentity(val barcode: String, val source: String, val format: String) {
    fun path(): String {
        require(validProductCode(barcode) && source.isNotBlank() && format.isNotBlank())
        return "/api/history/$barcode?source=${URLEncoder.encode(source, "UTF-8")}&format=${URLEncoder.encode(format, "UTF-8")}"
    }
}
internal data class HistoryPoint(val time: Long, val para: Long?)
internal data class HistoryEvent(val kind: String, val observed: String, val confirmed: String, val para: Long?, val scheduled: Boolean, val regular: Long?, val unit: Long?, val sale: Long?, val saleStart: String?, val saleEnd: String?)
internal data class HistoryData(val name: String, val active: Boolean, val truncated: Boolean, val events: List<HistoryEvent>, val points: List<HistoryPoint>)

internal fun parseHistory(json: JSONObject): HistoryData {
    val rows = json.getJSONArray("rows")
    val points = json.getJSONArray("points")
    return HistoryData(json.getString("name"), json.getBoolean("active"), json.getBoolean("truncated"),
        List(rows.length()) { index ->
            val r = rows.getJSONObject(index)
            val kind = r.getString("event_type")
            OffsetDateTime.parse(r.getString("observed_at"))
            OffsetDateTime.parse(r.getString("confirmed_until"))
            HistoryEvent(kind, r.getString("observed_at"), r.getString("confirmed_until"),
                if (kind == "withdrawn" || r.isNull("effective_para")) null else r.getLong("effective_para"), r.optBoolean("scheduled"),
                if (r.isNull("regular_para")) null else r.getLong("regular_para"),
                if (r.isNull("unit_para")) null else r.getLong("unit_para"),
                if (r.isNull("sale_para")) null else r.getLong("sale_para"),
                if (r.isNull("sale_start")) null else r.getString("sale_start"),
                if (r.isNull("sale_end")) null else r.getString("sale_end"))
        }, List(points.length()) { index ->
            val p = points.getJSONObject(index)
            HistoryPoint(OffsetDateTime.parse(p.getString("observed_at")).toInstant().toEpochMilli(),
                if (p.isNull("effective_para")) null else p.getLong("effective_para"))
        })
}

/** Online history is kept separate from the offline catalog and shopping list. */
internal class PriceHistory(private val activity: Activity) {
    private val worker = Executors.newSingleThreadExecutor()
    private var dialog: AlertDialog? = null
    private var request = 0
    private var closed = false
    private val money = NumberFormat.getNumberInstance(Locale.forLanguageTag("sr-Latn-RS")).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }
    private fun amount(para: Long?) = para?.let { money.format(BigDecimal.valueOf(it, 2)) + " RSD" } ?: "Nema ponude"
    private fun date(value: String): String = OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Europe/Belgrade"))
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy. HH:mm"))

    fun show(identity: HistoryIdentity) {
        if (closed) return
        val token = ++request
        dialog?.dismiss()
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun label(value: String) = KorpaUi.label(activity, value).apply {
            val spacing = KorpaUi.dp(activity, 8)
            setPadding(0, spacing, 0, spacing)
        }
        body.addView(ProgressBar(activity))
        body.addView(label("Učitavamo istoriju cene…"))
        val current = AlertDialog.Builder(activity).setTitle("Istorija cene")
            .setView(ScrollView(activity).apply { addView(body) }).setPositiveButton("Zatvori", null).create()
        dialog = current
        current.show()
        worker.execute {
            val result = runCatching {
                val connection = URI(BuildConfig.API_URL + identity.path()).toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 12_000; connection.readTimeout = 25_000
                try {
                    check(connection.responseCode == 200)
                    parseHistory(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
                } finally { connection.disconnect() }
            }
            activity.runOnUiThread {
                if (closed || token != request || !current.isShowing) return@runOnUiThread
                body.removeAllViews()
                result.fold(onSuccess = { history ->
                    body.addView(label(history.name))
                    body.addView(label(identity.format + " · " + identity.barcode))
                    if (history.events.isEmpty()) body.addView(label("Za ovu ponudu još nema sačuvane istorije."))
                    else {
                        if (!history.active) body.addView(label("Ponuda je povučena iz poslednjeg potpunog cenovnika."))
                        body.addView(label("Poslednja potvrda: ${date(history.events.last().confirmed)}"))
                        body.addView(label("Čuvamo promene cene i uslova akcije. Ista cena pri ponovnoj proveri produžava potvrdu. Prekidi na grafikonu označavaju nepotvrđene periode."))
                        if (history.points.any { it.para != null }) body.addView(HistoryChart(activity, history.points),
                            LinearLayout.LayoutParams(-1, (200 * activity.resources.displayMetrics.density).toInt()))
                        if (history.truncated) body.addView(label("Prikazano je poslednjih ${history.events.size} događaja."))
                        history.events.asReversed().forEach { event ->
                            val title = when (event.kind) {
                                "first" -> "Prvo viđenje"; "change" -> "Promena cene ili akcije"
                                "withdrawn" -> "Povučena ponuda"; "returned" -> "Ponuda ponovo dostupna"
                                else -> "Događaj"
                            }
                            val conditions = if (event.kind == "withdrawn") "" else buildString {
                                event.regular?.let { append("\nRedovna: ${amount(it)}") }
                                event.unit?.let { append("\nPo jedinici mere: ${amount(it)}") }
                                event.sale?.let { append("\nAkcijska: ${amount(it)} · ${event.saleStart.orEmpty()} – ${event.saleEnd.orEmpty()}") }
                            }
                            body.addView(label("$title · ${date(event.observed)}\n${amount(event.para)}$conditions\nPotvrđeno: ${date(event.confirmed)}" +
                                if (event.scheduled) "\nAktivirana ranije objavljena cena; ovo nije nova provera izvora." else ""))
                        }
                        body.addView(label("Cene su iz objavljenih cenovnika; ne potvrđuju zalihe u poslovnici. Starija istorija pre resetovanja baze nije dostupna."))
                    }
                }, onFailure = {
                    body.addView(label("Istorija nije učitana. Proveri internet i pokušaj ponovo."))
                    body.addView(KorpaUi.button(activity, "Pokušaj ponovo", true) { show(identity) })
                })
            }
        }
    }
    fun close() { closed = true; request++; dialog?.dismiss(); worker.shutdownNow() }
}

private class HistoryChart(activity: Activity, private val points: List<HistoryPoint>) : View(activity) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription = "Stepenasti grafikon potvrđenih cena. Detalji i datumi nalaze se ispod grafikona." }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val priced = points.filter { it.para != null }
        if (priced.isEmpty()) return
        val min = priced.minOf { it.para!! }.toDouble(); val max = priced.maxOf { it.para!! }.toDouble()
        val start = points.minOf { it.time }; val duration = (points.maxOf { it.time } - start).coerceAtLeast(1)
        val density = resources.displayMetrics.density
        val left = 68f * density; val top = 16f * density; val bottom = height - 28f * density
        val right = (width - 10f * density).coerceAtLeast(left + 1)
        fun x(p: HistoryPoint) = left + (p.time - start).toFloat() / duration * (right - left)
        fun y(p: HistoryPoint) = if (max == min) (top + bottom) / 2 else bottom - ((p.para!! - min) / (max - min) * (bottom - top)).toFloat()
        paint.color = KorpaUi.muted; paint.textSize = 11f * density; paint.style = Paint.Style.FILL
        val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("sr-Latn-RS")).apply { maximumFractionDigits = 2 }
        canvas.drawText(format.format(max / 100) + " RSD", 0f, top, paint)
        if (min != max) canvas.drawText(format.format(min / 100), 0f, bottom, paint)
        val dates = DateTimeFormatter.ofPattern("dd.MM.yy").withZone(ZoneId.of("Europe/Belgrade"))
        canvas.drawText(dates.format(java.time.Instant.ofEpochMilli(start)), left, height - 5f * density, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(dates.format(java.time.Instant.ofEpochMilli(points.maxOf { it.time })), right, height - 5f * density, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.color = KorpaUi.forest; paint.strokeWidth = 2f * density
        val path = Path(); var previous: HistoryPoint? = null
        for (point in points) {
            if (point.para == null) { previous = null; continue }
            if (previous == null) path.moveTo(x(point), y(point))
            else { path.lineTo(x(point), y(previous)); path.lineTo(x(point), y(point)) }
            canvas.drawCircle(x(point), y(point), 2f * density, paint)
            previous = point
        }
        paint.style = Paint.Style.STROKE; canvas.drawPath(path, paint)
    }
}
