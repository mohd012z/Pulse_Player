package com.pulseplayer.app.aipulse

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
        heading("Duration")
        val duration = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("16")
            setTextColor(Color.WHITE)
        }
        root.addView(duration)
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
            val request = try {
                AiPulseComposer.Request(
                    title = title.text.toString().trim(),
                    mood = mood.text.toString().trim(),
                    bpm = bpm.progress + 60,
                    seconds = duration.text.toString().toIntOrNull() ?: 0
                ).also(AiPulseComposer::validate)
            } catch (e: IllegalArgumentException) {
                AlertDialog.Builder(this).setMessage(e.message ?: "Invalid input")
                    .setPositiveButton("OK", null).show()
                return@setOnClickListener
            }
            working = true
            cancelRequested = false
            create.isEnabled = false
            cancel.isEnabled = true
            state.text = "Generating offline audio…"
            thread(name = "AiPulseCompose") {
                val tmp = File(cacheDir, "pulse-${System.nanoTime()}.wav")
                val result = runCatching {
                    AiPulseComposer.render(request, tmp) { cancelRequested }
                    if (cancelRequested) throw InterruptedException("Cancelled")
                    saveToMusic(tmp, request.title)
                }
                tmp.delete()
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

    private fun saveToMusic(input: File, title: String): String {
        val safe = title.replace(Regex("[^a-zA-Z0-9 _-]"), "_").take(60).ifBlank { "Pulse" }
        val name = "${safe}-${System.currentTimeMillis()}.wav"
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

    override fun onDestroy() {
        cancelRequested = true
        super.onDestroy()
    }
}
