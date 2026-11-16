package dev.intelisystems.potrosackakorpa

import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class StoreDestinationTest {
    @Test fun exactAddressOpensDirectionsAndEncodesSpecialCharacters() {
        val store = StoreDestination("s", "Market", "Lanac", "Čika Ljubina 4 & 6", "Beograd")
        val url = store.mapsUrl()
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1&destination="))
        assertTrue(url.contains("dir_action=navigate"))
        assertFalse(url.contains("4 & 6"))
        assertTrue(URLDecoder.decode(url, "UTF-8").contains("Čika Ljubina 4 & 6, Beograd, Srbija"))
    }
    @Test fun missingAddressSearchesBranchInItsOwnTown() {
        val store = StoreDestination("s", "Market Centar", "Lanac", "  ", "Kovin")
        assertFalse(store.hasAddress)
        assertEquals("https://www.google.com/maps/search/?api=1&query=Market Centar, Kovin, Srbija",
            URLDecoder.decode(store.mapsUrl(), "UTF-8"))
        assertTrue(URLDecoder.decode(store.copy(name="").mapsUrl(), "UTF-8").contains("Lanac, Kovin"))
    }
}
