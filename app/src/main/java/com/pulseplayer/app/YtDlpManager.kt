package com.pulseplayer.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Downloads audio from YouTube / YouTube Music by driving a bundled static
 * yt-dlp binary (same engine the ytdlnis CLI wraps: --extract-audio, with
 * the optional --write-thumbnail / --convert-thumbnails extras).
 *
 * Binaries ship as assets per ABI (aarch64 first, armv7 fallback) so the
 * feature needs no Play Store dependency: extracted once to filesDir/ytdlp/,
 * made executable, then invoked with a merged stdout/stderr stream whose
 * `[download] …%` lines are parsed for progress.
 *
 * All Android-framework work (asset copy, getExternalFilesDir, spawning the
 * process, MediaStore import) runs on worker threads; the pure command/
 * progress/URL logic is unit-testable without a framework.
 */
class YtDlpManager(private val context: Context) {

    companion object {
        /** Audio formats offered in the download dialog, in display order. */
        val FORMATS = listOf("MP3", "FLAC", "M4A", "OPUS")

        /** yt-dlp asset name for the given ABI prefix. Only aarch64 is
         *  bundled (one 40 MB static binary); 32-bit-only devices are
         *  rejected by [selectedAsset] with a clean error. */
        fun assetNameForAbi(abi: String): String {
            require(abi.startsWith("arm64")) { "yt-dlp ships for arm64 only (got $abi)" }
            return "yt-dlp_aarch64"
        }

        /** True when the URL is YouTube / YouTube Music (watch, shorts,
         *  live, playlist, channel or music.youtube.com). */
        fun isSupportedUrl(raw: String): Boolean {
            val url = raw.trim()
            if (url.isEmpty()) return false
            val lower = url.lowercase()
            if (!lower.startsWith("https://") && !lower.startsWith("http://")) return false
            val host = lower.removePrefix("https://").removePrefix("http://").substringBefore('/')
            if (host != "youtu.be" && !host.endsWith("youtube.com")) return false
            if (host == "youtu.be") {
                val path = lower.removePrefix("https://").removePrefix("http://").substringAfter('/', "")
                val id = path.substringBefore('/')
                return id.length == 11 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
            }
            return true
        }

        /** yt-dlp argv for an audio download of [url] to [outDir]. */
        fun buildCommand(
            binary: String,
            url: String,
            outDir: File,
            format: String,
            writeThumbnail: Boolean = false,
        ): List<String> {
            val fmt = normalizeFormat(format)
            val cmd = mutableListOf(
                binary, "--no-warnings", "--ignore-errors",
                "--retries", "3",
                "--output", File(outDir, "%(title)s [%(id)s].%(ext)s").absolutePath,
                "--extract-audio",
            )
            when (fmt) {
                "FLAC" -> cmd.addAll(listOf("--audio-format", "flac"))
                "M4A" -> cmd.addAll(listOf("--audio-format", "m4a", "--audio-quality", "192K"))
                "OPUS" -> cmd.addAll(listOf("--audio-format", "opus", "--audio-quality", "160K"))
                else -> cmd.addAll(listOf("--audio-format", "mp3", "--audio-quality", "192K"))
            }
            if (writeThumbnail) cmd.addAll(listOf("--write-thumbnail", "--convert-thumbnails", "jpg"))
            cmd.add(url)
            return cmd
        }

        fun normalizeFormat(raw: String): String =
            FORMATS.firstOrNull { it.equals(raw.trim(), ignoreCase = true) } ?: "MP3"

        /** Parsed yt-dlp progress state from a `[download]` line. */
        data class Progress(
            val percent: Double,
            val speed: String?,
            val etaSeconds: Long?,
        )

        /** Parse a yt-dlp `[download]` progress line; null for non-progress.
         *  Handles yt-dlp's real format, e.g.
         *  `[download]  42.7% of ~  10.20MiB at  1.23MiB/s ETA 00:12`. */
        fun parseProgress(line: String): Progress? {
            if (!line.contains("[download]")) return null
            val pct = Regex("\\b([0-9]+(?:\\.[0-9]+)?)%").find(line)
                ?.value?.removeSuffix("%")?.toDoubleOrNull() ?: return null
            val speed = Regex("at\\s+([0-9.]+[KMGT]?i?B/s)").find(line)?.groupValues?.get(1)
            val eta = Regex("ETA\\s+(\\d+):(\\d+)(?::(\\d+))?").find(line)
            val etaSeconds = eta?.let { m ->
                val mm = m.groupValues[1].toLong()
                val ss = m.groupValues[2].toLong()
                if (m.groupValues[3].isNotEmpty()) 3600L * mm + 60L * ss + m.groupValues[3].toLong()
                else 60L * mm + ss
            }
            return Progress(pct, speed, etaSeconds)
        }
    }

    private val jobLock = ReentrantLock()
    private var activeProcess: Process? = null
    private val seenDownloads = mutableSetOf<String>()
    private var seenInitialized = false

    private fun cacheDir(): File =
        File(context.filesDir, "ytdlp").apply { if (!exists()) mkdirs() }

    /** Pick the bundled binary for this device. arm64 (arm64-v8a) is the
     *  only supported ABI — every phone since Android 11 (API 30, 2020) is
     *  arm64; 32-bit-only devices get a clean, actionable error. */
    private fun selectedAsset(): String {
        val abis = Build.SUPPORTED_ABIS.toList()
        return when {
            abis.any { it.startsWith("arm64") } -> "yt-dlp_aarch64"
            else -> throw IllegalStateException(
                "YouTube download needs an arm64 (64-bit) device; this one is $abis. " +
                    "See Pulse Player → More → YouTube / YT Music — download."
            )
        }
    }

    /** Extract the static binary once; returns its executable path.
     *  Call on a worker thread (asset I/O). */
    fun ensureBinary(): String = jobLock.withLock {
        val dir = cacheDir()
        val asset = selectedAsset()
        val target = File(dir, asset)
        if (!target.exists() || target.length() == 0L) {
            val out = File(dir, "$asset.tmp")
            try {
                context.assets.openFd(asset).use { af ->
                    af.createInputStream().use { input ->
                        out.outputStream().use { o -> input.copyTo(o) }
                    }
                }
                if (!out.renameTo(target)) {
                    if (target.exists()) target.delete()
                    if (!out.renameTo(target)) throw IllegalStateException("Cannot place yt-dlp binary")
                }
            } finally {
                if (!target.exists()) out.delete()
            }
        }
        target.setExecutable(true, true)
        if (!target.canExecute()) {
            throw IllegalStateException("yt-dlp binary not executable: ${target.absolutePath}")
        }
        target.absolutePath
    }

    /** Parse a yt-dlp `[download]` progress line; null for non-progress.
     *  Handles yt-dlp's real format, e.g.
     *  `[download]  42.7% of ~  10.20MiB at  1.23MiB/s ETA 00:12`. */
    fun parseProgress(line: String): Progress? {
        if (!line.contains("[download]")) return null
        val pct = Regex("\\b([0-9]+(?:\\.[0-9]+)?)%").find(line)
            ?.value?.removeSuffix("%")?.toDoubleOrNull() ?: return null
        val speed = Regex("at\\s+([0-9.]+[KMGT]?i?B/s)").find(line)?.groupValues?.get(1)
        val eta = Regex("ETA\\s+(\\d+):(\\d+)(?::(\\d+))?").find(line)
        val etaSeconds = eta?.let { m ->
            val mm = m.groupValues[1].toLong()
            val ss = m.groupValues[2].toLong()
            if (m.groupValues[3].isNotEmpty()) 3600L * mm + 60L * ss + m.groupValues[3].toLong()
            else 60L * mm + ss
        }
        return Progress(pct, speed, etaSeconds)
    }

    /**
     * Directory where downloads land. Prefers the app's public external
     *  folder (no runtime permission on any API level; visible to file
     *  managers and, via the MediaStore import, to music apps). Falls back
     *  to the shared Downloads folder, then internal storage. Must be called
     *  on a worker thread (getExternalFilesDir does disk I/O).
     */
    fun resolveDownloadDir(): File {
        val appPublic = File(context.getExternalFilesDir(null), "youtube")
        if (appPublic.exists() || appPublic.mkdirs()) {
            return appPublic
        }
        @Suppress("DEPRECATION")
        val public = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (public != null && (public.exists() || public.mkdirs()) && public.canWrite()) {
            return File(public, "PulsePlayer").apply { mkdirs() }
        }
        return File(context.filesDir, "youtube").apply { mkdirs() }
    }

    private fun listAudioFiles(dir: File): List<File> =
        dir.listFiles { f ->
            f.isFile && !f.name.endsWith(".part") &&
                f.name.lowercase().substringAfterLast('.') in
                setOf("mp3", "flac", "m4a", "opus", "ogg", "webm")
        }?.toList() ?: emptyList()

    /** Snapshot current dir contents. [baseline]=true records the current
     *  set and returns nothing; later calls return only files created since
     *  the last snapshot (playlist / multi-track support). */
    fun newDownloads(dir: File, baseline: Boolean = false): List<File> {
        val current = listAudioFiles(dir)
        if (baseline || !seenInitialized) {
            seenInitialized = true
            seenDownloads.clear()
            seenDownloads += current.map { it.absolutePath }
            return emptyList()
        }
        val fresh = current.filter { it.absolutePath !in seenDownloads }
        seenDownloads += fresh.map { it.absolutePath }
        return fresh
    }

    /**
     * Download audio for [url] on a background thread. Returns false (and
     * reports why) when the URL is unsupported or a download is already
     * running. Callbacks run on that worker thread — post to the UI thread
     * before touching views.
     */
    fun start(
        url: String,
        format: String,
        writeThumbnail: Boolean,
        onProgress: (Progress) -> Unit,
        onComplete: (List<File>) -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        if (!isSupportedUrl(url)) {
            onError("Not a YouTube / YouTube Music link")
            return false
        }
        val canStart = jobLock.withLock { activeProcess?.isAlive != true }
        if (!canStart) {
            onError("A download is already running")
            return false
        }
        thread {
            var binary = ""
            var outDir = File("")
            val ready = try {
                binary = ensureBinary()
                outDir = resolveDownloadDir()
                true
            } catch (e: Exception) {
                onError("yt-dlp not ready: ${e.message}")
                false
            }
            if (!ready) return@thread
            val cmd = buildCommand(binary, url.trim(), outDir, format, writeThumbnail)
            val proc = try {
                ProcessBuilder(cmd)
                    .directory(outDir)
                    .redirectErrorStream(true)
                    .start()
            } catch (e: Exception) {
                onError("Cannot start yt-dlp: ${e.message}")
                return@thread
            }
            jobLock.withLock { activeProcess = proc }
            newDownloads(outDir, baseline = true)
            var lastPct = -1.0
            try {
                val reader = proc.inputStream.bufferedReader()
                while (true) {
                    val line = reader.readLine()
                    if (line == null) break
                    val progress = parseProgress(line)
                    if (progress != null && (progress.percent >= lastPct + 0.5 || lastPct < 0.0)) {
                        lastPct = progress.percent
                        onProgress(progress)
                    }
                }
            } catch (_: Exception) {
                // reader closed early; the return-code path still runs
            }
            val code = try {
                proc.waitFor()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                -1
            }
            jobLock.withLock { activeProcess = null }
            if (code == 0) {
                val fresh = newDownloads(outDir)
                if (fresh.isEmpty()) {
                    onError("Finished, but no new audio file appeared (already downloaded?)")
                } else {
                    onComplete(fresh)
                }
            } else {
                onError("yt-dlp exited with code $code")
            }
        }
        return true
    }

    /** Register [file] with MediaStore so the system library sees it.
     *  Call on a worker thread. */
    fun importToMediaStore(file: File) {
        try {
            context.contentResolver.delete(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Audio.Media.DATA} = ?",
                arrayOf(file.absolutePath),
            )
        } catch (_: Exception) {
        }
        val values = android.content.ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeFor(file.name))
            put(MediaStore.Audio.Media.DATA, file.absolutePath)
        }
        try {
            context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        } catch (_: Exception) {
        }
        try {
            context.sendBroadcast(
                android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    setDataAndType(Uri.fromFile(file), mimeFor(file.name))
                },
            )
        } catch (_: Exception) {
        }
    }

    /** MediaStore content URI for a previously imported file (best-effort);
     *  null when not indexed. Call on a worker thread. */
    fun mediaStoreUriFor(file: File): Uri? {
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DATA} = ?",
                arrayOf(file.absolutePath), null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    return Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        c.getString(0),
                    )
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun mimeFor(name: String): String = when (name.lowercase().substringAfterLast('.')) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }
}
