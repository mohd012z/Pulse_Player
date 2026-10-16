package com.pulseplayer.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ActivityNotFoundException
import android.media.MediaRouter2
import android.provider.Settings
import android.widget.Toast
import org.json.JSONArray
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.concurrent.thread

/** Fully native, dependency-light player UI; service owns playback. */
class MainActivity : Activity() {
    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 101
        private const val REQUEST_OPEN_AUDIO = 102
        private const val REQUEST_OPEN_FOLDER = 103
        private const val REQUEST_LYRICS = 104
        private const val REQUEST_SPECTRUM_PERMISSION = 105
        private const val EXTRA_SHORTCUT = "shortcut"
        private const val COLOR_BG = 0xFF0B1220L.toInt()
        private const val COLOR_PANEL = 0xFF182538L.toInt()
        private const val COLOR_ACCENT = 0xFF4DE0CCL.toInt()
        private const val COLOR_MUTED = 0xFFB6C3CDL.toInt()
    }

    private val clock = Handler(Looper.getMainLooper())
    private val uiExecutor = Executor { task -> clock.post(task) }
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val songs = ArrayList<MusicRepository.Song>()
    private val visibleSongs = ArrayList<MusicRepository.Song>()
    private val folderTracks = ArrayList<FolderAudioRepository.Track>()
    private val radioRepo by lazy { RadioRepository(this) }
    private val lyricsPrefs by lazy { getSharedPreferences("song_lyrics", MODE_PRIVATE) }
    private var lyricsTrackId: String? = null
    private var lyricLines: List<TimedLyrics.Line> = emptyList()
    private var lyricGeneration = 0
    private var spectrumView: SpectrumView? = null
    private var waveformDock: LinearLayout? = null
    private var activeSpectrumSession = -1
    private var spectrumRequestPending = false
    private var spectrumRetryAtMs = 0L
    private var customLow = 0
    private var customMid = 0
    private var customHigh = 0
    private val folderPrefs by lazy { getSharedPreferences("selected_folders", MODE_PRIVATE) }
    private var source = "DEVICE"
    private var folderScanGeneration = 0
    private var currentDisplaySize = 0
    private var scanGeneration = 0
    private var scanning = false
    private var pendingShortcut: String? = null
    private var pendingPickedUri: Uri? = null
    private var seeking = false
    private var bassStrength = 0

    private lateinit var lyricView: TextView
    private lateinit var titleView: TextView
    private lateinit var artistView: TextView
    private lateinit var progressView: TextView
    private lateinit var networkView: TextView
    private var lastRxBytes = -1L
    private var lastRxMs = 0L
    private lateinit var statusView: TextView
    private lateinit var libraryView: TextView
    private lateinit var sourceView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private lateinit var sourceButton: Button
    private lateinit var filterView: EditText
    private lateinit var trackList: ListView
    private lateinit var trackAdapter: ArrayAdapter<String>

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refreshNowPlaying()
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            status("Playback error: ${error.errorCodeName}. Try another audio file.")
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            refreshNowPlaying()
            if (controller?.isPlaying == true && spectrumView == null && !spectrumRequestPending &&
                android.os.SystemClock.elapsedRealtime() >= spectrumRetryAtMs) startSpectrumIfPermitted()
            refreshNetworkSpeed()
            clock.postDelayed(this, 750L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG
        buildUi()
        publishShortcuts()
        pendingShortcut = intent.getStringExtra(EXTRA_SHORTCUT)
    }

    override fun onStart() {
        super.onStart()
        connect()
        checkAudioPermissionAndScan()
        scanFolders()
        clock.post(ticker)
    }

    override fun onStop() {
        spectrumView?.stop()
        spectrumView = null
        activeSpectrumSession = -1
        clock.removeCallbacks(ticker)
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    override fun onDestroy() {
        lyricGeneration++
        spectrumView?.stop()
        scanGeneration++
        folderScanGeneration++
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShortcut = intent.getStringExtra(EXTRA_SHORTCUT)
        resolvePendingAction()
    }

    private fun connect() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture !== future || isFinishing || isDestroyed) return@addListener
            val result = runCatching { future.get() }.getOrElse {
                status("Cannot connect to playback service: ${it.message}")
                return@addListener
            }
            controller = result
            result.addListener(playerListener)
            refreshNowPlaying()
            fetchEffectCapabilities()
            pendingPickedUri?.let { uri ->
                pendingPickedUri = null
                playDocument(uri)
            }
            resolvePendingAction()
        }, uiExecutor)
    }

    private fun checkAudioPermissionAndScan() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            scanLibrary()
        } else {
            requestPermissions(arrayOf(permission), REQUEST_AUDIO_PERMISSION)
            status("Allow audio-library access to display device music. Open File works separately.")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SPECTRUM_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startSpectrumIfPermitted()
            } else {
                status("FFT visualization requires optional audio capture permission")
            }
        }
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                scanLibrary()
            } else {
                status("Music-library permission denied. Use Open File for individual tracks.")
            }
        }
    }

    private fun scanLibrary() {
        if (scanning) return
        scanning = true
        val generation = ++scanGeneration
        libraryView.text = "Scanning local music…"
        thread(name = "PulseLibraryScan", isDaemon = true) {
            val result = runCatching { MusicRepository(applicationContext).scan() }
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != scanGeneration) return@runOnUiThread
                scanning = false
                result.onSuccess { found ->
                    songs.clear()
                    songs.addAll(found)
                    filterLibrary()
                    resolvePendingAction()
                    status("Library ready: ${songs.size} audio tracks")
                }.onFailure { error ->
                    libraryView.text = "Unable to read library"
                    status("Library scan failed: ${error.message}")
                }
            }
        }
    }

    private fun filterLibrary() {
        val query = filterView.text?.toString()?.trim().orEmpty()
        visibleSongs.clear()
        visibleSongs.addAll((if (query.isEmpty()) songs else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
        }).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }) )
        val lines: List<String> = when (source) {
            "FOLDERS" -> folderTracks.filter { it.name.contains(query, true) || it.folder.contains(query, true) }
                .map { "${it.name}\n${it.folder}" }
            "RADIO", "MY RADIO" -> filteredStations(query)
                .map { "${it.name}\n${it.details()} · LIVE STREAM" }
            else -> visibleSongs.map { "${it.title}\n${it.artist}" }
        }
        currentDisplaySize = lines.size
        trackAdapter.clear()
        trackAdapter.addAll(lines)
        trackAdapter.notifyDataSetChanged()
        libraryView.text = "${source}  ·  $currentDisplaySize tracks / stations"
        if (::sourceView.isInitialized) sourceView.text = "${source.replace('_', ' ')} · ${currentDisplaySize} items"
        if (::sourceButton.isInitialized) sourceButton.text = "${source} ▾"
    }

    private fun playSelection(index: Int) {
        val c = controller ?: run { status("Connecting to player…"); return }
        val query = filterView.text?.toString()?.trim().orEmpty()
        val (queue, name) = when (source) {
            "FOLDERS" -> {
                val tracks = folderTracks.filter { it.name.contains(query, true) || it.folder.contains(query, true) }
                tracks.map { it.toMediaItem() } to tracks.getOrNull(index)?.name
            }
            "RADIO", "MY RADIO" -> {
                val stations = filteredStations(query)
                stations.map { it.toMediaItem() } to stations.getOrNull(index)?.name
            }
            else -> visibleSongs.map { it.toMediaItem() } to visibleSongs.getOrNull(index)?.title
        }
        if (index !in queue.indices) return
        c.setMediaItems(queue, index, 0L)
        c.prepare()
        c.play()
        status("Playing: ${name ?: "Selected track"}")
    }

    private fun switchPlaylist(next: String, startPlayback: Boolean = false) {
        source = next
        filterLibrary()
        if (startPlayback) {
            if (currentDisplaySize > 0) playSelection(0)
            else status("No tracks in $source. Import a folder or choose another playlist.")
        }
    }

    private fun rotatePlaylist() {
        val choices = listOf("DEVICE", "FOLDERS", "MY RADIO", "RADIO")
        switchPlaylist(choices[(choices.indexOf(source) + 1) % choices.size], true)
    }

    private fun folderUris(): List<Uri> = folderPrefs.getStringSet("tree_uris", emptySet())
        .orEmpty().mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }

    private fun openFolderPicker() {
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, REQUEST_OPEN_FOLDER)
        }.onFailure { status("Folder picker unavailable: ${it.message}") }
    }

    private fun scanFolders() {
        val generation = ++folderScanGeneration
        val trees = folderUris()
        if (trees.isEmpty()) {
            folderTracks.clear()
            filterLibrary()
            return
        }
        thread(name = "PulseFolderScan", isDaemon = true) {
            val found = runCatching { FolderAudioRepository(applicationContext).scanTrees(trees) }
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != folderScanGeneration) return@runOnUiThread
                found.onSuccess {
                    folderTracks.clear()
                    folderTracks.addAll(it)
                    filterLibrary()
                    status("Imported folders: ${folderTracks.size} audio files")
                }.onFailure { status("Folder scan failed: ${it.message}") }
            }
        }
    }

    private fun shareCurrentTrack() {
        val item = controller?.currentMediaItem ?: run { status("Choose a local song first"); return }
        val uri = item.localConfiguration?.uri ?: run { status("No file is selected"); return }
        shareLocalUri(uri)
    }

    private fun shareLocalUri(uri: Uri) {
        if (uri.scheme != "content") {
            status("Only local audio files are shareable; radio audio is not a music file")
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Audio file", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, "Share audio using")) }
            .onFailure { status("Cannot share this file: ${it.message}") }
    }

    private fun clearFolders() {
        AlertDialog.Builder(this)
            .setTitle("Remove imported folders?")
            .setMessage("The indexed folder list and persistent access permissions will be removed. Music files are not deleted.")
            .setPositiveButton("Remove") { _, _ ->
                folderUris().forEach { uri ->
                    runCatching { contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                folderPrefs.edit().remove("tree_uris").apply()
                folderTracks.clear()
                ++folderScanGeneration
                if (source == "FOLDERS") filterLibrary()
                status("Imported folder access cleared. No source files were modified.")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun onTrackLongPress(index: Int) {
        val query = filterView.text?.toString()?.trim().orEmpty()
        when (source) {
            "RADIO", "MY RADIO" -> {
                val station = filteredStations(query).getOrNull(index) ?: return
                AlertDialog.Builder(this)
                    .setTitle(station.name)
                    .setItems(arrayOf("Play station", "Save as favorite", "Remove from favorites")) { _, selected ->
                        when (selected) {
                            0 -> playSelection(index)
                            1 -> status(if (radioRepo.add(station.name, station.url))
                                "Added to radio favorites" else "Station already bookmarked")
                            2 -> status(if (radioRepo.remove(station.url))
                                "Removed from favorites" else "No removable favorite found")
                        }
                        filterLibrary()
                    }.show()
            }
            "FOLDERS" -> {
                val track = folderTracks.filter { it.name.contains(query, true) || it.folder.contains(query, true) }.getOrNull(index)
                track?.let { shareLocalUri(it.uri) }
            }
            else -> visibleSongs.getOrNull(index)?.let { shareLocalUri(it.uri) }
        }
    }

    private fun addRadioDialog() {
        val nameEdit = EditText(this).apply { hint = "Station name"; setSingleLine(true) }
        val urlEdit = EditText(this).apply {
            hint = "https://station.example/live.mp3"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(4))
            addView(nameEdit)
            addView(urlEdit)
        }
        AlertDialog.Builder(this)
            .setTitle("Add HTTPS radio station")
            .setMessage("Enter a direct MP3, AAC, or HLS (.m3u8) stream URL. Playlist links ending .pls/.m3u are not supported.")
            .setView(fields)
            .setPositiveButton("Add") { _, _ ->
                if (radioRepo.add(nameEdit.text.toString(), urlEdit.text.toString())) {
                    switchPlaylist("RADIO")
                    status("Radio station saved")
                } else status("Invalid or duplicate station. Use a direct HTTPS audio stream URL.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openSystemSettings(action: String) {
        runCatching { startActivity(Intent(action)) }
            .onFailure { status("This system settings screen is not available: ${it.message}") }
    }

    private fun chooseAudioOutput() {
        if (Build.VERSION.SDK_INT >= 34) {
            val opened = runCatching { MediaRouter2.getInstance(this).showSystemOutputSwitcher() }.getOrDefault(false)
            if (opened) return
        }
        // Android controls pairing / routing; app does not force-connect arbitrary Bluetooth devices.
        openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
        status("Select an output using Android audio controls; Bluetooth settings opened as fallback")
    }

    private fun selectMixPreset() {
        val options = SoundPresets.featuredNames.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Sound mixer · EQ profile")
            .setSingleChoiceItems(options, -1) { dialog, which ->
                val name = options[which]
                customCommand(PlaybackService.CMD_SET_PRESET, Bundle().apply {
                    putString(PlaybackService.ARG_PRESET, name)
                }) { _, ok -> status(if (ok) "Mixer EQ: $name" else "Device equalizer unavailable") }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun discoverMalaysiaRadio() {
        status("Searching public Malaysia internet-radio directory…")
        thread(name = "PulseMalaysiaRadioScan", isDaemon = true) {
            val result = runCatching { MalaysiaRadioDirectory().scan() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { found ->
                    if (found.isNotEmpty() && radioRepo.replaceDiscovery(found)) {
                        switchPlaylist("MY RADIO")
                        status("Saved ${found.size} Malaysia radio directory entries. Long-press to favorite.")
                    } else {
                        status("No confirmed HTTPS Malaysian stations found. Saved stations were kept.")
                    }
                }.onFailure { error ->
                    status("Radio scan unavailable: ${error.message}. Saved radio stations remain available.")
                }
            }
        }
    }

    private fun lyricKey(id: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun lyricsForCurrent() {
        val item = controller?.currentMediaItem ?: run { status("Play a local song first"); return }
        val uri = item.localConfiguration?.uri ?: return
        if (uri.scheme != "content") {
            status("Lyrics import is available for local music, not internet radio")
            return
        }
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        runCatching { startActivityForResult(picker, REQUEST_LYRICS) }
            .onFailure { status("Cannot open LRC subtitle picker: ${it.message}") }
    }

    private fun refreshLyricsIfNeeded() {
        val item = controller?.currentMediaItem
        val id = item?.mediaId?.takeIf { it.isNotBlank() }
            ?: item?.localConfiguration?.uri?.toString()
        if (lyricsTrackId == id) return
        lyricsTrackId = id
        lyricLines = emptyList()
        if (!::lyricView.isInitialized) return
        lyricView.text = "♫ Lyrics: choose More → Lyrics → Import .lrc"
        if (id == null) return
        val saved = lyricsPrefs.getString(lyricKey(id), null) ?: return
        parseLyricsAsync(id, Uri.parse(saved))
    }

    private fun parseLyricsAsync(songId: String, lyricUri: Uri) {
        val generation = ++lyricGeneration
        thread(name = "PulseLrcReader", isDaemon = true) {
            val result = runCatching {
                val buffer = java.io.ByteArrayOutputStream()
                contentResolver.openInputStream(lyricUri)?.use { input ->
                    val part = ByteArray(4096)
                    while (true) {
                        val n = input.read(part)
                        if (n < 0) break
                        check(buffer.size() + n <= 1_000_000) { "LRC file exceeds 1 MB" }
                        buffer.write(part, 0, n)
                    }
                } ?: error("Cannot read selected LRC file")
                TimedLyrics.parse(buffer.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF"))
            }
            runOnUiThread {
                if (generation != lyricGeneration || lyricsTrackId != songId || isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess {
                    lyricLines = it
                    status(if (it.isEmpty()) "LRC has no valid timed lines" else "Loaded ${it.size} synchronized LRC subtitles")
                }.onFailure { error ->
                    lyricLines = emptyList()
                    status("Cannot load lyrics: ${error.message}")
                }
            }
        }
    }

    private fun showLyricDialog() {
        if (lyricLines.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Synchronized song lyrics")
                .setMessage("Import a local .lrc file for the current song first. No copyrighted lyrics are fetched automatically.")
                .setPositiveButton("Import .lrc") { _, _ -> lyricsForCurrent() }
                .setNegativeButton("Close", null).show()
            return
        }
        val text = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(18), dp(16), dp(18))
            text = lyricLines.take(600).joinToString("\n\n") { it.text }
        }
        val scroll = ScrollView(this).apply { addView(text) }
        AlertDialog.Builder(this)
            .setTitle("Lyrics · synchronized line shown in mini-player")
            .setView(scroll)
            .setPositiveButton("Import another .lrc") { _, _ -> lyricsForCurrent() }
            .setNegativeButton("Close", null).show()
    }

    private fun showPerformanceSettings() {
        val tiers = arrayOf("Automatic", "Lite · battery saver", "Balanced", "High · detailed visuals")
        val prefs = getSharedPreferences("player_state", MODE_PRIVATE)
        val current = prefs.getString("performance_tier", null)
        val selected = when (current) {
            "LITE" -> 1
            "BALANCED" -> 2
            "HIGH" -> 3
            else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle("Playback & visual performance")
            .setMessage("Current: ${DevicePerformanceProfile.description(this)}. Lower profiles reduce waveform drawing workload; audio quality is unchanged. Restart the waveform after changing.")
            .setSingleChoiceItems(tiers, selected) { dialog, index ->
                val value = when (index) {
                    1 -> "LITE"
                    2 -> "BALANCED"
                    3 -> "HIGH"
                    else -> null
                }
                prefs.edit().apply {
                    if (value == null) remove("performance_tier") else putString("performance_tier", value)
                }.apply()
                spectrumView?.stop()
                status("Performance: ${DevicePerformanceProfile.description(this)}")
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomEqualizer() {
        val values = intArrayOf(customLow, customMid, customHigh)
        val labels = arrayOf("Bass (under 250 Hz)", "Midrange (250–4000 Hz)", "Treble (over 4000 Hz)")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(8))
        }
        labels.forEachIndexed { index, name ->
            val text = label("$name · ${values[index]} dB", 13f, COLOR_MUTED)
            layout.addView(text)
            val slider = SeekBar(this).apply {
                max = 12
                progress = values[index] + 6
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(bar: SeekBar) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar) = Unit
                    override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                        values[index] = progress - 6
                        text.text = "$name · ${values[index]} dB"
                    }
                })
            }
            layout.addView(slider)
        }
        AlertDialog.Builder(this)
            .setTitle("Custom EQ · ±6 dB per region")
            .setMessage("Adjust conservatively to avoid distortion. Uses device EQ, not a full parametric processor.")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                customCommand(PlaybackService.CMD_SET_CUSTOM, Bundle().apply {
                    putInt(PlaybackService.ARG_CUSTOM_BASS, values[0])
                    putInt(PlaybackService.ARG_CUSTOM_MID, values[1])
                    putInt(PlaybackService.ARG_CUSTOM_TREBLE, values[2])
                }) { _, ok ->
                    if (ok) {
                        customLow = values[0]
                        customMid = values[1]
                        customHigh = values[2]
                    }
                    status(if (ok) "Custom EQ applied" else "Device equalizer unavailable; settings saved for compatible output")
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun requestSpectrum() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle("Optional live audio visualizer")
                .setMessage("Android requires audio-capture permission for the live pixel-cloud waveform. PulsePlayer displays the current audio session only, does not save captured sound, and stops capture when the visualizer is closed.")
                .setPositiveButton("Allow") { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_SPECTRUM_PERMISSION)
                }
                .setNegativeButton("Cancel", null).show()
        } else showSpectrum()
    }

    private fun showSpectrum() {
        val row = waveformDock ?: return
        if (row.visibility == android.view.View.VISIBLE) {
            spectrumRetryAtMs = android.os.SystemClock.elapsedRealtime() + 60_000L
            spectrumView?.stop()
            spectrumView = null
            activeSpectrumSession = -1
            row.removeAllViews()
            row.visibility = android.view.View.GONE
        } else {
            startSpectrumIfPermitted()
        }
    }

    private fun startSpectrumIfPermitted() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (spectrumRequestPending || controller?.isPlaying != true) return
        spectrumRetryAtMs = android.os.SystemClock.elapsedRealtime() + 5_000L
        spectrumRequestPending = true
        customCommand(PlaybackService.CMD_STATUS) { extras, success ->
            spectrumRequestPending = false
            if (!success || isFinishing || isDestroyed || controller?.isPlaying != true) return@customCommand
            val sessionId = extras.getInt(PlaybackService.ARG_AUDIO_SESSION_ID, -1)
            if (sessionId <= 0 || (spectrumView != null && activeSpectrumSession == sessionId)) return@customCommand
            val row = waveformDock ?: return@customCommand
            spectrumView?.stop()
            spectrumView = null
            activeSpectrumSession = -1
            row.removeAllViews()
            val view = SpectrumView(this).apply { setBackgroundColor(COLOR_PANEL) }
            if (!view.start(sessionId)) {
                row.visibility = android.view.View.GONE
                return@customCommand
            }
            spectrumView = view
            activeSpectrumSession = sessionId
            row.addView(view, LinearLayout.LayoutParams(-1, dp(46)))
            row.visibility = android.view.View.VISIBLE
        }
    }

    private fun playDocument(uri: Uri) {
        val c = controller ?: run { pendingPickedUri = uri; return }
        var name = "Selected audio file"
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0)?.ifBlank { name } ?: name
                    }
                }
        }
        val media = MediaItem.Builder().setUri(uri)
            .setMediaId(uri.toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build())
            .build()
        c.setMediaItem(media)
        c.prepare()
        c.play()
        status("Opened: $name")
    }

    private fun openFilePicker() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(picker, REQUEST_OPEN_AUDIO)
    }

    @Deprecated("Kept for standalone Activity with no AndroidX Activity dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN_FOLDER && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val persist = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (persist == 0) {
                status("Folder permission not granted")
                return
            }
            val saved = runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            }.getOrDefault(false)
            if (!saved) {
                status("This file provider does not support persistent folder access")
                return
            }
            val selections = folderPrefs.getStringSet("tree_uris", emptySet()).orEmpty().toMutableSet()
            selections.add(uri.toString())
            folderPrefs.edit().putStringSet("tree_uris", selections).apply()
            switchPlaylist("FOLDERS")
            scanFolders()
        }
        if (requestCode == REQUEST_LYRICS && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val id = controller?.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
                ?: controller?.currentMediaItem?.localConfiguration?.uri?.toString()
            if (id == null) {
                status("Play a local track before attaching LRC lyrics")
                return
            }
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            lyricsPrefs.edit().putString(lyricKey(id), uri.toString()).apply()
            lyricsTrackId = id
            parseLyricsAsync(id, uri)
        }
        if (requestCode == REQUEST_OPEN_AUDIO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.onFailure { status("File opened for this session; persistable access unavailable") }
            playDocument(uri)
        }
    }

    private fun resolvePendingAction() {
        val action = pendingShortcut ?: return
        val c = controller ?: return
        if (action == "shuffle" && songs.isEmpty() && scanning) return
        when (action) {
            "resume" -> {
                if (c.mediaItemCount > 0) {
                    c.prepare()
                    c.play()
                } else if (visibleSongs.isNotEmpty()) {
                    playSelection(0)
                } else status("No previous track. Choose a song or Open File.")
            }
            "shuffle" -> {
                if (songs.isNotEmpty()) {
                    // Shuffle all songs, regardless of the current search filter.
                    c.setMediaItems(songs.shuffled().map { it.toMediaItem() })
                    c.shuffleModeEnabled = true
                    c.prepare()
                    c.play()
                } else status("No music in the device library")
            }
            "radio" -> { switchPlaylist("RADIO", true) }
            "next" -> {
                if (c.hasNextMediaItem()) {
                    c.seekToNextMediaItem()
                    c.play()
                } else status("No next track in the queue")
            }
        }
        pendingShortcut = null
    }

    private fun publishShortcuts() {
        if (Build.VERSION.SDK_INT < 25) return
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
        val shortcuts = listOf(
            "resume" to "Resume playback",
            "shuffle" to "Shuffle all",
            "next" to "Next track",
            "radio" to "Internet radio"
        ).map { (action, label) ->
            val open = Intent(this, MainActivity::class.java).apply {
                this.action = Intent.ACTION_VIEW
                putExtra(EXTRA_SHORTCUT, action)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            ShortcutInfo.Builder(this, action)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(Icon.createWithResource(this, R.drawable.ic_launcher))
                .setIntent(open)
                .build()
        }
        runCatching { shortcutManager.setDynamicShortcuts(shortcuts) }
            .onFailure { status("Launcher shortcuts unavailable on this device") }
    }

    private fun refreshNetworkSpeed() {
        if (!::networkView.isInitialized) return
        val now = android.os.SystemClock.elapsedRealtime()
        val bytes = android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid())
        if (bytes < 0) {
            networkView.text = "Network: unavailable"
        } else if (lastRxBytes >= 0 && now > lastRxMs) {
            val kbps = ((bytes - lastRxBytes).coerceAtLeast(0L) * 8.0 / (now - lastRxMs))
            networkView.text = if (controller?.isPlaying == true && source.contains("RADIO"))
                String.format(Locale.US, "App network ↓ %.0f kbps", kbps)
            else "Network: idle / app total"
        }
        lastRxBytes = bytes
        lastRxMs = now
    }

    private fun refreshNowPlaying() {
        val c = controller
        val media = c?.currentMediaItem
        titleView.text = media?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: "Choose a track"
        artistView.text = media?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: "Local audio • ExoPlayer"
        playButton.text = ""
        playButton.setCompoundDrawablesWithIntrinsicBounds(
            ActionIconDrawable(if (c?.isPlaying == true) "pause" else "play", dp(21)), null, null, null)
        playButton.contentDescription = if (c?.isPlaying == true) "Pause" else "Play"
        val duration = c?.duration ?: C.TIME_UNSET
        val current = c?.currentPosition?.coerceAtLeast(0L) ?: 0L
        progressView.text = "${formatTime(current)}  /  ${formatTime(duration)}"
        refreshLyricsIfNeeded()
        val lyricIndex = TimedLyrics.activeIndex(lyricLines, current)
        lyricView.text = if (lyricIndex >= 0) "♫ ${lyricLines[lyricIndex].text}"
            else if (lyricLines.isNotEmpty()) "♫ Lyrics begin soon…"
            else "♫ Lyrics: More → Lyrics → Import .lrc"
        if (!seeking) {
            if (duration > 0L && duration != C.TIME_UNSET && c?.isCurrentMediaItemLive != true) {
                seekBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                seekBar.progress = current.coerceAtMost(seekBar.max.toLong()).toInt()
                seekBar.isEnabled = true
            } else {
                seekBar.progress = 0
                seekBar.max = 100
                seekBar.isEnabled = false
            }
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0 || ms == C.TIME_UNSET) return "--:--"
        val seconds = ms / 1000L
        return if (seconds >= 3600L) {
            String.format(Locale.US, "%d:%02d:%02d", seconds / 3600L, (seconds / 60L) % 60L, seconds % 60L)
        } else String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L)
    }

    private fun status(message: String) {
        if (::statusView.isInitialized) statusView.text = message
    }

    private fun customCommand(action: String, bundle: Bundle = Bundle.EMPTY, onResult: ((Bundle, Boolean) -> Unit)? = null) {
        val c = controller ?: run { status("Playback service not connected"); return }
        val future = c.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), bundle)
        future.addListener({
            val result = runCatching { future.get() }.getOrElse {
                status("Audio command failed: ${it.message}")
                return@addListener
            }
            val success = result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS
            onResult?.invoke(result.extras, success)
            if (!success && onResult == null) status("Effect unavailable on current audio output")
        }, uiExecutor)
    }

    private fun fetchEffectCapabilities() {
        customCommand(PlaybackService.CMD_STATUS) { result, ok ->
            if (!ok) return@customCommand
            val eq = result.getBoolean(PlaybackService.ARG_EQ_SUPPORTED)
            val bass = result.getBoolean(PlaybackService.ARG_BASS_SUPPORTED)
            val preset = result.getString(PlaybackService.ARG_PRESET, "Flat")
            val strength = result.getInt(PlaybackService.ARG_BASS, 0)
            bassStrength = strength
            customLow = result.getInt(PlaybackService.ARG_CUSTOM_BASS, 0)
            customMid = result.getInt(PlaybackService.ARG_CUSTOM_MID, 0)
            customHigh = result.getInt(PlaybackService.ARG_CUSTOM_TREBLE, 0)
            status("Audio effects: EQ ${if (eq) "available" else "unsupported"}, Bass ${if (bass) "available" else "unsupported"} • $preset")
        }
    }

    private fun filteredStations(query: String): List<RadioRepository.Station> =
        radioRepo.stations().filter { station ->
            (source != "MY RADIO" || station.name.startsWith("MY ·")) &&
                station.name.contains(query, ignoreCase = true)
        }

    private fun openProvider(destination: String) {
        val url = when (destination) {
            "YouTube" -> "https://www.youtube.com/"
            "YouTube Music" -> "https://music.youtube.com/"
            "YouTube Shorts" -> "https://www.youtube.com/shorts"
            "YouTube Live TV" -> "https://www.youtube.com/results?search_query=live+tv+streaming&sp=EgJAAQ%253D%253D"
            "Short Drama" -> "https://www.youtube.com/results?search_query=short+drama"
            "JOOX" -> "https://www.joox.com/"
            "Spotify" -> "https://open.spotify.com/"
            "RTM Radio Malaysia" -> "https://radio.rtm.gov.my/"
            "BFM 89.9" -> "https://www.bfm.my/"
            "ERA Malaysia" -> "https://era.je/"
            "MY FM" -> "https://my.com.my/"
            else -> return
        }
        runCatching {
            if (destination in setOf("Spotify", "JOOX", "YouTube", "YouTube Music", "YouTube Shorts", "Short Drama", "YouTube Live TV")) {
                startActivity(Intent(this, ProviderBrowserActivity::class.java).apply {
                    putExtra(ProviderBrowserActivity.EXTRA_PROVIDER, destination)
                })
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                })
            }
        }.onFailure {
            status("Cannot open $destination: ${it.message}")
        }
    }

    private fun showPicker(title: String, options: Array<String>, selected: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                selected(which)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPlaylistDialog() = showPicker(
        "Choose playlist / source",
        arrayOf("Device MP3 library", "Imported folders", "Malaysia radio", "All radio stations", "Next playlist and play")
    ) { index ->
        when (index) {
            0 -> switchPlaylist("DEVICE")
            1 -> switchPlaylist("FOLDERS")
            2 -> switchPlaylist("MY RADIO")
            3 -> switchPlaylist("RADIO")
            4 -> rotatePlaylist()
        }
    }

    private fun showRadioHealth() {
        val stations = radioRepo.stations().filter { it.name.startsWith("MY ·") }
        if (stations.isEmpty()) { status("No Malaysia stations saved"); return }
        status("Checking station stream responses in background…")
        thread(name = "PulseRadioHealth", isDaemon = true) {
            val lines = stations.map { station ->
                val result = runCatching {
                    val connection = (java.net.URL(station.url).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 3500
                        readTimeout = 3500
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "PulsePlayer/RadioHealth")
                        setRequestProperty("Range", "bytes=0-0")
                    }
                    try {
                        val code = connection.responseCode
                        val type = connection.contentType.orEmpty()
                        if (code in 200..299) "HTTP $code · $type" else "HTTP $code"
                    } finally { connection.disconnect() }
                }.getOrElse { "Unreachable: ${it.javaClass.simpleName}" }
                "${station.name} — $result"
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val view = TextView(this).apply {
                    text = lines.joinToString("\n\n")
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                }
                AlertDialog.Builder(this).setTitle("Malaysia radio · stream checks")
                    .setMessage("HTTP checks do not guarantee successful audio decoding or playback.")
                    .setView(ScrollView(this).apply { addView(view) })
                    .setPositiveButton("Close", null).show()
            }
        }
    }

    private fun showRadioTuner() {
        val stations = radioRepo.stations().filter { it.name.startsWith("MY ·") }
        if (stations.isEmpty()) {
            status("Discover Malaysia radio stations first")
            return
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        val current = label(stations.first().name, 16f, Color.WHITE, true)
        val detail = label(stations.first().details() + " · internet stream", 12f, COLOR_MUTED)
        val slider = SeekBar(this).apply { max = stations.lastIndex; progress = 0 }
        container.addView(current)
        container.addView(detail)
        container.addView(slider)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                current.text = stations[progress].name
                detail.text = "Station ${progress + 1} / ${stations.size} · ${stations[progress].details()}"
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        AlertDialog.Builder(this)
            .setTitle("Malaysia Radio · manual tuning")
            .setView(container)
            .setPositiveButton("Play selected") { _, _ ->
                val station = stations[slider.progress]
                val c = controller
                if (c == null) status("Playback service not connected")
                else {
                    c.setMediaItem(station.toMediaItem())
                    c.prepare()
                    c.play()
                    status("Playing ${station.name}")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMalaysiaRadioQuickMenu() = showPicker("Malaysia radio · stations", arrayOf(
        "Browse Malaysia radio stations", "Discover more Malaysia stations (online)",
        "All saved radio stations", "Add a radio stream", "Manual station tuning slider", "Check Malaysia stream responses"
    )) { selected ->
        when (selected) {
            0 -> switchPlaylist("MY RADIO")
            1 -> discoverMalaysiaRadio()
            2 -> switchPlaylist("RADIO")
            3 -> addRadioDialog()
            4 -> showRadioTuner()
            5 -> showRadioHealth()
        }
    }

    private fun showVideoServices() = showPicker(
        "YouTube & Short Drama",
        arrayOf("YouTube videos", "YouTube Shorts", "Short Drama", "Live TV Dashboard", "YouTube Music")
    ) { index ->
        if (index == 3) showLiveTvDashboard() else openProvider(arrayOf("YouTube", "YouTube Shorts", "Short Drama", "YouTube Music")[if (index > 3) index - 1 else index])
    }

    private fun showLiveTvDashboard() {
        val prefs = getSharedPreferences("live_tv_favorites", MODE_PRIVATE)
        val categories = arrayOf("Malaysia", "News", "Sports", "Music", "Entertainment", "Education")
        fun openLiveSearch(term: String) {
            startActivity(Intent(this, ProviderBrowserActivity::class.java).apply {
                putExtra(ProviderBrowserActivity.EXTRA_PROVIDER, "YouTube Live TV")
                putExtra(ProviderBrowserActivity.EXTRA_SEARCH, term)
            })
        }
        val actions = arrayOf(
            "▶ Live channels · Malaysia",
            "◉ Live News",
            "◉ Live Sports",
            "♫ Live Music",
            "▣ Live Entertainment",
            "▣ Live Education",
            "★ Saved favorite searches",
            "★ Manage / remove favorites",
            "＋ Add favorite search",
            "⌕ Custom channel / live search",
            "▶ Browse all YouTube Live"
        )
        showPicker("Live TV · channel discovery", actions) { index ->
            when (index) {
                in 0..5 -> openLiveSearch(categories[index] + " live tv")
                6 -> {
                    val favorites = prefs.all.keys.sorted()
                    if (favorites.isEmpty()) {
                        status("No saved Live TV searches yet")
                    } else {
                        showPicker("Favorite live searches", favorites.toTypedArray()) { selected ->
                            openLiveSearch(favorites[selected])
                        }
                    }
                }
                7 -> {
                    val favorites = prefs.all.keys.sorted()
                    if (favorites.isEmpty()) {
                        status("No saved Live TV searches to remove")
                    } else {
                        showPicker("Remove saved search", favorites.toTypedArray()) { selected ->
                            val term = favorites[selected]
                            AlertDialog.Builder(this)
                                .setTitle("Remove favorite?")
                                .setMessage(term)
                                .setPositiveButton("Remove") { _, _ ->
                                    prefs.edit().remove(term).apply()
                                    status("Removed favorite: $term")
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                }
                8, 9 -> {
                    val input = EditText(this).apply {
                        hint = "Channel or live TV search"
                        setSingleLine(true)
                    }
                    AlertDialog.Builder(this)
                        .setTitle(if (index == 8) "Save Live TV search" else "Search YouTube Live")
                        .setView(input)
                        .setPositiveButton(if (index == 8) "Save" else "Search") { _, _ ->
                            val query = input.text.toString().trim().take(100)
                            if (query.isBlank()) {
                                status("Enter a channel or search term")
                            } else if (index == 8) {
                                prefs.edit().putBoolean(query, true).apply()
                                status("Saved favorite: $query")
                            } else {
                                openLiveSearch(query + " live")
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                10 -> openProvider("YouTube Live TV")
            }
        }
    }

    private fun showMoreMenu() = showPicker("Pulse Player · More", arrayOf(
        "Import / file manager", "Online music services", "Malaysian radio", "Audio mixer / effects",
        "Lyrics / subtitles", "Live audio spectrum", "Playback preferences", "Bluetooth / Wi-Fi / output", "Share current song", "Download & Convert (local video)", "Authorized direct download", "Device performance profile"
    )) { index ->
        when (index) {
            0 -> showPicker("Import music", arrayOf("Open audio file", "Import folder", "Remove saved folder permissions")) {
                when (it) { 0 -> openFilePicker(); 1 -> openFolderPicker(); 2 -> clearFolders() }
            }
            1 -> showPicker("Online music · official services", arrayOf("YouTube", "YouTube Music", "YouTube Shorts", "Short Drama", "YouTube Live TV", "JOOX", "Spotify")) {
                openProvider(arrayOf("YouTube", "YouTube Music", "YouTube Shorts", "Short Drama", "YouTube Live TV", "JOOX", "Spotify")[it])
            }
            2 -> showPicker("Malaysia radio", arrayOf(
                "Play Malaysia radio list", "Auto-discover MY stations & save", "All saved radio", "Add HTTPS stream",
                "Manual station tuning slider", "RTM official radio (stations)", "BFM 89.9 official player",
                "ERA official player", "MY FM official player"
            )) {
                when (it) {
                    0 -> switchPlaylist("MY RADIO")
                    1 -> discoverMalaysiaRadio()
                    2 -> switchPlaylist("RADIO")
                    3 -> addRadioDialog()
                    4 -> showRadioTuner()
                    5 -> openProvider("RTM Radio Malaysia")
                    6 -> openProvider("BFM 89.9")
                    7 -> openProvider("ERA Malaysia")
                    8 -> openProvider("MY FM")
                }
            }
            3 -> showAudioMenu()
            4 -> showPicker("Lyrics / subtitles", arrayOf("Import local .lrc for playing track", "View lyrics", "Subtitle & translation studio")) {
                when (it) { 0 -> lyricsForCurrent(); 1 -> showLyricDialog(); 2 -> startActivity(Intent(this, SubtitleStudioActivity::class.java)) }
            }
            5 -> requestSpectrum()
            6 -> showPlaybackMenu()
            7 -> showPicker("Connection options", arrayOf("Android Bluetooth settings", "Change audio output", "Android Wi-Fi settings")) {
                when (it) {
                    0 -> openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                    1 -> chooseAudioOutput()
                    2 -> openSystemSettings(Settings.ACTION_WIFI_SETTINGS)
                }
            }
            8 -> shareCurrentTrack()
            9 -> startActivity(Intent(this, MediaExportActivity::class.java))
            10 -> startActivity(Intent(this, AuthorizedDownloadActivity::class.java))
            11 -> showPerformanceSettings()
        }
    }

    private fun showAudioDiagnostics() {
        val c = controller
        if (c == null) { status("Connect the playback service first"); return }
        val audioFormats = c.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group ->
                (0 until group.length).filter { group.isTrackSelected(it) }.map { group.getTrackFormat(it) }
            }
        val format = audioFormats.firstOrNull()
        val item = c.currentMediaItem
        val sourceUri = item?.localConfiguration?.uri
        val source = when (sourceUri?.scheme?.lowercase(Locale.US)) {
            "content", "file" -> "Local audio"
            "https", "http" -> "Network stream (quality depends on source)"
            else -> "Unknown / no selected media"
        }
        val bitrate = format?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps (declared)" } ?: "Not reported"
        val sampleRate = format?.sampleRate?.takeIf { it > 0 }?.let { "$it Hz" } ?: "Not reported"
        val channels = format?.channelCount?.takeIf { it > 0 }?.toString() ?: "Not reported"
        val codec = format?.sampleMimeType ?: "Not reported"
        val message = """
            Source: $source
            Codec: $codec
            Bitrate: $bitrate
            Sample rate: $sampleRate
            Channels: $channels

            These are stream metadata, not measured fidelity or loudness. Low bitrate, clipping,
            distortion and channel imbalance require separate PCM analysis.

            For weak audio: try Flat first, then Noise Softening if needed. Avoid stacking
            maximum BassBoost with boosted EQ bands; use moderate listening volume.

            Pulse Player effects apply to its own playback session, not protected audio
            playing inside YouTube, Spotify or JOOX.
        """.trimIndent()
        AlertDialog.Builder(this).setTitle("Audio quality · diagnostics")
            .setMessage(message)
            .setPositiveButton("Use Flat EQ") { _, _ ->
                customCommand(PlaybackService.CMD_SET_PRESET, Bundle().apply {
                    putString(PlaybackService.ARG_PRESET, "Flat")
                }) { _, ok -> status(if (ok) "Flat EQ selected" else "EQ unavailable on this route") }
            }
            .setNeutralButton("Noise Softening") { _, _ ->
                customCommand(PlaybackService.CMD_SET_PRESET, Bundle().apply {
                    putString(PlaybackService.ARG_PRESET, "Noise Softening")
                }) { _, ok -> status(if (ok) "Noise Softening selected" else "EQ unavailable on this route") }
            }
            .setNegativeButton("Close", null).show()
    }

    private fun showAudioMenu() = showPicker("Sound · local and radio only", arrayOf(
        "Choose EQ preset", "NovaStream TV cinematic EQ", "Custom 3-band equalizer", "Bass strength", "Noise Softening (EQ only)", "Audio quality diagnostics", "Device speaker / spatial / refresh scan", "Android system sound settings"
    )) {
        when (it) {
            0 -> selectMixPreset()
            1 -> customCommand(PlaybackService.CMD_SET_PRESET, Bundle().apply {
                putString(PlaybackService.ARG_PRESET, "NovaStream TV")
            }) { _, ok -> status(if (ok) "NovaStream TV cinematic EQ active" else "EQ unavailable on this output") }
            2 -> showCustomEqualizer()
            3 -> showBassDialog()
            4 -> customCommand(PlaybackService.CMD_SET_PRESET, Bundle().apply {
                putString(PlaybackService.ARG_PRESET, "Noise Softening")
            }) { _, ok -> status(if (ok) "Noise Softening EQ applied; this is not AI restoration" else "EQ unavailable") }
            5 -> showAudioDiagnostics()
            6 -> showDeviceSoundCapabilities()
            7 -> openSystemSettings(Settings.ACTION_SOUND_SETTINGS)
        }
    }

    private fun showDeviceSoundCapabilities() {
        val manager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val outputs = manager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
        val routeNames = outputs.map { device ->
            val type = when (device.type) {
                android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth stereo"
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth call audio"
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
                android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
                android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
                android.media.AudioDeviceInfo.TYPE_HDMI -> "HDMI audio"
                else -> "Other output (type ${device.type})"
            }
            "$type: ${device.productName}"
        }
        val spatial = if (android.os.Build.VERSION.SDK_INT >= 32) {
            val spatializer = manager.spatializer
            "Spatial audio supported: ${spatializer.immersiveAudioLevel != android.media.Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE}\n" +
                "Available for current route: ${spatializer.isAvailable}\n" +
                "Enabled in Android: ${spatializer.isEnabled}"
        } else "Spatial audio capability check requires Android 12L or newer."
        val display = windowManager.defaultDisplay
        val modes = display.supportedModes.map { it.refreshRate }.distinct().sorted()
        val message = """
            Connected output devices (not necessarily the active media route):
            ${routeNames.ifEmpty { listOf("No output reported") }.joinToString("\n")}

            $spatial

            Display refresh rate: ${display.refreshRate} Hz
            Supported display modes: ${modes.joinToString(", ")} Hz

            Audio recommendations:
            • Built-in speakers: start with Flat; apply modest EQ, avoid extreme bass.
            • Headphones: use supported Android spatial audio if available.
            • Bluetooth: audio codec and device DSP affect quality.
            • EQ/BassBoost only affect Pulse Player playback, not YouTube WebView audio.

            Display refresh rate is separate from video FPS and audio quality.
            The system decides whether to switch refresh rate.
        """.trimIndent()
        AlertDialog.Builder(this).setTitle("Sound engine · device capability scan")
            .setMessage(message)
            .setPositiveButton("Sound settings") { _, _ ->
                openSystemSettings(Settings.ACTION_SOUND_SETTINGS)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showBassDialog() {
        val slider = SeekBar(this).apply {
            max = 1000
            progress = bassStrength
        }
        AlertDialog.Builder(this)
            .setTitle("Bass boost · device dependent")
            .setMessage("0 = disabled. Keep a moderate listening level to avoid clipping.")
            .setView(slider)
            .setPositiveButton("Apply") { _, _ ->
                val level = slider.progress
                customCommand(PlaybackService.CMD_SET_BASS, Bundle().apply {
                    putInt(PlaybackService.ARG_BASS, level)
                }) { _, success ->
                    if (success) bassStrength = level
                    status(if (success) "Bass: $level / 1000" else "BassBoost not supported on this output")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPlaybackMenu() = showPicker("Playback options", arrayOf(
        "Toggle shuffle", "Cycle repeat", "Change speed", "Sleep timer", "Seek back 10s", "Seek forward 10s"
    )) {
        val c = controller
        when (it) {
            0 -> c?.let { player ->                player.shuffleModeEnabled = !player.shuffleModeEnabled
                status(if (player.shuffleModeEnabled) "Shuffle ON" else "Shuffle OFF")
            }
            1 -> c?.let { player ->
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                status("Repeat mode: ${player.repeatMode}")
            }
            2 -> c?.let { player ->
                val speeds = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                val current = speeds.indexOfFirst { speed -> kotlin.math.abs(speed - player.playbackParameters.speed) < 0.01f }
                val next = speeds[(current + 1).coerceAtLeast(0) % speeds.size]
                player.setPlaybackParameters(PlaybackParameters(next))
                status("Speed: ${next}×")
            }
            3 -> showPicker("Stop playback after", arrayOf("Off", "15 minutes", "30 minutes", "60 minutes")) { index ->
                val minutes = intArrayOf(0, 15, 30, 60)[index]
                customCommand(PlaybackService.CMD_SET_SLEEP, Bundle().apply {
                    putInt(PlaybackService.ARG_MINUTES, minutes)
                }) { _, success ->
                    if (success) status(if (minutes == 0) "Sleep timer off" else "Sleep in ${minutes} minutes")
                }
            }
            4 -> seekRelative(-10_000L)
            5 -> seekRelative(10_000L)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(dp(12) + bars.left, dp(8) + bars.top,
                    dp(12) + bars.right, dp(8) + bars.bottom)
                insets
            }
        }
        setContentView(root)

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val brand = label("PULSE  /  STUDIO", 18f, COLOR_ACCENT, true)
        toolbar.addView(brand, LinearLayout.LayoutParams(0, dp(48), 1f))
        toolbar.addView(button("Menu") { showMoreMenu() }, LinearLayout.LayoutParams(dp(88), dp(40)))
        root.addView(toolbar)
        sourceButton = button("DEVICE ▾") { showPlaylistDialog() }
        root.addView(sourceButton, LinearLayout.LayoutParams(-1, dp(38)).apply { bottomMargin = dp(3) })

        sourceView = label("DEVICE · 0 items", 12f, COLOR_ACCENT, true)
        root.addView(sourceView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        filterView = EditText(this).apply {
            hint = "Search MP3 / artist / folder / station"
            setHintTextColor(0xFF8296A7L.toInt())
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = panelBackground()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filterLibrary()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        root.addView(filterView, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(7) })
        libraryView = label("Music library", 12f, COLOR_MUTED)
        root.addView(libraryView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        trackAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, arrayListOf<String>()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.textSize = 13.5f
                view.setPadding(dp(12), dp(10), dp(10), dp(10))
                return view
            }
        }
        trackList = ListView(this).apply {
            adapter = trackAdapter
            dividerHeight = dp(1)
            setBackgroundColor(COLOR_BG)
            isFastScrollEnabled = true
            isVerticalScrollBarEnabled = true
            isSmoothScrollbarEnabled = true
            setOnItemClickListener { _, _, position, _ -> playSelection(position) }
            setOnItemLongClickListener { _, _, position, _ ->
                onTrackLongPress(position)
                true
            }
        }
        // This weighted ListView owns the screen's scrolling; the mini-player never scrolls away.
        root.addView(trackList, LinearLayout.LayoutParams(-1, 0, 1f))

        statusView = label("Connecting…", 11f, COLOR_MUTED)
        statusView.maxLines = 2
        root.addView(statusView, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        lyricView = label("♫ Lyrics: More → Lyrics → Import .lrc", 12f, COLOR_ACCENT)
        lyricView.isSingleLine = true
        lyricView.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        lyricView.isSelected = true
        lyricView.setOnClickListener { showLyricDialog() }
        root.addView(lyricView, LinearLayout.LayoutParams(-1, dp(28)))
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(6))
            background = panelBackground()
        }
        root.addView(dock)
        val waveformRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = android.view.View.GONE
        }
        waveformDock = waveformRow
        val titleInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleView = label("Choose a track", 15f, Color.WHITE, true).apply {
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        artistView = label("Swipe ←/→ track · drag timeline to seek", 11f, COLOR_MUTED).apply {
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titleInfo.addView(titleView)
        titleInfo.addView(artistView)
        titleInfo.contentDescription = "Mini player: swipe left for next track, swipe right for previous"
        val gesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true
            override fun onSingleTapUp(event: MotionEvent): Boolean {
                showPlaybackMenu()
                return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val action = MiniPlayerGestures.classify(
                    deltaX = e2.x - e1.x,
                    deltaY = e2.y - e1.y,
                    velocityX = velocityX,
                    minDistancePx = dp(55).toFloat()
                )
                val c = controller ?: return false
                when (action) {
                    MiniPlayerGestures.Action.NEXT_TRACK -> {
                        if (c.hasNextMediaItem()) { c.seekToNextMediaItem(); c.play() }
                        else status("Last song in queue")
                    }
                    MiniPlayerGestures.Action.PREVIOUS_TRACK -> {
                        if (c.hasPreviousMediaItem()) { c.seekToPreviousMediaItem(); c.play() }
                        else status("First song in queue")
                    }
                    MiniPlayerGestures.Action.IGNORE -> return false
                }
                return true
            }
        })
        titleInfo.setOnTouchListener { _, event -> gesture.onTouchEvent(event) }
        titleInfo.isClickable = true

        val mini = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        mini.addView(titleInfo, LinearLayout.LayoutParams(0, -2, 1f))
        mini.addView(button("◀◀") { controller?.let { c ->
            if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem()
        } }, LinearLayout.LayoutParams(dp(48), dp(44)))
        playButton = button("▶") {
            controller?.let { if (it.isPlaying) it.pause() else { it.prepare(); it.play() } }
        }
        mini.addView(playButton, LinearLayout.LayoutParams(dp(48), dp(44)))
        mini.addView(button("▶▶") { controller?.let { c ->
            if (c.hasNextMediaItem()) c.seekToNextMediaItem()
        } }, LinearLayout.LayoutParams(dp(48), dp(44)))
        dock.addView(mini)
        networkView = label("Network: waiting", 10f, COLOR_MUTED)
        dock.addView(networkView)
        seekBar = SeekBar(this).apply {
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) { seeking = true }
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) progressView.text = "${formatTime(progress.toLong())} / ${formatTime(controller?.duration ?: C.TIME_UNSET)}"
                }
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    controller?.seekTo(seekBar.progress.toLong())
                    seeking = false
                }
            })
        }
        dock.addView(waveformRow, LinearLayout.LayoutParams(-1, dp(46)))
        dock.addView(seekBar, LinearLayout.LayoutParams(-1, dp(30)))
        progressView = label("00:00 / --:--", 11f, COLOR_MUTED).apply {
            gravity = android.view.Gravity.END
        }
        dock.addView(progressView)
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val actions = listOf(
            "Library" to { showPlaylistDialog() },
            "Radio" to { showMalaysiaRadioQuickMenu() },
            "Tuner" to { showRadioTuner() },
            "Video" to { showVideoServices() },
            "FX" to { showAudioMenu() },
            "Lyrics" to { showPicker("Lyrics & subtitles", arrayOf("Import LRC", "View lyrics", "Subtitle Studio")) {
                when (it) {
                    0 -> lyricsForCurrent()
                    1 -> showLyricDialog()
                    2 -> startActivity(Intent(this, SubtitleStudioActivity::class.java))
                }
            } }
        )
        actions.forEachIndexed { index, item ->
            bottomNav.addView(button(item.first, item.second),
                LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    if (index > 0) leftMargin = dp(3)
                })
        }
        root.addView(bottomNav, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(5) })

    }

    private fun seekRelative(delta: Long) {
        controller?.let {
            val desired = (it.currentPosition + delta).coerceAtLeast(0L)
            val duration = it.duration
            it.seekTo(if (duration > 0 && duration != C.TIME_UNSET) desired.coerceAtMost(duration) else desired)
        }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun panelBackground() = GradientDrawable().apply {
        setColor(COLOR_PANEL)
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), 0xFF2B3947L.toInt())
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        val iconKind = when {
            text == "◀◀" -> "previous"
            text == "▶▶" -> "next"
            text == "▶" || text == "Ⅱ" -> "play"
            text == "✕" -> "close"
            text.contains("Waveform", true) -> "wave"
            text.contains("Scan", true) -> "scan"
            text.contains("Radio", true) || text.contains("Tuner", true) -> "radio"
            text.contains("Video", true) -> "play"
            text.contains("FX", true) || text.contains("Sound", true) -> "fx"
            text.contains("More", true) || text.contains("Menu", true) || text.contains("Setting", true) -> "settings"
            text.contains("DEVICE", true) || text.contains("FOLDERS", true) -> "device"
            text.contains("Lyrics", true) -> "wave"
            else -> "library"
        }
        this.text = if (text in listOf("◀◀", "▶▶", "▶", "Ⅱ", "✕")) "" else text.trimStart { !it.isLetterOrDigit() }.trim()
        if (iconKind != null) {
            setCompoundDrawablesWithIntrinsicBounds(ActionIconDrawable(iconKind, dp(18)), null, null, null)
            compoundDrawablePadding = dp(5)
        }
        setAllCaps(false)
        textSize = 11f
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        setTextColor(Color.WHITE)
        val accent = when {
            text.contains("Radio", true) || text.contains("Scan", true) -> 0xFF087F83L.toInt()
            text.contains("FX", true) || text.contains("Sound", true) -> 0xFF6742A6L.toInt()
            text.contains("Play", true) || text.contains("Pause", true) -> 0xFF13765CL.toInt()
            text.contains("Web", true) || text.contains("YouTube", true) -> 0xFF345CA0L.toInt()
            else -> 0xFF27384BL.toInt()
        }
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(accent, 0xFF1A2638L.toInt())).apply {
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), 0xFF496177L.toInt())
        }
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(8), 0, dp(8), 0)
        elevation = dp(2).toFloat()
        contentDescription = when (text) { "◀◀" -> "Previous track"; "▶▶" -> "Next track"; "▶" -> "Play"; "✕" -> "Close waveform"; else -> text }
        setOnClickListener { onClick() }
    }

    private fun buttonRow(vararg buttons: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEachIndexed { index, button ->
            addView(button, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                if (index != 0) leftMargin = dp(4)
                topMargin = dp(6)
            })
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}