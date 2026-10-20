package com.pulseplayer.app

/**
 * Navigation compatibility mapping based on the publicly visible labels of MovieBox's
 * packaged appTab.json. This intentionally contains no MovieBox private URLs, tokens,
 * icon assets, streaming endpoints, or proprietary mini-app deep links.
 */
object PulseCatalogNavigation {
    data class Category(val sourceCode: String, val label: String, val playable: Boolean = false)
    val categories = listOf(
        Category("Trending", "Trending"),
        Category("Movie", "Movies"),
        Category("TVshow", "TV Series"),
        Category("Animation", "Anime"),
        Category("ShortTV_Discover", "Short Drama"),
        Category("Live", "Live"),
        Category("Kids", "Kids"),
        Category("Education", "Education"),
        Category("Football", "Football"),
        Category("Music", "Music"),
        Category("Novel", "Novels"),
        Category("WesternTv", "Western"),
        Category("KDrama", "Asian Drama"),
        Category("Game", "Games"),
        Category("Wrestling", "Fight Zone")
    )
    private val codes = categories.associateBy { it.sourceCode.lowercase(java.util.Locale.ROOT) }
    fun categoryFor(code: String): Category? = codes[code.trim().lowercase(java.util.Locale.ROOT)]
    fun visibleCategories() = categories
}
