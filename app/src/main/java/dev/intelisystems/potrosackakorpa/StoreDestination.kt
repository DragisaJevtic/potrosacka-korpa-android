package dev.intelisystems.potrosackakorpa

import java.net.URLEncoder

/** Structured branch data: never derive an address from the formatted price label. */
data class StoreDestination(val source: String, val name: String, val chain: String,
                            val address: String, val city: String) {
    val hasAddress: Boolean get() = address.isNotBlank()

    fun mapsUrl(): String {
        val query = if (hasAddress) listOf(address, city, "Srbija")
            else listOf(name.ifBlank { chain }, city, "Srbija")
        val encoded = URLEncoder.encode(query.filter { it.isNotBlank() }.joinToString(", "), "UTF-8")
        return if (hasAddress) "https://www.google.com/maps/dir/?api=1&destination=$encoded&travelmode=driving&dir_action=navigate"
            else "https://www.google.com/maps/search/?api=1&query=$encoded"
    }
}
