package com.pulseplayer.app.aipulse

import java.util.Locale

/** Pure library operations; caller persists changes and owns URI permissions. */
object AiPulseLibraryCatalog {
    data class Entry(
        val id: String,
        val uri: String,
        val title: String,
        val artist: String,
        val folder: String,
        val format: AiPulseLibraryPolicy.Format,
        val missing: Boolean = false,
        val trashed: Boolean = false,
        val modifiedAt: Long = 0L
    )

    enum class Sort { TITLE, ARTIST, RECENT }

    fun page(
        entries: List<Entry>,
        folder: String? = null,
        query: String = "",
        sort: Sort = Sort.TITLE,
        offset: Int = 0,
        limit: Int = 50,
        includeTrash: Boolean = false
    ): List<Entry> {
        require(offset >= 0) { "Offset must be nonnegative" }
        require(limit in 1..200) { "Page size must be between 1 and 200" }
        val needle = query.trim().lowercase(Locale.ROOT)
        val filtered = entries.asSequence()
            .filter { it.trashed == includeTrash }
            .filter { folder == null || it.folder == folder }
            .filter {
                needle.isEmpty() || it.title.lowercase(Locale.ROOT).contains(needle) ||
                    it.artist.lowercase(Locale.ROOT).contains(needle)
            }
        val comparator = when (sort) {
            Sort.TITLE -> compareBy<Entry> { it.title.lowercase(Locale.ROOT) }.thenBy { it.id }
            Sort.ARTIST -> compareBy<Entry> { it.artist.lowercase(Locale.ROOT) }.thenBy { it.id }
            Sort.RECENT -> compareByDescending<Entry> { it.modifiedAt }.thenBy { it.id }
        }
        return filtered.sortedWith(comparator).drop(offset).take(limit).toList()
    }

    fun moveToTrash(entries: List<Entry>, ids: Set<String>): List<Entry> =
        entries.map { if (it.id in ids) it.copy(trashed = true) else it }

    fun restore(entries: List<Entry>, ids: Set<String>): List<Entry> =
        entries.map { if (it.id in ids) it.copy(trashed = false) else it }

    fun moveToFolder(entries: List<Entry>, ids: Set<String>, folder: String): List<Entry> {
        require(folder.isNotBlank() && folder.length <= 120) { "Invalid folder name" }
        return entries.map { if (it.id in ids) it.copy(folder = folder.trim()) else it }
    }
}
