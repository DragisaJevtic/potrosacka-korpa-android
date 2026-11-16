package dev.intelisystems.potrosackakorpa

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CheapestShoppingTest {
    private val items = listOf("a", "b", "c").map {
        ShoppingItem(Offer(it, "Artikal $it", "Lanac", null, 10000), 2, true)
    }
    @Test fun preservesAreaAndNearestOffersWithoutInventingMissingPrices() {
        val response = JSONObject("""{
          "fallback_status":"searched", "area":{"nearby_stores":3}, "rows":[
            {"id":"c","unavailable":true},
            {"id":"b","outside_area":true,"para":20000,"original_para":10000,
             "comparable":true,"offer_count":4,"price_basis":"chain","chain":"Lanac",
             "store":{"name":"Dalja","address":null,"city":"Drugo mesto","distance_km":12.5}},
            {"id":"a","outside_area":false,"para":9000,"original_para":10000,
             "comparable":true,"offer_count":2,"price_basis":"store_format","chain":"Lanac",
             "store":{"name":"Bliza","address":"Ulica 1","city":"Mesto","distance_km":1}}
          ]} """)
        val result = parseShoppingComparison(items, response)
        assertEquals("searched", result.fallbackStatus)
        assertEquals(3, result.nearbyStores)
        assertFalse(result.items[0].outsideArea)
        assertTrue(result.items[1].outsideArea)
        assertEquals(20000L, result.items[1].price)
        assertTrue(result.items[1].retailer!!.contains("Drugo mesto"))
        assertEquals("Ulica 1", result.items[0].destination!!.address)
        assertEquals("Drugo mesto", result.items[1].destination!!.city)
        assertFalse(result.items[1].destination!!.hasAddress)
        assertTrue(result.items[1].destination!!.mapsUrl().contains("/maps/search/"))
        assertNull(result.items[2].price)
        assertNull(result.items[2].destination)
    }
    @Test fun acceptsMissingOriginButRejectsUnsupportedServer() {
        val response = JSONObject("""{"fallback_status":"location_required",
            "area":{"nearby_stores":0},"rows":[{"id":"a","unavailable":true}]}""")
        assertEquals("location_required", parseShoppingComparison(items.take(1), response).fallbackStatus)
        response.remove("fallback_status")
        assertThrows(IllegalStateException::class.java) { parseShoppingComparison(items.take(1), response) }
    }
}
