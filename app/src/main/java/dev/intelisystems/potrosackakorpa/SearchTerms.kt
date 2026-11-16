package dev.intelisystems.potrosackakorpa

/** Each entered word may occur anywhere in the name or retailer, in any order. */
internal data class SearchTerms(val sql: String, val arguments: List<String>)

internal fun searchTerms(query: String): SearchTerms {
    val terms = searchKey(query.trim()).split(Regex("\\s+")).filter { it.isNotEmpty() }.distinct()
    if (terms.isEmpty()) return SearchTerms("1=1", emptyList())
    val clause = "(name_key LIKE ? ESCAPE '!' OR retailer_key LIKE ? ESCAPE '!')"
    return SearchTerms(terms.joinToString(" AND ") { clause }, terms.flatMap {
        val pattern = "%" + it.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
        listOf(pattern, pattern)
    })
}
