package com.pulseplayer.app

import java.util.Locale

/**
 * Fail-closed catalog filtering. Applies to all remote discovery, search, rails and cached results.
 * Do not infer maturity from country, actor, or language.
 */
object PulseCatalogPolicy {
    data class Entry(
        val id: String,
        val category: String?,
        val adult: Boolean?,
        val certification: String?,
        val certificationCountry: String?
    )

    private val excludedCategories = setOf("nollywood")
    private val matureCertifications = setOf("18", "18+", "r18", "r18+", "nc-17", "tv-ma", "x", "xxx", "adults only")
    private val safeCertifications = setOf("u", "g", "pg", "pg-13", "12", "12a", "13", "tv-y", "tv-y7", "tv-g", "tv-pg", "tv-14")

    fun allow(entry: Entry): Boolean {
        if (entry.category?.trim()?.lowercase(Locale.ROOT) in excludedCategories) return false
        if (entry.adult != false) return false
        val rating = entry.certification?.trim()?.lowercase(Locale.ROOT)
        // Unknown/unrated is hidden until the catalog has verified metadata.
        if (rating.isNullOrBlank()) return false
        if (rating in matureCertifications) return false
        return rating in safeCertifications
    }

    fun <T> filter(items: List<T>, metadata: (T) -> Entry): List<T> =
        items.filter { allow(metadata(it)) }

    /** Always include this parameter in every supported TMDB search/discover request. */
    const val TMDB_ADULT_PARAMETER = "include_adult=false"
}
