package com.pulseplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import java.util.Locale

/**
 * Local subtitle editor. Translation is explicitly user-initiated through an installed
 * translation app; no audio or transcript is silently uploaded to a third party.
 * Speech recognition is a short-utterance helper, not a full-track transcription engine.
 */
class SubtitleStudioActivity : Activity() {
    private val importCode = 831
    private val exportCode = 832
    private val speechCode = 833
    private lateinit var editor: EditText
    private lateinit var status: TextView
    private lateinit var language: Spinner
    private var subtitleName = "PulseSubtitles.srt"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "Subtitle Studio · English / Bahasa Melayu"
            textSize = 21f
        })
        root.addView(TextView(this).apply {
            text = "Import, edit and export SRT. Speech recognition can create a short draft using your device's recognizer. Translation opens a compatible installed app; translated text must be pasted back and checked before export."
        })
        language = Spinner(this).apply {
            adapter = ArrayAdapter(this@SubtitleStudioActivity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("English", "Bahasa Melayu"))
        }
        root.addView(language)
        status = TextView(this).apply { text = "No subtitles loaded" }
        root.addView(status)
        editor = EditText(this).apply {
            hint = "1\n00:00:00,000 --> 00:00:03,000\nSubtitle text"
            minLines = 12
            gravity = android.view.Gravity.TOP
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            addView(editor)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun action(label: String, click: () -> Unit) {
            root.addView(Button(this).apply { text = label; setOnClickListener { click() } })
        }
        action("Import SRT") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, importCode)
        }
        action("Validate & preview cues") {
            val cues = SrtParser.parse(editor.text.toString())
            status.text = "Valid cues: ${cues.size}. Preview: ${cues.firstOrNull()?.text.orEmpty().take(90)}"
        }
        action("Generate short caption from speech") {
            val locale = if (language.selectedItemPosition == 1) "ms-MY" else "en-US"
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a short caption")
            }
            runCatching { startActivityForResult(intent, speechCode) }
                .onFailure { status.text = "No compatible speech recognizer installed." }
        }
        action("Translate selected text in installed app") {
            val text = editor.text.toString()
            if (text.isBlank()) { status.text = "Import or type subtitles first"; return@action }
            val target = if (language.selectedItemPosition == 1) "Malay" else "English"
            AlertDialog.Builder(this).setTitle("Translate to $target")
                .setMessage("The text will be shared with an app you select. Use a trusted translator, then paste its translated text back here. Timing lines should remain unchanged.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue") { _, _ ->
                    runCatching {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Translate to $target, preserve SRT timestamps:\n$text")
                        }, "Choose translation app"))
                    }.onFailure { status.text = "No compatible translation app available" }
                }.show()
        }
        action("Export SRT") {
            val cues = SrtParser.parse(editor.text.toString())
            if (cues.isEmpty()) { status.text = "No valid SRT cues to export"; return@action }
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "text/plain"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, subtitleName)
            }, exportCode)
        }
        setContentView(root)
    }

    @Deprecated("Legacy Activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            importCode -> {
                val uri = data?.data ?: return
                runCatching {
                    contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }.onSuccess {
                    editor.setText(it)
                    status.text = "Imported ${SrtParser.parse(it).size} timed cues"
                }.onFailure { status.text = "Import failed: ${it.message}" }
            }
            exportCode -> {
                val uri = data?.data ?: return
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
                        it.write(editor.text.toString())
                    }
                }.onSuccess { status.text = "SRT exported" }
                    .onFailure { status.text = "Export failed: ${it.message}" }
            }
            speechCode -> {
                val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!result.isNullOrBlank()) {
                    val cues = SrtParser.parse(editor.text.toString())
                    val start = (cues.lastOrNull()?.endMs ?: 0L) + 250
                    fun stamp(ms: Long): String = String.format(Locale.ROOT,
                        "%02d:%02d:%02d,%03d", ms / 3600000, ms / 60000 % 60,
                        ms / 1000 % 60, ms % 1000)
                    val prefix = if (editor.text.isBlank()) "" else "\n\n"
                    editor.append("$prefix${cues.size + 1}\n${stamp(start)} --> ${stamp(start + 3000)}\n$result")
                    status.text = "Draft caption added. Adjust timing manually."
                }
            }
        }
    }
}
