package com.pulseplayer.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment

/** Queues an artist-authorized MP3 using Android's background DownloadManager.
 *  API 29+: visible in public Downloads/PulsePlayer. Older Android: app-scoped downloads
 *  to avoid requesting broad write permissions. Does not extract service-owned streams.
 */
class AuthorizedMp3Downloader(private val context: Context) {
    fun queue(track: JamendoCatalogRepository.Track,
              downloadUrl: String, quality: Mp3DownloadInputs.Quality): Long {
        require(track.downloadAllowed) { "Artist has disabled track downloads" }
        require(WorldMusicInputs.validAudioUrl(downloadUrl)) { "Invalid HTTPS download URL" }
        val name = Mp3DownloadInputs.filename(track.artist, track.title, track.id, quality)
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("${track.artist} — ${track.title}")
            setDescription("Licensed independent MP3 (${quality.label})")
            setMimeType("audio/mpeg")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "PulsePlayer/$name")
            } else {
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
            }
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }
}