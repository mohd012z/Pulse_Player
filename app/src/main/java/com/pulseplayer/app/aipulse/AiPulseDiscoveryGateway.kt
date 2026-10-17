package com.pulseplayer.app.aipulse

/**
 * Serialized metadata lookup with a bounded, expiring in-memory cache.
 * Call from a background thread. No audio downloads or training data collection.
 */
object AiPulseDiscoveryGateway {
    private data class Entry(
        val timestamp: Long,
        val recordings: List<AiPulseHybridDiscovery.Recording>
    )
    private val cache = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private const val TTL_MS = 24L * 60 * 60 * 1000
    private const val MIN_INTERVAL_MS = 1100L
    private var lastRequestMs = 0L

    @Synchronized
    fun search(query: String, online: Boolean): List<AiPulseHybridDiscovery.Recording> {
        val key = query.trim().lowercase().take(80)
        require(key.length >= 2) { "Enter at least two characters" }
        val now = System.currentTimeMillis()
        val existing = cache[key]
        if (existing != null && now - existing.timestamp in 0..TTL_MS) {
            return existing.recordings
        }
        if (!online) return emptyList()
        val delay = MIN_INTERVAL_MS - (now - lastRequestMs)
        if (delay > 0) Thread.sleep(delay)
        lastRequestMs = System.currentTimeMillis()
        val records = AiPulseHybridDiscovery.searchRecording(key)
        cache[key] = Entry(System.currentTimeMillis(), records)
        while (cache.size > 40) cache.remove(cache.keys.first())
        return records
    }

    @Synchronized fun clear() = cache.clear()
}
