package com.pulseplayer.app

/**
 * Pure policy for safely selecting the next media item for preloading.
 * The actual player integration must revalidate indices before starting audio.
 */
object CrossfadeQueuePolicy {
    fun nextIndex(
        current: Int,
        count: Int,
        repeatMode: Int,
        shuffleEnabled: Boolean,
        shuffledNext: Int?
    ): Int? {
        if (count < 2 || current !in 0 until count) return null
        // Repeat-one should replay the current track without overlapping itself.
        if (repeatMode == 1) return null
        if (shuffleEnabled) return shuffledNext?.takeIf { it in 0 until count && it != current }
        val next = current + 1
        return when {
            next < count -> next
            repeatMode == 2 -> 0
            else -> null
        }
    }
}
