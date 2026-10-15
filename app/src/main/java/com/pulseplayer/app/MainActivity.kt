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
import android.widget.SeekBar
import android.widget.TextView
import android.view.inputmethod.EditorInfo
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
        private const val REQUEST_OPEN_LRC = 104
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
    private val pickedRepo by lazy { ImportedAudioRepository(this) }
    private val worldRepo by lazy { JamendoCatalogRepository() }
    private val worldPrefs by lazy { getSharedPreferences("world_music_catalog", MODE_PRIVATE) }
    private val worldTracks = ArrayList<JamendoCatalogRepository.Track>()
    private var worldQuery = ""
    private var worldOffset = 0
    private var worldSearchGeneration = 0
    private var selectedWorldTrackId: String? = null
    private lateinit var worldDownloadButton: Button
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
    private var importedLyrics: List<LrcLyrics.Line> = emptyList()

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
        clock.removeCallbacks(ticker)
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    override fun onDestroy() {
        scanGeneration++
        folderScanGeneration++
        worldSearchGeneration++
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
            "PICKED" -> pickedRepo.all().filter { it.name.contains(query, true) }
                .map { "${it.name}\nSelected local file" }
            "ALL LOCAL" -> allLocalItems().filter { it.second.contains(query, true) }
                .map { "${it.second}\nOn your device" }
            "WORLD" -> worldTracks.map { "${it.title}\n${it.artist} · ${if (it.downloadAllowed) "↓ MP3 available" else "stream only"}" }
            else -> visibleSongs.map { "${it.title}\n${it.artist}" }
        }
        currentDisplaySize = lines.size
        trackAdapter.clear()
        trackAdapter.addAll(lines)
        trackAdapter.notifyDataSetChanged()
        libraryView.text = "${source}  ·  $currentDisplaySize tracks / stations"
        if (::sourceView.isInitialized) sourceView.text = "${source.replace('_', ' ')} · ${currentDisplaySize} items"
        if (::sourceButton.isInitialized) sourceButton.text = "${source} ▾"
        if (::worldDownloadButton.isInitialized) worldDownloadButton.visibility =
            if (source == "WORLD") View.VISIBLE else View.GONE
    }

    private fun allLocalItems(): List<Pair<MediaItem, String>> = (
        songs.map { it.toMediaItem() to it.title } +
            folderTracks.map { it.toMediaItem() to it.name } +
            pickedRepo.all().map { it.toMediaItem() to it.name }
        ).distinctBy { (item, _) -> item.mediaId }
            .sortedBy { (_, title) -> title.lowercase(Locale.ROOT) }

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
            "PICKED" -> {
                val picked = pickedRepo.all().filter { it.name.contains(query, true) }
                picked.map { it.toMediaItem() } to picked.getOrNull(index)?.name
            }
            "ALL LOCAL" -> {
                val local = allLocalItems().filter { it.second.contains(query, true) }
                local.map { it.first } to local.getOrNull(index)?.second
            }
            "WORLD" -> worldTracks.map { it.toMediaItem() } to worldTracks.getOrNull(index)?.title
            else -> visibleSongs.map { it.toMediaItem() } to visibleSongs.getOrNull(index)?.title
        }
        if (index !in queue.indices) return
        if (source == "WORLD") selectedWorldTrackId = worldTracks.getOrNull(index)?.id
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
        val choices = listOf("ALL LOCAL", "DEVICE", "FOLDERS", "PICKED", "WORLD", "MY RADIO", "RADIO")
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
                    .setItems(arrayOf("Play station", "Remove saved station")) { _, selected ->
                        if (selected == 0) playSelection(index)
                        else if (radioRepo.remove(station.url)) {
                            filterLibrary()
                            status("Removed saved radio station")
                        } else status("Built-in sample stations cannot be removed")
                    }.show()
            }
            "FOLDERS" -> {
                val track = folderTracks.filter { it.name.contains(query, true) || it.folder.contains(query, true) }.getOrNull(index)
                track?.let { shareLocalUri(it.uri) }
            }
            "PICKED" -> pickedRepo.all().filter { it.name.contains(query, true) }
                .getOrNull(index)?.let { shareLocalUri(it.uri) }
            "WORLD" -> {
                val track = worldTracks.getOrNull(index) ?: return
                AlertDialog.Builder(this)
                    .setTitle(track.title)
                    .setMessage("${track.artist}\n\n${if (track.downloadAllowed) "Artist permits MP3 download." else "Stream only — artist does not permit downloads."}\nLicense: ${track.licenseUrl}")
                    .setPositiveButton("Play") { _, _ -> playSelection(index) }
                    .setNeutralButton(if (track.downloadAllowed) "↓ Download MP3" else "No download") { _, _ ->
                        if (track.downloadAllowed) showMp3QualityPicker(track)
                        else status("Artist has disabled downloads for this song")
                    }
                    .setNegativeButton("Close", null).show()
            }
            "ALL LOCAL" -> allLocalItems().filter { it.second.contains(query, true) }
                .getOrNull(index)?.first?.localConfiguration?.uri?.let { shareLocalUri(it) }
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
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
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
        if (requestCode == REQUEST_OPEN_LRC && resultCode == RESULT_OK) {
            val lyricUri = data?.data ?: return
            val parsed = runCatching {
                contentResolver.openInputStream(lyricUri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    LrcLyrics.parse(reader.readText())
                } ?: emptyList()
            }.getOrElse { status("Could not open LRC file: ${it.message}"); return }
            importedLyrics = parsed
            status("Loaded ${parsed.size} synchronized lyric lines")
        }
        if (requestCode == REQUEST_OPEN_AUDIO && resultCode == RESULT_OK) {
            val chosen = LinkedHashSet<Uri>()
            data?.clipData?.let { clips ->
                for (index in 0 until clips.itemCount.coerceAtMost(500)) {
                    chosen.add(clips.getItemAt(index).uri)
                }
            }
            data?.data?.let { chosen.add(it) }
            if (chosen.isEmpty()) return
            val persisted = chosen.filter { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    true
                }.getOrDefault(false)
            }
            if (persisted.isNotEmpty()) {
                val total = runCatching { pickedRepo.add(persisted) }.getOrElse { error ->
                    status("Unable to index selected audio: ${error.message}")
                    0
                }
                status("Saved $total selected files. You may reopen them in Picked Files.")
            }
            val selected = chosen.first()
            if (persisted.isEmpty()) {
                playDocument(selected)
                status("Playing one selected file. Its provider did not allow persistent access.")
            } else if (chosen.size == 1) playDocument(selected)
            else {
                switchPlaylist("PICKED")
                status("Selected ${chosen.size} audio files. Tap a track to play")
            }
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
            status("Audio effects: EQ ${if (eq) "available" else "unsupported"}, Bass ${if (bass) "available" else "unsupported"} • $preset")
        }
    }

    private fun filteredStations(query: String): List<RadioRepository.Station> =
        radioRepo.stations().filter { station ->
            (source != "MY RADIO" || station.name.startsWith("MY ·")) &&
                station.name.contains(query, ignoreCase = true)
        }

    private fun openInAppProvider(provider: ProviderMediaUrls.Provider) {
        // Do not overlap our ExoPlayer audio with an independent authorized provider.
        controller?.pause()
        runCatching {
            startActivity(Intent(this, InAppMusicHubActivity::class.java).apply {
                putExtra(InAppMusicHubActivity.EXTRA_PROVIDER, provider.name)
            })
        }.onFailure { status("In-app provider unavailable: ${it.message}") }
    }

    private fun openProvider(destination: String) {
        val url = when (destination) {
            "YouTube" -> "https://www.youtube.com/"
            "YouTube Music" -> "https://music.youtube.com/"
            "JOOX" -> "https://www.joox.com/"
            "Spotify" -> "https://open.spotify.com/search/" + Uri.encode(filterView.text?.toString()?.trim().orEmpty())
            "RTM Radio Malaysia" -> "https://radio.rtm.gov.my/"
            "BFM 89.9" -> "https://www.bfm.my/"
            "ERA Malaysia" -> "https://era.je/"
            "MY FM" -> "https://my.com.my/"
            else -> return
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
        arrayOf("All local audio", "Device MP3 library", "Imported folders", "Picked audio files", "World independent music", "Malaysia radio", "All radio stations", "Next playlist and play")
    ) { index ->
        when (index) {
            0 -> switchPlaylist("ALL LOCAL")
            1 -> switchPlaylist("DEVICE")
            2 -> switchPlaylist("FOLDERS")
            3 -> switchPlaylist("PICKED")
            4 -> showWorldMenu()
            5 -> switchPlaylist("MY RADIO")
            6 -> switchPlaylist("RADIO")
            7 -> rotatePlaylist()
        }
    }

    private fun showMoreMenu() = showPicker("Pulse Player · More", arrayOf(
        "Import / file manager", "Online music services", "Malaysian radio", "Audio presets / bass",
        "Playback preferences", "Bluetooth / Wi-Fi / output", "Share current song", "Synchronized lyrics (.lrc)",
        "World music catalog (Jamendo)", "In-app YouTube / Spotify / JOOX"
    )) { index ->
        when (index) {
            0 -> showPicker("Import music", arrayOf("Pick one or multiple audio files", "Import folder recursively", "View selected files", "Remove imported folders", "Clear picked file list", "Rescan device music")) {
                when (it) {
                    0 -> openFilePicker()
                    1 -> openFolderPicker()
                    2 -> switchPlaylist("PICKED")
                    3 -> clearFolders()
                    4 -> clearPickedFiles()
                    5 -> scanLibrary()
                }
            }
            1 -> showPicker("Online music · authorized sources", arrayOf("World music (Jamendo)", "Spotify in-app embed", "JOOX in-app website", "YouTube Music in-app", "YouTube in-app")) {
                when (it) {
                    0 -> showWorldMenu()
                    1 -> openInAppProvider(ProviderMediaUrls.Provider.SPOTIFY)
                    2 -> openInAppProvider(ProviderMediaUrls.Provider.JOOX)
                    3 -> openInAppProvider(ProviderMediaUrls.Provider.YOUTUBE_MUSIC)
                    4 -> openInAppProvider(ProviderMediaUrls.Provider.YOUTUBE)
                }
            }
            2 -> showPicker("Malaysia radio", arrayOf(
                "Play Malaysia radio list", "All saved radio", "Add HTTPS stream",
                "RTM official radio (stations)", "BFM 89.9 official player",
                "ERA official player", "MY FM official player"
            )) {
                when (it) {
                    0 -> switchPlaylist("MY RADIO")
                    1 -> switchPlaylist("RADIO")
                    2 -> addRadioDialog()
                    3 -> openProvider("RTM Radio Malaysia")
                    4 -> openProvider("BFM 89.9")
                    5 -> openProvider("ERA Malaysia")
                    6 -> openProvider("MY FM")
                }
            }
            3 -> showAudioMenu()
            4 -> showPlaybackMenu()
            5 -> showPicker("Connection options", arrayOf("Android Bluetooth settings", "Change audio output", "Android Wi-Fi settings")) {
                when (it) {
                    0 -> openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                    1 -> chooseAudioOutput()
                    2 -> openSystemSettings(Settings.ACTION_WIFI_SETTINGS)
                }
            }
            6 -> shareCurrentTrack()
            7 -> showLyricsDialog()
            8 -> showWorldMenu()
            9 -> showPicker("Choose in-app provider", arrayOf("YouTube", "YouTube Music", "Spotify Embed", "JOOX Website")) { which ->
                val provider = listOf(
                    ProviderMediaUrls.Provider.YOUTUBE,
                    ProviderMediaUrls.Provider.YOUTUBE_MUSIC,
                    ProviderMediaUrls.Provider.SPOTIFY,
                    ProviderMediaUrls.Provider.JOOX
                )[which]
                openInAppProvider(provider)
            }
        }
    }

    private fun clearPickedFiles() {
        AlertDialog.Builder(this)
            .setTitle("Remove picked audio list?")
            .setMessage("This removes the saved list and releases its file permissions. Original music is not deleted.")
            .setPositiveButton("Remove") { _, _ ->
                pickedRepo.all().forEach { track ->
                    runCatching { contentResolver.releasePersistableUriPermission(track.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                pickedRepo.removeAll()
                filterLibrary()
                status("Picked audio list cleared")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showWorldMenu() = showPicker("World music · independent artists", arrayOf(
        "Browse world music", "Search current text", "Load next 50 tracks", "Set Jamendo client ID", "Spotify search in official app", "JOOX official app"
    )) { option ->
        when (option) {
            0 -> {
                switchPlaylist("WORLD")
                if (worldTracks.isEmpty()) searchWorld(false, browse = true)
            }
            1 -> searchWorld(false)
            2 -> searchWorld(true)
            3 -> setJamendoClientId()
            4 -> openProvider("Spotify")
            5 -> openProvider("JOOX")
        }
    }

    private fun setJamendoClientId() {
        val field = EditText(this).apply {
            hint = "Your Jamendo developer client ID"
            setSingleLine(true)
            setText(worldPrefs.getString("jamendo_client_id", ""))
        }
        AlertDialog.Builder(this)
            .setTitle("Jamendo catalog access")
            .setMessage("Register your own Jamendo API application at developer.jamendo.com. The app will search independently published music, not Spotify/JOOX's licensed catalogs.")
            .setView(field)
            .setPositiveButton("Save") { _, _ ->
                val id = field.text.toString().trim()
                if (!WorldMusicInputs.validClientId(id)) {
                    status("Invalid Jamendo API client ID")
                } else {
                    worldPrefs.edit().putString("jamendo_client_id", id).apply()
                    switchPlaylist("WORLD")
                    status("Jamendo client ID saved. Loading independent world music…")
                    searchWorld(false, browse = true)
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun searchWorld(loadMore: Boolean, browse: Boolean = false) {
        val key = worldPrefs.getString("jamendo_client_id", "").orEmpty()
        if (!WorldMusicInputs.validClientId(key)) {
            status("World catalog needs a Jamendo client ID: More → World music → Set ID")
            return
        }
        val query = if (loadMore) worldQuery else if (browse) "" else filterView.text?.toString()?.trim().orEmpty()
        if (query.isNotBlank() && !WorldMusicInputs.validSearch(query)) {
            status("Enter at least two characters for world music search")
            return
        }
        if (!loadMore && !browse && query.isBlank()) {
            status("Enter at least two characters or choose Browse world music")
            return
        }
        if (loadMore && worldTracks.isEmpty()) {
            status("Search the catalog first")
            return
        }
        val offset = if (loadMore) worldOffset else 0
        val generation = ++worldSearchGeneration
        source = "WORLD"
        status(if (query.isBlank()) "Discovering independent world music…" else "Searching independent world music…")
        thread(name = "JamendoCatalogSearch", isDaemon = true) {
            val result = runCatching { worldRepo.search(key, query, offset = offset) }
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != worldSearchGeneration) return@runOnUiThread
                result.onSuccess { tracks ->
                    if (!loadMore) {
                        worldTracks.clear()
                        selectedWorldTrackId = null
                    }
                    val known = worldTracks.mapTo(hashSetOf()) { it.id }
                    tracks.filter { known.add(it.id) }.forEach(worldTracks::add)
                    worldQuery = query
                    worldOffset = offset + tracks.size
                    filterLibrary()
                    status("World music: ${worldTracks.size} tracks · Search next page using More menu")
                }.onFailure { error ->
                    status("Catalog search failed: ${error.message}. Check your API ID / network.")
                }
            }
        }
    }

    private fun showSelectedMp3Download() {
        if (source != "WORLD") { status("Open World music and choose a song to download"); return }
        val selected = worldTracks.firstOrNull { it.id == selectedWorldTrackId }
        if (selected == null) {
            status("Tap a World music track first, then press ↓ MP3")
            return
        }
        showMp3QualityPicker(selected)
    }

    private fun showMp3QualityPicker(track: JamendoCatalogRepository.Track) {
        if (!track.downloadAllowed) {
            status("Artist has not authorized downloads for this track")
            return
        }
        val choices = Mp3DownloadInputs.Quality.entries.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("↓ Download MP3 · ${track.title}")
            .setMessage("Choose provider quality. High means variable bitrate, not guaranteed 320 kbps. Download only where permitted by the artist's license. Files appear in Downloads/PulsePlayer on Android 10+.")
            .setItems(choices.map { it.label }.toTypedArray()) { _, position ->
                val quality = choices[position]
                val key = worldPrefs.getString("jamendo_client_id", "").orEmpty()
                if (!WorldMusicInputs.validClientId(key)) {
                    status("Set a valid Jamendo client ID before downloading")
                } else {
                    status("Checking artist download permission…")
                    thread(name = "AuthorizedMp3Download", isDaemon = true) {
                        val resolved = runCatching { worldRepo.resolveMp3Download(key, track.id, quality) }
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            resolved.onSuccess { url ->
                                runCatching { AuthorizedMp3Downloader(this).queue(track, url, quality) }
                                    .onSuccess { id ->
                                        status("MP3 queued (#$id). Check Android Downloads for completion.")
                                    }.onFailure { error -> status("Cannot queue MP3: ${error.message}") }
                            }.onFailure { error ->
                                status("MP3 download unavailable: ${error.message}")
                            }
                        }
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun importLyrics() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/octet-stream"))
        }
        runCatching { startActivityForResult(picker, REQUEST_OPEN_LRC) }
            .onFailure { status("No compatible lyrics file picker") }
    }

    private fun showLyricsDialog() {
        val c = controller
        val current = LrcLyrics.at(importedLyrics, c?.currentPosition ?: 0L)
        AlertDialog.Builder(this)
            .setTitle("Local LRC lyrics")
            .setMessage(current ?: "No lyric line at this timestamp. Import a synchronized .lrc text file first.")
            .setPositiveButton("Import LRC") { _, _ -> importLyrics() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAudioMenu() = showPicker("Sound · local and radio only", arrayOf(
        "Choose EQ preset", "Bass strength", "Android system sound settings"
    )) {
        when (it) {
            0 -> selectMixPreset()
            1 -> showBassDialog()
            2 -> openSystemSettings(Settings.ACTION_SOUND_SETTINGS)
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
            0 -> c?.let { player ->
                player.shuffleModeEnabled = !player.shuffleModeEnabled
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

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sourceButton = button("DEVICE ▾") { showPlaylistDialog() }
        tabs.addView(sourceButton, LinearLayout.LayoutParams(0, dp(44), 1.6f))
        tabs.addView(button("World ♫") { showWorldMenu() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(6) })
        tabs.addView(button("♫ FX") { selectMixPreset() },
            LinearLayout.LayoutParams(0, dp(44), 0.7f).apply { leftMargin = dp(6) })
        root.addView(tabs)

        sourceView = label("DEVICE · 0 items", 12f, COLOR_ACCENT, true)
        root.addView(sourceView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        filterView = EditText(this).apply {
            hint = "Search device songs / artist / independent world music"
            setHintTextColor(0xFF8296A7L.toInt())
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH && source == "WORLD") {
                    searchWorld(false)
                    true
                } else false
            }
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
        val libraryHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        libraryHeader.addView(libraryView, LinearLayout.LayoutParams(0, -2, 1f))
        worldDownloadButton = button("↓ MP3") { showSelectedMp3Download() }.apply {
            contentDescription = "Download selected authorized world music as MP3; choose quality"
            visibility = View.GONE
        }
        libraryHeader.addView(worldDownloadButton, LinearLayout.LayoutParams(dp(84), dp(38)))
        root.addView(libraryHeader, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
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
        textSize = 11f
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