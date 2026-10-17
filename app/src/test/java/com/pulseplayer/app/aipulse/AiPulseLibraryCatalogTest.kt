package com.pulseplayer.app.aipulse

import org.junit.Assert.*
import org.junit.Test

class AiPulseLibraryCatalogTest {
    private fun song(n: Int) = AiPulseLibraryCatalog.Entry(
        id = n.toString(), uri = "content://music/$n",
        title = "Track %05d".format(n), artist = if (n % 2 == 0) "Alpha" else "Beta",
        folder = if (n % 2 == 0) "Imports" else "Generated",
        format = AiPulseLibraryPolicy.Format.MP3, modifiedAt = n.toLong()
    )

    @Test fun tenThousandSongsPageDeterministically() {
        val all = (0 until 10000).map(::song)
        val first = AiPulseLibraryCatalog.page(all, limit = 50)
        val second = AiPulseLibraryCatalog.page(all, offset = 50, limit = 50)
        assertEquals(50, first.size)
        assertEquals(50, second.size)
        assertTrue(first.map { it.id }.intersect(second.map { it.id }.toSet()).isEmpty())
        assertEquals("0", first.first().id)
        assertEquals("9999", AiPulseLibraryCatalog.page(all, offset = 9999).single().id)
        assertTrue(AiPulseLibraryCatalog.page(all, offset = 10000).isEmpty())
    }

    @Test fun trashRestoreAndFoldersAreReversible() {
        val initial = listOf(song(1), song(2))
        val moved = AiPulseLibraryCatalog.moveToTrash(initial, setOf("1"))
        assertEquals(listOf("2"), AiPulseLibraryCatalog.page(moved).map { it.id })
        assertEquals(listOf("1"), AiPulseLibraryCatalog.page(moved, includeTrash = true).map { it.id })
        val restored = AiPulseLibraryCatalog.restore(moved, setOf("1"))
        assertEquals(initial, restored)
        val folder = AiPulseLibraryCatalog.moveToFolder(initial, setOf("2"), "Favorites")
        assertEquals("Favorites", folder[1].folder)
        assertEquals("Imports", initial[1].folder)
    }

    @Test fun searchFilterAndRecentSort() {
        val all = (0 until 100).map(::song)
        assertEquals(50, AiPulseLibraryCatalog.page(all, folder = "Imports").size)
        assertEquals(1, AiPulseLibraryCatalog.page(all, query = "track 00009").size)
        assertEquals("99", AiPulseLibraryCatalog.page(all, sort = AiPulseLibraryCatalog.Sort.RECENT).first().id)
    }

    @Test fun rejectsInvalidPagesAndFolders() {
        assertThrows(IllegalArgumentException::class.java) {
            AiPulseLibraryCatalog.page(emptyList(), offset = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiPulseLibraryCatalog.page(emptyList(), limit = 1000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiPulseLibraryCatalog.moveToFolder(emptyList(), emptySet(), " ")
        }
    }
}
