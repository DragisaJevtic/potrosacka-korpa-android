package dev.intelisystems.potrosackakorpa

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PriceHistoryTest {
    @Test fun preservesGapsWithdrawalAndEarlierConfirmationOfScheduledPrice() {
        val history = parseHistory(JSONObject("""{
            "name":"Mleko", "active":false,"truncated":true,
            "rows":[
              {"event_type":"change","observed_at":"2026-11-15T00:00:00+01:00","confirmed_until":"2026-11-14T10:00:00Z","effective_para":12000,"regular_para":15000,"unit_para":15000,"sale_para":12000,"sale_start":"2026-11-15","sale_end":"2026-11-20","scheduled":true},
              {"event_type":"withdrawn","observed_at":"2026-11-16T10:00:00Z","confirmed_until":"2026-11-15T10:00:00Z","effective_para":null,"scheduled":false}
            ],"points":[
              {"observed_at":"2026-11-15T00:00:00+01:00","effective_para":12000},
              {"observed_at":"2026-11-15T10:00:00Z","effective_para":null}
            ]} """))
        assertFalse(history.active); assertTrue(history.truncated)
        assertTrue(history.events.first().scheduled)
        assertEquals(15000L, history.events.first().regular)
        assertEquals(12000L, history.events.first().sale)
        assertEquals("2026-11-20", history.events.first().saleEnd)
        assertEquals("2026-11-14T10:00:00Z", history.events.first().confirmed)
        assertNull(history.events.last().para); assertNull(history.points.last().para)
        assertEquals(12000L, history.points.first().para)
    }
    @Test fun emptyHistoryDoesNotInventAPrice() {
        val history = parseHistory(JSONObject("""{"name":"Mleko","active":false,"truncated":false,"rows":[],"points":[]} """))
        assertTrue(history.events.isEmpty()); assertTrue(history.points.isEmpty())
    }
    @Test fun historyUsesExactBarcodeSourceAndEncodedFormat() {
        val path = HistoryIdentity("8601500126362", "trial-source", "Market & online/Beograd").path()
        assertEquals("/api/history/8601500126362?source=trial-source&format=Market+%26+online%2FBeograd", path)
        assertThrows(IllegalArgumentException::class.java) { HistoryIdentity("123", "source", "format").path() }
    }
}
