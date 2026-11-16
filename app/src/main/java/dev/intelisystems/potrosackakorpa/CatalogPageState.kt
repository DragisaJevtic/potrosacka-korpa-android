package dev.intelisystems.potrosackakorpa

/** Validate an entire snapshot before Catalog commits any of its staged pages. */
internal class CatalogPageState(private val since: String) {
    var version = ""; private set
    var reset = false; private set
    var cursor = ""; private set
    var pages = 0; private set
    private var finished = false

    fun unchanged() {
        check(pages == 0 && since.isNotEmpty()) { "Neočekivan odgovor bez kataloga." }
    }

    fun accept(nextVersion: String, nextReset: Boolean, next: String?, removed: Int) {
        check(!finished)
        check(Regex("[0-9a-f-]{36}:[1-9][0-9]{0,17}").matches(nextVersion))
        if (pages == 0) {
            check(nextReset || (since.isNotEmpty() && since.substringBefore(':') == nextVersion.substringBefore(':')))
            if (!nextReset) check(nextVersion.substringAfter(':').toLong() >= since.substringAfter(':').toLong())
            version = nextVersion
            reset = nextReset
        }
        check(version == nextVersion && reset == nextReset) { "Nedosledne stranice kataloga." }
        check(!reset || removed == 0)
        check(next == null || (Regex("[0-9a-f]{32}").matches(next) && next > cursor))
        cursor = next.orEmpty()
        finished = next == null
        pages++
    }
}
