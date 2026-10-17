package com.pulseplayer.app.aipulse

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.net.Uri
import android.content.Intent
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.concurrent.thread

/** Standalone offline studio: creates original PCM audio, never accesses third-party streams. */
class AiPulseStudioActivity : Activity() {
    @Volatile private var cancelRequested = false
    @Volatile private var working = false
    private var recorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private var recording = false
    private var importedVoiceWav: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF10141F.toInt())
        }
        val scroller = ScrollView(this)
        scroller.addView(root)
        setContentView(scroller)
        fun heading(text: String): TextView = TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 14, 0, 12)
            root.addView(this)
        }
        fun field(label: String, initial: String): EditText {
            heading(label)
            return EditText(this).apply {
                setText(initial)
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.LTGRAY)
                root.addView(this)
            }
        }
        heading("AI PULSE · Offline Composer")
        heading("Original synthesized instrumentals — not neural text-to-music")
        heading("Visual preview · animated demo (not sound-reactive)")
        root.addView(Button(this).apply {
            text = "Open LIVE spectrum · FFT / Pixel Water"
            contentDescription = "Open main music player to view the sound-reactive FFT or pixel-water waveform"
            setOnClickListener {
                startActivity(Intent(this@AiPulseStudioActivity, com.pulseplayer.app.MainActivity::class.java))
                Toast.makeText(this@AiPulseStudioActivity,
                    "Play a track, then select Live Audio Spectrum. Tap the live display to switch FFT and Pixel Water.",
                    Toast.LENGTH_LONG).show()
            }
        })
        val visuals = AiPulseVisualView(this)
        root.addView(visuals, LinearLayout.LayoutParams(-1, (220 * resources.displayMetrics.density).toInt()))
        val presets = arrayOf("Spectrum Bars", "Neon Wave", "Orbit", "Minimal")
        val presetButton = Button(this).apply { text = "Visual: Spectrum Bars" }
        root.addView(presetButton)
        presetButton.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Choose visual preset")
                .setSingleChoiceItems(presets, visuals.preset.ordinal) { dialog, which ->
                    visuals.preset = AiPulseVisualView.Preset.entries[which]
                    presetButton.text = "Visual: ${presets[which]}"
                    dialog.dismiss()
                }.setNegativeButton("Cancel", null).show()
        }
        val motionButton = Button(this).apply { text = "Motion: On (tap to reduce)" }
        root.addView(motionButton)
        motionButton.setOnClickListener {
            visuals.motionEnabled = !visuals.motionEnabled
            motionButton.text = if (visuals.motionEnabled) "Motion: On (tap to reduce)" else "Motion: Off"
        }
        val title = field("Track title", "My Pulse Song")
        val mood = field("Mood (bright / sad / dark)", "bright")
        heading("Tempo")
        val bpmValue = TextView(this).apply { text = "110 BPM"; setTextColor(Color.WHITE) }
        root.addView(bpmValue)
        val bpm = SeekBar(this).apply {
            max = 120
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    bpmValue.text = "${value + 60} BPM"
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        root.addView(bpm)
        heading("Song Creator · offline assistant")
        val idea = field("Song idea / story", "Keeping promises and appreciating others")
        val genre = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@AiPulseStudioActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Pop Rock", "Ballad", "R&B", "Acoustic", "EDM"))
        }
        root.addView(genre)
        val language = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@AiPulseStudioActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Bahasa Melayu", "English", "BM + English"))
        }
        root.addView(language)
        val instruments = field("Instruments (comma separated)", "Electric guitar, Bass, Drums, Piano")
        heading("Editable lyrics / song sections")
        val lyrics = EditText(this).apply {
            minLines = 7
            gravity = Gravity.TOP
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            hint = "Original lyrics"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        root.addView(lyrics)
        root.addView(Button(this).apply {
            text = "Assistant: Suggest structure and arrangement"
            setOnClickListener {
                val suggestion = AiPulseSongAssistant.style(genre.selectedItem.toString())
                bpm.progress = suggestion.bpm - 60
                instruments.setText(suggestion.instruments.joinToString(", "))
                if (lyrics.text.isBlank()) lyrics.setText(
                    AiPulseSongAssistant.lyricOutline(idea.text.toString(), language.selectedItem.toString()))
                AlertDialog.Builder(this@AiPulseStudioActivity)
                    .setTitle("Arrangement advice")
                    .setMessage("Chords: ${suggestion.chords}\n${suggestion.guidance}\nLyrics are editable placeholders, not AI-generated verses.")
                    .setPositiveButton("OK", null).show()
            }
        })
        heading("Creative generation controls · independent engine")
        val creativeMode = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@AiPulseStudioActivity,
                android.R.layout.simple_spinner_dropdown_item, listOf("Simple", "Advanced", "Speech"))
        }
        root.addView(creativeMode)
        val styleInput = field("Style instructions (genre / instruments / mood)", "")
        val excludedInput = field("Exclude instruments or traits", "")
        val instrumentalOnly = android.widget.CheckBox(this).apply {
            text = "Instrumental only (current offline renderer)"
            setTextColor(Color.WHITE)
            isChecked = true
        }
        root.addView(instrumentalOnly)
        fun creativeSlider(label: String, initial: Int): SeekBar {
            val value = heading("$label: $initial")
            return SeekBar(this).apply {
                max = 100
                progress = initial
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                        value.text = "$label: $progress"
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
                root.addView(this)
            }
        }
        val weirdness = creativeSlider("Variation / weirdness", 50)
        val styleInfluence = creativeSlider("Style influence", 50)
        val creativeStatus = heading("Creative settings ready · backend support is validated before rendering")
        heading("Hybrid online/offline · reference discovery")
        heading("Online metadata search is optional. It does not download songs or train on results.")
        val discoveryQuery = field("Reference song or artist", "")
        val discoveryResult = heading("Offline composer is always available")
        root.addView(Button(this).apply {
            text = "Search public music metadata"
            setOnClickListener {
                val query = discoveryQuery.text.toString()
                val online = AiPulseHybridDiscovery.isOnline(this@AiPulseStudioActivity)
                isEnabled = false
                discoveryResult.text = "Searching public recording metadata…"
                val button = this
                thread(name = "AiPulseMetadataLookup") {
                    val result = runCatching {
                        val stored = AiPulsePersistentCache.get(applicationContext, query)
                        if (stored != null) stored else {
                            val found = AiPulseDiscoveryGateway.search(query, online)
                            if (online) AiPulsePersistentCache.put(applicationContext, query, found)
                            found
                        }
                    }
                    runOnUiThread {
                        button.isEnabled = true
                        if (!isFinishing && !isDestroyed) {
                            discoveryResult.text = result.fold(
                                onSuccess = { matches ->
                                    if (matches.isEmpty()) "No cached matches. Online search requires a connection."
                                    else matches.joinToString("\\n") { "${it.title} — ${it.artist}" }
                                },
                                onFailure = { "Lookup unavailable: ${it.message}. Offline creation still works." }
                            )
                        }
                    }
                }
            }
        })
        heading("Duration")
        val duration = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("16")
            setTextColor(Color.WHITE)
        }
        root.addView(duration)
        var editedArrangement: AiPulseBrain.Plan? = null
        heading("Arrangement timeline · bar order and section energy")
        val timelineView = AiPulseTimelineView(this)
        root.addView(timelineView, LinearLayout.LayoutParams(
            -1, (120 * resources.displayMetrics.density).toInt()
        ))
        root.addView(Button(this).apply {
            text = "Brain: Generate song arrangement blueprint"
            setOnClickListener {
                val plan = AiPulseBrain.plan(AiPulseBrain.Input(
                    genre = genre.selectedItem.toString(),
                    mood = mood.text.toString(),
                    bpm = bpm.progress + 60,
                    bars = 8
                ))
                editedArrangement = plan
                timelineView.plan = plan
                duration.setText(AiPulseRenderDuration.secondsFor(plan).toString())
                val summary = plan.segments.joinToString("\\n") {
                    "Bars ${it.startBar + 1}–${it.startBar + it.bars}: ${it.section} · energy ${it.energy} · ${it.instruments.joinToString(", ")}"
                }
                AlertDialog.Builder(this@AiPulseStudioActivity)
                    .setTitle("AI Pulse Brain · offline arrangement")
                    .setMessage(summary)
                    .setPositiveButton("OK", null).show()
            }
        })
        root.addView(Button(this).apply {
            text = "Edit arrangement sections"
            setOnClickListener {
                val plan = editedArrangement ?: AiPulseBrain.plan(AiPulseBrain.Input(
                    genre = genre.selectedItem.toString(),
                    mood = mood.text.toString(),
                    bpm = bpm.progress + 60,
                    bars = 8
                )).also { editedArrangement = it }
                val descriptions = plan.segments.map {
                    "${it.section} · bars ${it.startBar + 1}–${it.startBar + it.bars} · energy ${(it.energy * 100).toInt()}%"
                }.toTypedArray()
                AlertDialog.Builder(this@AiPulseStudioActivity)
                    .setTitle("Select section to edit")
                    .setItems(descriptions) { _, index ->
                        val actions = arrayOf("Move earlier", "Move later", "Add 4 bars",
                            "Remove 4 bars", "Change section type", "Quieter (-10%)",
                            "Louder (+10%)", "Insert chorus after", "Remove section")
                        AlertDialog.Builder(this@AiPulseStudioActivity)
                            .setTitle("Edit ${plan.segments[index].section}")
                            .setItems(actions) { _, action ->
                                runCatching {
                                    val segment = plan.segments[index]
                                    editedArrangement = when (action) {
                                        0 -> AiPulseArrangementEditor.move(plan, index, index - 1)
                                        1 -> AiPulseArrangementEditor.move(plan, index, index + 1)
                                        2 -> AiPulseArrangementEditor.update(plan, index, bars = segment.bars + 4)
                                        3 -> AiPulseArrangementEditor.update(plan, index, bars = segment.bars - 4)
                                        4 -> {
                                            val types = AiPulseBrain.Section.entries
                                            val next = types[(segment.section.ordinal + 1) % types.size]
                                            AiPulseArrangementEditor.update(plan, index, section = next)
                                        }
                                        5 -> AiPulseArrangementEditor.update(plan, index,
                                            energy = (segment.energy - 0.10).coerceAtLeast(0.0))
                                        6 -> AiPulseArrangementEditor.update(plan, index,
                                            energy = (segment.energy + 0.10).coerceAtMost(1.0))
                                        7 -> AiPulseArrangementEditor.insert(plan, index)
                                        else -> AiPulseArrangementEditor.remove(plan, index)
                                    }
                                    val updated = requireNotNull(editedArrangement)
                                    timelineView.plan = updated
                                    duration.setText(AiPulseRenderDuration.secondsFor(updated).toString())
                                    Toast.makeText(this@AiPulseStudioActivity,
                                        "Timeline updated: ${updated.totalBars} bars", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(this@AiPulseStudioActivity,
                                        "Edit rejected: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            }.show()
                    }.show()
            }
        })
        heading("Your voice · local microphone recording")
        val voiceStatus = heading("No voice recording · recordings stay on device")
        val voiceButton = Button(this).apply { text = "Record voice" }
        root.addView(voiceButton)
        voiceButton.setOnClickListener {
            if (recording) {
                runCatching { recorder?.stop() }.onSuccess {
                    voiceStatus.text = "Saved private recording: ${voiceFile?.name}"
                }.onFailure {
                    voiceFile?.delete()
                    voiceStatus.text = "Recording failed: ${it.message}"
                }
                recorder?.release()
                recorder = null
                recording = false
                voiceButton.text = "Record voice"
            } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 300)
            } else {
                runCatching {
                    val file = File(filesDir, "ai_pulse_voice_${System.currentTimeMillis()}.m4a")
                    val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this@AiPulseStudioActivity) else MediaRecorder()
                    try {
                        r.setAudioSource(MediaRecorder.AudioSource.MIC)
                        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        r.setAudioEncodingBitRate(128000)
                        r.setAudioSamplingRate(44100)
                        r.setOutputFile(file.absolutePath)
                        r.prepare()
                        r.start()
                    } catch (e: Exception) { r.release(); file.delete(); throw e }
                    recorder = r
                    voiceFile = file
                    recording = true
                    voiceButton.text = "Stop recording"
                    voiceStatus.text = "Recording microphone locally…"
                }.onFailure { voiceStatus.text = "Microphone unavailable: ${it.message}" }
            }
        }
        val useRecordedVoice = android.widget.CheckBox(this).apply {
            text = "Mix my latest microphone recording (AAC/M4A) into generated music"
            setTextColor(Color.WHITE)
            isChecked = false
        }
        root.addView(useRecordedVoice)
        heading("Vocal mixing · optional PCM WAV")
        val importedVoiceStatus = heading("No imported WAV vocal. Microphone AAC/M4A can be decoded for mixing.")
        root.addView(Button(this).apply {
            text = "Import my authorized WAV vocal"
            setOnClickListener {
                AlertDialog.Builder(this@AiPulseStudioActivity)
                    .setTitle("Confirm media rights")
                    .setMessage("Import only recordings you created or have permission to use. This will not train an AI model or upload audio.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("I confirm") { _, _ ->
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "audio/wav"
                        }
                        startActivityForResult(intent, 301)
                    }.show()
            }
        })
        root.addView(Button(this).apply {
            text = "Remove imported vocal"
            setOnClickListener {
                importedVoiceWav?.delete()
                importedVoiceWav = null
                importedVoiceStatus.text = "No vocal selected"
            }
        })
        heading("Mix balance · adjust before generation")
        fun mixerSlider(label: String, initial: Int): SeekBar {
            val readout = heading("$label: $initial%")
            return SeekBar(this).apply {
                max = 100
                progress = initial
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                        readout.text = "$label: $value%"
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
                root.addView(this)
            }
        }
        val instrumentalGain = mixerSlider("Instrumental volume", 65)
        val recordedVoiceGain = mixerSlider("Vocal volume", 75)
        heading("Vocal delay · milliseconds")
        val vocalDelay = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("0")
            setTextColor(Color.WHITE)
        }
        root.addView(vocalDelay)
        heading("Audio mastering · WAV")
        val masteringPreset = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@AiPulseStudioActivity,
                android.R.layout.simple_spinner_dropdown_item,
                AiPulseMastering.Preset.entries.map { it.name })
        }
        root.addView(masteringPreset)
        heading("Instrument mixer · individual stem levels")
        val leadLevel = mixerSlider("Lead", 100)
        val bassLevel = mixerSlider("Bass", 100)
        val padLevel = mixerSlider("Pad", 100)
        val drumsLevel = mixerSlider("Drums", 100)
        heading("Track mute / solo")
        fun muteBox(label: String) = android.widget.CheckBox(this).apply {
            text = "Mute $label"
            setTextColor(Color.WHITE)
            root.addView(this)
        }
        val muteLead = muteBox("Lead")
        val muteBass = muteBox("Bass")
        val mutePad = muteBox("Pad")
        val muteDrums = muteBox("Drums")
        val soloTrack = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(
                this@AiPulseStudioActivity,
                android.R.layout.simple_spinner_dropdown_item,
                AiPulseTrackMixer.Track.entries.map { it.name }
            )
        }
        heading("Solo instrument (NONE = full mix)")
        root.addView(soloTrack)
        val exportStems = android.widget.CheckBox(this).apply {
            text = "Also export separate lead, bass, pad and drum WAV stems"
            setTextColor(Color.WHITE)
            isChecked = false
        }
        root.addView(exportStems)
        root.addView(Button(this).apply {
            text = "Save mixer preset"
            setOnClickListener {
                runCatching {
                    val preset = AiPulseMixPreset(
                        lead = leadLevel.progress, bass = bassLevel.progress,
                        pad = padLevel.progress, drums = drumsLevel.progress,
                        muteLead = muteLead.isChecked, muteBass = muteBass.isChecked,
                        mutePad = mutePad.isChecked, muteDrums = muteDrums.isChecked,
                        solo = AiPulseTrackMixer.Track.entries[soloTrack.selectedItemPosition],
                        musicGain = instrumentalGain.progress, vocalGain = recordedVoiceGain.progress,
                        vocalDelayMs = vocalDelay.text.toString().toIntOrNull()
                            ?: error("Enter a valid vocal delay"),
                        exportStems = exportStems.isChecked
                    )
                    File(filesDir, "ai_pulse_mixer.json").writeText(preset.toJson())
                    Toast.makeText(this@AiPulseStudioActivity, "Mixer preset saved locally", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this@AiPulseStudioActivity, "Save failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Load mixer preset"
            setOnClickListener {
                runCatching {
                    val p = AiPulseMixPreset.fromJson(File(filesDir, "ai_pulse_mixer.json").readText())
                    leadLevel.progress = p.lead; bassLevel.progress = p.bass
                    padLevel.progress = p.pad; drumsLevel.progress = p.drums
                    muteLead.isChecked = p.muteLead; muteBass.isChecked = p.muteBass
                    mutePad.isChecked = p.mutePad; muteDrums.isChecked = p.muteDrums
                    soloTrack.setSelection(p.solo.ordinal)
                    instrumentalGain.progress = p.musicGain
                    recordedVoiceGain.progress = p.vocalGain
                    vocalDelay.setText(p.vocalDelayMs.toString())
                    exportStems.isChecked = p.exportStems
                    Toast.makeText(this@AiPulseStudioActivity, "Mixer preset restored", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this@AiPulseStudioActivity, "Load failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Save editable project"
            setOnClickListener {
                runCatching {
                    val project = AiPulseProject(title.text.toString().trim(), idea.text.toString(),
                        genre.selectedItem.toString(), language.selectedItem.toString(),
                        mood.text.toString(), instruments.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        lyrics.text.toString(), bpm.progress + 60,
                        AiPulseMixPreset(
                            lead = leadLevel.progress, bass = bassLevel.progress,
                            pad = padLevel.progress, drums = drumsLevel.progress,
                            muteLead = muteLead.isChecked, muteBass = muteBass.isChecked,
                            mutePad = mutePad.isChecked, muteDrums = muteDrums.isChecked,
                            solo = AiPulseTrackMixer.Track.entries[soloTrack.selectedItemPosition],
                            musicGain = instrumentalGain.progress, vocalGain = recordedVoiceGain.progress,
                            vocalDelayMs = vocalDelay.text.toString().toIntOrNull()
                                ?: error("Invalid vocal delay"),
                            exportStems = exportStems.isChecked
                        ),
                        editedArrangement?.takeIf {
                            it.genre == genre.selectedItem.toString() && it.bpm == bpm.progress + 60
                        })
                    val file = File(filesDir, "ai_pulse_project.json")
                    file.writeText(project.toJson())
                    Toast.makeText(this@AiPulseStudioActivity, "Project saved locally", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(this@AiPulseStudioActivity, it.message, Toast.LENGTH_LONG).show() }
            }
        })
        root.addView(Button(this).apply {
            text = "Load saved project"
            setOnClickListener {
                runCatching {
                    val project = AiPulseProject.fromJson(File(filesDir, "ai_pulse_project.json").readText())
                    title.setText(project.title)
                    idea.setText(project.idea)
                    mood.setText(project.mood)
                    lyrics.setText(project.lyrics)
                    instruments.setText(project.instruments.joinToString(", "))
                    bpm.progress = project.bpm - 60
                    val p = project.mixer
                    leadLevel.progress = p.lead; bassLevel.progress = p.bass
                    padLevel.progress = p.pad; drumsLevel.progress = p.drums
                    muteLead.isChecked = p.muteLead; muteBass.isChecked = p.muteBass
                    mutePad.isChecked = p.mutePad; muteDrums.isChecked = p.muteDrums
                    soloTrack.setSelection(p.solo.ordinal)
                    instrumentalGain.progress = p.musicGain
                    recordedVoiceGain.progress = p.vocalGain
                    vocalDelay.setText(p.vocalDelayMs.toString())
                    exportStems.isChecked = p.exportStems
                    editedArrangement = project.arrangement
                    timelineView.plan = project.arrangement
                    project.arrangement?.let { duration.setText(AiPulseRenderDuration.secondsFor(it).toString()) }
                    (0 until genre.count).firstOrNull { genre.getItemAtPosition(it) == project.genre }?.let { genre.setSelection(it) }
                    (0 until language.count).firstOrNull { language.getItemAtPosition(it) == project.language }?.let { language.setSelection(it) }
                }.onFailure { Toast.makeText(this@AiPulseStudioActivity, it.message, Toast.LENGTH_LONG).show() }
            }
        })
        val memory = AiPulseGenerationMemory(applicationContext)
        root.addView(Button(this).apply {
            text = "Generation history · chronological summary"
            setOnClickListener {
                val events = memory.recent(15)
                val history = events.joinToString("\n") {
                    "${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.timestampMs))} · ${it.backendId} · ${it.outcome} · ${it.promptSummary}"
                }
                AlertDialog.Builder(this@AiPulseStudioActivity)
                    .setTitle("AI Pulse generation memory")
                    .setMessage(memory.summary() + if (history.isEmpty()) "\nNo attempts recorded yet." else "\n\n" + history)
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Clear history") { _, _ ->
                        AlertDialog.Builder(this@AiPulseStudioActivity)
                            .setTitle("Delete generation history?")
                            .setMessage("This clears only local generation history, not your songs or projects.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Clear") { _, _ -> memory.clear() }
                            .show()
                    }.show()
            }
        })
        val state = heading("Ready — generates WAV, saved to Music/AI Pulse")
        val create = Button(this).apply { text = "Generate music" }
        val cancel = Button(this).apply { text = "Cancel"; isEnabled = false }
        root.addView(create)
        root.addView(cancel)
        cancel.setOnClickListener {
            cancelRequested = true
            state.text = "Cancelling…"
        }
        create.setOnClickListener {
            if (working) return@setOnClickListener
            val creativePlan = try {
                AiPulseCreativeSettings.plan(AiPulseCreativeSettings.Settings(
                    mode = AiPulseCreativeSettings.Mode.entries[creativeMode.selectedItemPosition],
                    description = idea.text.toString(),
                    lyrics = lyrics.text.toString(),
                    style = styleInput.text.toString(),
                    exclusions = excludedInput.text.toString(),
                    instrumental = instrumentalOnly.isChecked,
                    weirdness = weirdness.progress,
                    styleInfluence = styleInfluence.progress,
                    bpm = bpm.progress + 60,
                    durationSeconds = duration.text.toString().toIntOrNull() ?: 16
                ))
            } catch (error: IllegalArgumentException) {
                creativeStatus.text = "Creative settings error: ${error.message}"
                return@setOnClickListener
            }
            if (creativePlan.task != AiPulseGenerationRouter.Task.INSTRUMENTAL) {
                creativeStatus.text = "This offline renderer supports instrumental only. Vocal song and speech require a separate installed backend."
                return@setOnClickListener
            }
            if (excludedInput.text.isNotBlank() || weirdness.progress != 50 || styleInfluence.progress != 50) {
                creativeStatus.text = "Variation, style influence and exclusions are stored as guidance only; the procedural renderer does not yet apply them."
            } else {
                creativeStatus.text = "Using offline instrumental composer"
            }
            val request = try {
                AiPulseComposer.Request(
                    title = title.text.toString().trim(),
                    mood = mood.text.toString().trim(),
                    bpm = bpm.progress + 60,
                    seconds = editedArrangement?.let { plan ->
                        require(AiPulseRenderDuration.compatible(plan,
                            genre.selectedItem.toString(), bpm.progress + 60)) {
                            "Your arrangement uses a different tempo or genre. Regenerate it or restore its original settings."
                        }
                        AiPulseRenderDuration.secondsFor(plan)
                    } ?: (duration.text.toString().toIntOrNull() ?: 0),
                    genre = genre.selectedItem.toString()
                ).also(AiPulseComposer::validate)
            } catch (e: IllegalArgumentException) {
                AlertDialog.Builder(this).setMessage(e.message ?: "Invalid input")
                    .setPositiveButton("OK", null).show()
                return@setOnClickListener
            }
            val delayMs = vocalDelay.text.toString().toIntOrNull()
            if (delayMs == null || delayMs !in 0..30000) {
                Toast.makeText(this, "Vocal delay must be 0–30000 ms", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val mixSettings = AiPulseWavMixer.Settings(
                musicGain = instrumentalGain.progress / 100.0,
                voiceGain = recordedVoiceGain.progress / 100.0,
                voiceDelayMs = delayMs
            )
            val includeRecordedVoice = useRecordedVoice.isChecked
            val saveStems = exportStems.isChecked
            val chosenArrangement = editedArrangement
            val instrumentLevels = AiPulseTrackMixer.apply(
                AiPulseStereoRenderer.Levels(
                    lead = leadLevel.progress / 100.0,
                    bass = bassLevel.progress / 100.0,
                    pad = padLevel.progress / 100.0,
                    drums = drumsLevel.progress / 100.0
                ),
                AiPulseTrackMixer.Settings(
                    leadMuted = muteLead.isChecked,
                    bassMuted = muteBass.isChecked,
                    padMuted = mutePad.isChecked,
                    drumsMuted = muteDrums.isChecked,
                    solo = AiPulseTrackMixer.Track.entries[soloTrack.selectedItemPosition]
                )
            )
            val selectedMasteringPreset = AiPulseMastering.Preset.entries[masteringPreset.selectedItemPosition]
            val routing = AiPulseGenerationRouter.choose(
                AiPulseGenerationRouter.Request(
                    prompt = idea.text.toString().ifBlank { request.title },
                    task = AiPulseGenerationRouter.Task.INSTRUMENTAL,
                    offlineOnly = true
                ),
                AiPulseGenerationRouter.Device(availableRamMb = 0, online = false),
                listOf(AiPulseGenerationRouter.Backend(
                    id = "ai-pulse-procedural-v1",
                    kind = AiPulseGenerationRouter.Kind.PROCEDURAL_OFFLINE,
                    supportedTasks = setOf(AiPulseGenerationRouter.Task.INSTRUMENTAL),
                    installed = true, licenseApproved = true, requiresNetwork = false
                ))
            )
            if (routing.backend == null) {
                state.text = routing.reason
                return@setOnClickListener
            }
            val generationStartedAt = System.currentTimeMillis()
            val generationId = java.util.UUID.randomUUID().toString()
            val promptSummary = idea.text.toString().trim().take(240)
            working = true
            cancelRequested = false
            create.isEnabled = false
            cancel.isEnabled = true
            state.text = "Generating offline audio…"
            thread(name = "AiPulseCompose") {
                val tmp = File(cacheDir, "pulse-${System.nanoTime()}.wav")
                val result = runCatching {
                    AiPulseStereoRenderer.render(request, tmp, levels = instrumentLevels, arrangement = chosenArrangement) { cancelRequested }
                    if (cancelRequested) throw InterruptedException("Cancelled")
                    val report = AiPulseWaveInspector.inspect(tmp)
                    require(report.durationSeconds > 0.0) { "Empty output" }
                    val mastered = File(cacheDir, "master-${System.nanoTime()}.wav")
                    try {
                        AiPulseMastering.process(tmp, mastered, selectedMasteringPreset) { cancelRequested }
                        if (cancelRequested) throw InterruptedException("Cancelled")
                        val decodedVoice = if (includeRecordedVoice) {
                            val recordingFile = voiceFile
                            require(!recording && recordingFile != null && recordingFile.exists()) {
                                "Finish recording before generation"
                            }
                            File(cacheDir, "decoded-voice-${System.nanoTime()}.wav").also {
                                AiPulseVoiceDecoder.decode(applicationContext, Uri.fromFile(recordingFile), it) { cancelRequested }
                            }
                        } else null
                        val vocal = decodedVoice ?: importedVoiceWav
                        val resampledVoice = if (vocal != null && vocal.exists() &&
                            AiPulseWavResampler.sampleRate(vocal) != 44100) {
                            File(cacheDir, "resampled-vocal-${System.nanoTime()}.wav").also {
                                AiPulseWavResampler.convert(vocal, it, 44100) { cancelRequested }
                            }
                        } else null
                        try {
                            val compatibleVoice = resampledVoice ?: vocal
                            if (compatibleVoice != null && compatibleVoice.exists()) {
                                val combined = File(cacheDir, "vocal-mix-${System.nanoTime()}.wav")
                                try {
                                    AiPulseWavMixer.mix(mastered, compatibleVoice, combined, mixSettings, cancelled = { cancelRequested })
                                    saveToMusic(combined, request.title)
                                } finally { combined.delete() }
                            } else saveToMusic(mastered, request.title)
                        } finally {
                            resampledVoice?.delete()
                            decodedVoice?.delete()
                        }
                    } finally { mastered.delete() }
                }
                if (result.isSuccess && saveStems && !cancelRequested) {
                    for (stem in AiPulseStereoRenderer.Stem.entries.filter { it != AiPulseStereoRenderer.Stem.MIX }) {
                        val stemFile = File(cacheDir, "stem-${stem.name.lowercase()}-${System.nanoTime()}.wav")
                        try {
                            AiPulseStereoRenderer.render(request, stemFile, stem, instrumentLevels, chosenArrangement) { cancelRequested }
                            if (cancelRequested) break
                            saveToMusic(stemFile, "${request.title}-${stem.name.lowercase()}")
                        } catch (error: Exception) {
                            runOnUiThread {
                                Toast.makeText(this@AiPulseStudioActivity,
                                    "Stem export issue: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                            break
                        } finally { stemFile.delete() }
                    }
                }
                tmp.delete()
                runCatching {
                    memory.record(AiPulseGenerationMemory.Event(
                        id = generationId,
                        timestampMs = generationStartedAt,
                        backendId = routing.backend.id,
                        task = "INSTRUMENTAL",
                        promptSummary = promptSummary,
                        outcome = when {
                            result.isSuccess -> "success"
                            result.exceptionOrNull() is InterruptedException -> "cancelled"
                            else -> "failed"
                        },
                        durationMs = System.currentTimeMillis() - generationStartedAt
                    ))
                }.onFailure { error ->
                    android.util.Log.w("AiPulseStudio", "Unable to save generation history", error)
                }
                runOnUiThread {
                    working = false
                    create.isEnabled = true
                    cancel.isEnabled = false
                    if (!isFinishing && !isDestroyed) {
                        result.onSuccess {
                            state.text = "Saved: $it · reopen Device library to scan"
                            Toast.makeText(this, "Music created", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            state.text = if (it is InterruptedException) "Cancelled" else
                                "Generation failed: ${it.message}"
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 301 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val target = File(filesDir, "ai_pulse_authorized_voice.wav")
        runCatching {
            val temporary = File(filesDir, "ai_pulse_voice_import.part")
            try {
                contentResolver.openInputStream(uri)?.use { source ->
                    temporary.outputStream().use { destination -> source.copyTo(destination) }
                } ?: error("Unable to open the selected recording")
                require(temporary.length() in 44..(64L * 1024 * 1024)) { "Vocal must be a WAV below 64 MB" }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                importedVoiceWav = target
                Toast.makeText(this, "Vocal imported; select Generate music to mix", Toast.LENGTH_LONG).show()
            } finally { temporary.delete() }
        }.onFailure {
            Toast.makeText(this, "Import failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveToMusic(input: File, title: String): String {
        val safe = title.replace(Regex("[^a-zA-Z0-9 _-]"), "_").take(60).ifBlank { "Pulse" }
        val name = "${safe}-${System.currentTimeMillis()}.wav"
        // RELATIVE_PATH and IS_PENDING require Android 10. On Android 8–9,
        // use app-specific external Music storage without broad storage access.
        // This location is private to the app and removed on uninstall.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val music = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: throw IllegalStateException("External Music storage unavailable")
            val folder = File(music, "AI Pulse")
            require(folder.isDirectory || folder.mkdirs()) { "Cannot create Music export directory" }
            val target = File(folder, name)
            try {
                input.copyTo(target, overwrite = false)
                require(target.length() == input.length()) { "Incomplete WAV export" }
                MediaScannerConnection.scanFile(applicationContext,
                    arrayOf(target.absolutePath), arrayOf("audio/wav"), null)
                return target.absolutePath
            } catch (error: Exception) {
                target.delete()
                throw error
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/AI Pulse")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Unable to create media entry")
        try {
            resolver.openOutputStream(uri)?.use { out -> input.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("Unable to open output")
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return name
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    override fun onStop() {
        if (recording) {
            runCatching { recorder?.stop() }.onFailure { voiceFile?.delete() }
            recorder?.release()
            recorder = null
            recording = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        cancelRequested = true
        super.onDestroy()
    }
}
