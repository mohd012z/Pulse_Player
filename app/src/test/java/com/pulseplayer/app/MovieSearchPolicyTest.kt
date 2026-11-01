package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test

class MovieSearchPolicyTest {
    @Test fun malayGenreYearAndOfficial() {
        val q = MovieSearchPolicy.query("Movie Melayu", 2, 1, true, 2026)
        assertTrue(q.contains("filem melayu"))
        assertTrue(q.contains("comedy"))
        assertTrue(q.contains("2026"))
        assertTrue(q.contains("saluran rasmi"))
    }
    @Test fun indonesianDefaultAndUnrestricted() {
        val q = MovieSearchPolicy.query("Movie Indonesia", 0, 0, false, 2026)
        assertTrue(q.contains("film indonesia"))
        assertFalse(q.contains("official"))
        assertFalse(q.contains("resmi"))
    }
    @Test fun invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MovieSearchPolicy.query("Unknown", 0, 0, true, 2026)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MovieSearchPolicy.query("Movie Melayu", 99, 0, true, 2026)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MovieSearchPolicy.query("Movie Melayu", 0, 99, true, 2026)
        }
    }
}
