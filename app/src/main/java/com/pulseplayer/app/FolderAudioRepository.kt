package com.pulseplayer.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** Only enumerates folders explicitly authorized through ACTION_OPEN_DOCUMENT_TREE. */
class FolderAudioRepository(private val context: Context) {
    data class Track(val name: String, val uri: Uri, val folder: String) {
        fun toMediaItem(): MediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(name).setArtist(folder).build())
            .build()
    }

    companion object {
        private const val MAX_FILES = 5000
        private const val MAX_DEPTH = 15
        fun isAudio(name: String, mimeType: String?): Boolean =
            PlaybackInputs.isAudioFile(name, mimeType)
    }

    fun scanTrees(uris: List<Uri>): List<Track> {
        val result = ArrayList<Track>()
        val seen = HashSet<Uri>()
        for (uri in uris) {
            if (result.size >= MAX_FILES) break
            val root = DocumentFile.fromTreeUri(context, uri) ?: continue
            if (!root.isDirectory) continue
            val stack = ArrayDeque<Pair<DocumentFile, Int>>()
            stack.add(root to 0)
            while (stack.isNotEmpty() && result.size < MAX_FILES) {
                val (folder, depth) = stack.removeLast()
                val children = runCatching { folder.listFiles() }.getOrDefault(emptyArray())
                for (file in children) {
                    if (result.size >= MAX_FILES) break
                    if (file.isDirectory) {
                        if (depth < MAX_DEPTH) stack.add(file to depth + 1)
                    } else if (file.isFile && seen.add(file.uri)) {
                        val name = file.name ?: continue
                        if (isAudio(name, file.type)) {
                            result.add(Track(name, file.uri, folder.name ?: "Imported folder"))
                        }
                    }
                }
            }
        }
        return result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}