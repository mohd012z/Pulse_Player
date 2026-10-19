package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test

/** Vibe-style evidence checks: MovieBox-inspired navigation, not proprietary assets or APIs. */
class MovieBoxNavigationContractTest {
    @Test fun requiredCategoriesExist() {
        val codes = PulseCatalogNavigation.visibleCategories().map { it.sourceCode }.toSet()
        listOf("Trending", "Movie", "TVshow", "Animation", "ShortTV_Discover",
            "Live", "Kids", "Education", "Music", "Novel", "KDrama").forEach {
            assertTrue("Missing category: $it", it in codes)
        }
    }
    @Test fun nollywoodIsNotPresent() {
        assertFalse(PulseCatalogNavigation.visibleCategories().any {
            it.sourceCode.equals("Nollywood", ignoreCase = true) ||
                it.label.contains("Nollywood", ignoreCase = true)
        })
    }
    @Test fun noFakePlayableCatalogEntries() {
        assertTrue(PulseCatalogNavigation.visibleCategories().none { it.playable })
    }
    @Test fun mappingIgnoresCase() {
        assertEquals("Movies", PulseCatalogNavigation.categoryFor("movie")?.label)
        assertEquals("Short Drama", PulseCatalogNavigation.categoryFor("SHORTTV_DISCOVER")?.label)
    }
}
