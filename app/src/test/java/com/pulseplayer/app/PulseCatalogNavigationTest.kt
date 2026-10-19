package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test

class PulseCatalogNavigationTest {
    @Test fun mapsMovieBoxCategoryCodesWithoutPrivateUrls() {
        assertEquals("TV Series", PulseCatalogNavigation.categoryFor("TVshow")?.label)
        assertEquals("Short Drama", PulseCatalogNavigation.categoryFor("ShortTV_Discover")?.label)
        assertNull(PulseCatalogNavigation.categoryFor("Nollywood"))
        assertFalse(PulseCatalogNavigation.visibleCategories().any { it.playable })
    }
}
