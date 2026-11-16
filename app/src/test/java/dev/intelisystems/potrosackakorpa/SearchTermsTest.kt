package dev.intelisystems.potrosackakorpa

import org.junit.Assert.*
import org.junit.Test

class SearchTermsTest {
    @Test fun brandAndQuantityAreIndependentWords() {
        val query = searchTerms("  GRAND   250  ")
        assertEquals(listOf("%grand%", "%grand%", "%250%", "%250%"), query.arguments)
        assertEquals(2, Regex("name_key LIKE").findAll(query.sql).count())
        assertTrue(query.sql.contains(" AND "))
        assertEquals(query.arguments.toSet(), searchTerms("250 grand").arguments.toSet())
    }
    @Test fun normalizesAccentsAndEscapesLiteralWildcards() {
        assertEquals(listOf("%caj%", "%caj%", "%10!%%", "%10!%%", "%a!_b!!%", "%a!_b!!%"),
            searchTerms("ČAJ 10% A_B!").arguments)
        assertEquals(2, searchTerms("kafa kafa").arguments.size)
        assertEquals(SearchTerms("1=1", emptyList<String>()), searchTerms("  "))
    }
}
