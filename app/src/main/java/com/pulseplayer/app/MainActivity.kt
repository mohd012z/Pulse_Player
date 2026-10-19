package com.pulseplayer.app

import com.pulseplayer.ui.dialogs.Phase1InspectorDialog
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
        private const val COLOR_BG = 0xFF10141FL.toInt()
        private const val COLOR_PANEL = 0xFF1B2331L.toInt()
        private const val COLOR_ACCENT = 0xFF75E2C0L.toInt()
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
    private val powerSaving by lazy { PowerSavingManager(this) }
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
            clock.postDelayed(this, powerSaving.refreshIntervalMillis(controller?.isPlaying == true))
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
                showSpectrum()
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
        visibleSongs.addAll(if (query.isEmpty()) songs else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
        })
        val lines: List<String> = when (source) {
            "FOLDERS" -> folderTracks.filter { it.name.contains(query, true) || it.folder.contains(query, true) }
                .map { "${it.name}\n${it.folder}" }
            "RADIO", "MY RADIO" -> filteredStations(query)
                .map { "${it.name}\nLIVE STREAM" }
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
        val options = SoundPresets.names.toTypedArray()
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
        if (!powerSaving.allowSpectrum()) {
            status("Spectrum disabled in Ultra Saving. Change Power Saving mode to enable it.")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle("Optional live audio visualizer")
                .setMessage("Android requires audio-capture permission for FFT visualization. PulsePlayer uses this only to display the current song spectrum, does not record sound to disk, and stops capture when closed.")
                .setPositiveButton("Allow") { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_SPECTRUM_PERMISSION)
                }
                .setNegativeButton("Cancel", null).show()
        } else showSpectrum()
    }

    private fun showSpectrum() {
        if (!powerSaving.allowSpectrum()) {
            status("Spectrum disabled in Ultra Saving")
            return
        }
        customCommand(PlaybackService.CMD_STATUS) { extras, success ->
            if (!success || isFinishing || isDestroyed) return@customCommand
            val sessionId = extras.getInt(PlaybackService.ARG_AUDIO_SESSION_ID, -1)
            if (sessionId <= 0) {
                status("Start playback before opening the audio spectrum")
                return@customCommand
            }
            spectrumView?.stop()
            val view = SpectrumView(this).apply {
                setBackgroundColor(COLOR_BG)
                minimumHeight = dp(240)
            }
            if (!view.start(sessionId)) {
                status("Audio spectrum unsupported on this playback route")
                return@customCommand
            }
            spectrumView = view
            AlertDialog.Builder(this)
                .setTitle("Live music spectrum · FFT")
                .setView(view)
                .setPositiveButton("Close", null)
                .create().also { dialog ->
                    dialog.setOnDismissListener {
                        view.stop()
                        if (spectrumView === view) spectrumView = null
                    }
                    dialog.show()
                }
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

    private fun refreshNowPlaying() {
        val c = controller
        val media = c?.currentMediaItem
        titleView.text = media?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: "Choose a track"
        artistView.text = media?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: "Local audio • ExoPlayer"
        playButton.text = if (c?.isPlaying == true) "Ⅱ" else "▶"
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

    private fun showYouTubeEmbedDialog() {
        val input = EditText(this).apply {
            hint = "Paste YouTube video or playlist URL"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("YouTube · embedded player")
            .setView(input)
            .setPositiveButton("Play") { _, _ ->
                val url = input.text.toString().trim()
                if (YouTubeLinkParser.embedUrl(url) == null) {
                    status("Invalid YouTube video or playlist URL")
                } else {
                    startActivity(Intent(this, YouTubePlayerActivity::class.java)
                        .putExtra(YouTubePlayerActivity.EXTRA_URL, url))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openProvider(destination: String) {
        val url = when (destination) {
            "YouTube" -> "https://www.youtube.com/"
            "YouTube Music" -> "https://music.youtube.com/"
            "JOOX" -> "https://www.joox.com/"
            "Spotify" -> "https://open.spotify.com/"
            "RTM Radio Malaysia" -> "https://radio.rtm.gov.my/"
            "BFM 89.9" -> "https://www.bfm.my/"
            "ERA Malaysia" -> "https://era.je/"
            "MY FM" -> "https://my.com.my/"
            else -> return
        }
        if (destination in listOf("YouTube", "YouTube Music", "JOOX", "Spotify")) {
            val mode = powerSaving.effectiveMode()
            if (mode != PowerSavingManager.Mode.OFF) {
                status("$destination opens in its own app/browser; Pulse Player cannot control its video quality or battery usage. Power mode: ${mode.name}.")
            }
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            })
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

    private fun showMoreMenu() = showPicker("Pulse Player · More", arrayOf(
        "Import / file manager", "Online music services", "Malaysian radio", "Audio mixer / effects",
        "Lyrics / subtitles", "Live audio spectrum", "Playback preferences", "Bluetooth / Wi-Fi / output", "Share current song", "Power saving mode", "Inspect media URL"
    )) { index ->
        when (index) {
            0 -> showPicker("Import music", arrayOf("Open audio file", "Import folder", "Remove saved folder permissions")) {
                when (it) { 0 -> openFilePicker(); 1 -> openFolderPicker(); 2 -> clearFolders() }
            }
            1 -> showPicker("Online music · official services", arrayOf("YouTube · embedded link", "YouTube · official", "YouTube Music · official", "JOOX", "Spotify")) {
                when (it) {
                    0 -> showYouTubeEmbedDialog()
                    1 -> openProvider("YouTube")
                    2 -> openProvider("YouTube Music")
                    3 -> openProvider("JOOX")
                    4 -> openProvider("Spotify")
                }
            }
            2 -> showPicker("Malaysia radio", arrayOf(
                "Play Malaysia radio list", "Auto-discover MY stations & save", "All saved radio", "Add HTTPS stream",
                "RTM official radio (stations)", "BFM 89.9 official player",
                "ERA official player", "MY FM official player"
            )) {
                when (it) {
                    0 -> switchPlaylist("MY RADIO")
                    1 -> discoverMalaysiaRadio()
                    2 -> switchPlaylist("RADIO")
                    3 -> addRadioDialog()
                    4 -> openProvider("RTM Radio Malaysia")
                    5 -> openProvider("BFM 89.9")
                    6 -> openProvider("ERA Malaysia")
                    7 -> openProvider("MY FM")
                }
            }
            3 -> showAudioMenu()
            4 -> showPicker("Lyrics / subtitles", arrayOf("Import local .lrc for playing track", "View lyrics")) {
                when (it) { 0 -> lyricsForCurrent(); 1 -> showLyricDialog() }
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
            9 -> showPowerSavingDialog()
            10 -> Phase1InspectorDialog.show(this)
        }
    }

    private fun showPowerSavingDialog() {
        val modes = PowerSavingManager.Mode.values()
        val labels = arrayOf(
            "Auto Smart · adapt to battery level",
            "Eco · slower interface refresh",
            "Ultra Saving · minimal optional UI activity",
            "Off · normal performance"
        )
        AlertDialog.Builder(this)
            .setTitle("Smart Power Saving")
            .setSingleChoiceItems(labels, modes.indexOf(powerSaving.mode)) { dialog, selected ->
                powerSaving.mode = modes[selected]
                if (!powerSaving.allowSpectrum()) spectrumView?.stop()
                status("Power mode: ${powerSaving.mode.name} · effective ${powerSaving.effectiveMode().name}")
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAudioMenu() = showPicker("Sound · local and radio only", arrayOf(
        "Choose EQ preset", "Custom 3-band equalizer", "Bass strength", "Noise Softening (EQ only)", "Android system sound settings"
    )) {
        when (it) {
            0 -> selectMixPreset()
            1 -> showCustomEqualizer()
            2 -> showBassDialog()
            3 -> customCommand(PlaybackService.CMD_SET_PRESET, Bundle().apply {
                putString(PlaybackService.ARG_PRESET, "Noise Softening")
            }) { _, ok -> status(if (ok) "Noise Softening EQ applied; this is not AI restoration" else "EQ unavailable") }
            4 -> openSystemSettings(Settings.ACTION_SOUND_SETTINGS)
        }
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
        val brand = label("♫  PULSE PLAYER", 18f, COLOR_ACCENT, true)
        toolbar.addView(brand, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
        })
        toolbar.addView(button("⋮ More") { showMoreMenu() }, LinearLayout.LayoutParams(dp(92), dp(44)))
        root.addView(toolbar)

        // Adaptive two-row navigation: prevent the old duplicate radio shortcut and
        // preserve legible touch targets on narrow portrait displays.
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val primaryTabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sourceButton = button("DEVICE ▾") { showPlaylistDialog() }
        primaryTabs.addView(sourceButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        primaryTabs.addView(button("♫ FX") { selectMixPreset() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        tabs.addView(primaryTabs)
        val categoryTabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        categoryTabs.addView(button("♫ Music") { switchPlaylist("DEVICE") },
            LinearLayout.LayoutParams(0, dp(48), 1f))
        categoryTabs.addView(button("📻 Radio") { switchPlaylist("RADIO") },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(5) })
        categoryTabs.addView(button("★ My Radio") { switchPlaylist("MY RADIO") },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(5) })
        tabs.addView(categoryTabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        root.addView(tabs)

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
        dock.addView(seekBar, LinearLayout.LayoutParams(-1, dp(30)))
        progressView = label("00:00 / --:--", 11f, COLOR_MUTED).apply {
            gravity = android.view.Gravity.END
        }
        dock.addView(progressView)
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
        this.text = text
        setAllCaps(false)
        textSize = 13f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(0xFF2A3849L.toInt())
            cornerRadius = dp(9).toFloat()
        }
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(2), 0, dp(2), 0)
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