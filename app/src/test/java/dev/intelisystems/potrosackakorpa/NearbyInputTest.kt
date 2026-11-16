package dev.intelisystems.potrosackakorpa

import org.junit.Assert.*
import org.junit.Test

class NearbyInputTest {
    @Test fun smallTownRadiusIsExplicitAndLocalOnlyRemainsAvailable() {
        val village = Settlement("id", "Selo", "Opština", 1000)
        val nearby = ShoppingArea(village, 44.0, 20.0, 10)
        assertEquals(10, nearby.json().getInt("radius_km"))
        assertTrue(nearby.label.contains("celo mesto i okolina"))
        assertFalse(ShoppingArea(village).json().has("radius_km"))
        assertEquals("Selo", ShoppingArea(village).label)
    }
    @Test fun townItselfPrecedesMunicipalityVillages() {
        val city = Settlement("city", "Pančevo", "Pančevo", 73401)
        val village = Settlement("village", "Banatski Brestovac", "Pančevo", 2785)
        assertEquals(city, rankSettlements(listOf(village, city), "pancevo").first())
        assertEquals(city, rankSettlements(listOf(village, city), "Панчево").first())
    }
    @Test fun houseSuffixAllowedButApartmentIsRejected() {
        assertNull(addressInputError("Cara Dušana", "73"))
        assertNull(addressInputError("Cara Dušana", "73a"))
        assertNull(addressInputError("Cara Dušana", "73а"))
        assertNotNull(addressInputError("Cara Dušana", "73/4"))
        assertNotNull(addressInputError("Cara Dušana 73/4", "73"))
        assertNotNull(addressInputError("Cara Dušana", "73 sprat 4"))
        assertNotNull(addressInputError("", "73"))
    }
    @Test fun exactTownRanksBeforeMoreThanFiftyMunicipalityMatches() {
        val city = Settlement("city", "Pančevo", "Pančevo", 73401)
        val villages = (1..60).map { Settlement("$it", "A selo $it", "Pančevo", 100) }
        assertEquals(city, rankSettlements(villages + city, "Pančevo").first())
    }
}
