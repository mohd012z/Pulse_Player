package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test

class PulseCatalogPolicyTest {
    private fun item(category: String? = "Movie", adult: Boolean? = false, rating: String? = "PG-13") =
        PulseCatalogPolicy.Entry("test", category, adult, rating, "US")

    @Test fun permitsVerifiedGeneralAudience() {
        assertTrue(PulseCatalogPolicy.allow(item()))
        assertTrue(PulseCatalogPolicy.allow(item(rating = "TV-14")))
    }

    @Test fun blocksAdultAndUnratedAndUnknownMetadata() {
        assertFalse(PulseCatalogPolicy.allow(item(adult = true)))
        assertFalse(PulseCatalogPolicy.allow(item(adult = null)))
        assertFalse(PulseCatalogPolicy.allow(item(rating = "18")))
        assertFalse(PulseCatalogPolicy.allow(item(rating = "TV-MA")))
        assertFalse(PulseCatalogPolicy.allow(item(rating = null)))
        assertFalse(PulseCatalogPolicy.allow(item(rating = "Unrated")))
    }

    @Test fun blocksExcludedCategoryWithoutNationalityHeuristics() {
        assertFalse(PulseCatalogPolicy.allow(item(category = " Nollywood ")))
        assertTrue(PulseCatalogPolicy.allow(item(category = "Movie")))
    }

    @Test fun filtersEveryItemBeforeDisplay() {
        val input = listOf(item(), item(rating = "18+"), item(category = "Nollywood"))
        assertEquals(1, PulseCatalogPolicy.filter(input) { it }.size)
    }
}
