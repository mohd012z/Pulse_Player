package com.pulseplayer.app.aipulse

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** Local voicepack management and explicitly labeled experimental ONNX render. */
class AiPulseVoicePackActivity : Activity() {
    private lateinit var status: TextView
    private val packDir get() = File(filesDir, "ai_pulse_voicepack")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        fun button(title: String, click: () -> Unit) {
            root.addView(Button(this).apply { text = title; setOnClickListener { click() } })
        }
        status = TextView(this).apply {
            textSize = 16f
            text = "No model validated. Import manifest.json, acoustic.onnx and vocoder.onnx separately."
        }
        root.addView(status)
        button("Import voicepack file (manifest / acoustic / vocoder)") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, 701)
        }
        button("Validate installed model pack") {
            try {
                val pack = AiPulseModelPack.load(packDir)
                status.text = "Validated: ${pack.name} (${pack.language}); voice license: ${pack.license}. Model inference not yet tested."
            } catch (e: Exception) { status.text = "Validation failed: ${e.message}" }
        }
        button("Inspect ONNX model signatures (P0)") {
            status.text = "Inspecting acoustic and vocoder graphs…"
            Thread {
                val report = runCatching {
                    val pack = AiPulseModelPack.load(packDir)
                    AiPulseOnnxInspector.inspect(pack).toString(2)
                }
                runOnUiThread {
                    status.text = report.fold(
                        onSuccess = { it },
                        onFailure = { "Model inspection failed: ${it.message}" }
                    )
                }
            }.start()
        }
        val tokenInput = EditText(this).apply { hint = "Phoneme IDs (comma-separated), e.g. 1,2,3"; setSingleLine() }
        val framesInput = EditText(this).apply { hint = "Durations in frames, e.g. 40,40,40"; setSingleLine() }
        val midiInput = EditText(this).apply { hint = "MIDI pitches per frame, e.g. 60x40,62x40,64x40"; setSingleLine() }
        root.addView(tokenInput); root.addView(framesInput); root.addView(midiInput)
        val syllableInput = EditText(this).apply { hint = "Syllables separated by spaces"; setSingleLine() }
        val notesInput = EditText(this).apply { hint = "MIDI:seconds pairs e.g. 60:0.5,62:0.5"; setSingleLine() }
        root.addView(syllableInput); root.addView(notesInput)
        button("Prepare phonemes from dictionary.json") {
            try {
                val syllables = syllableInput.text.toString().trim().split(Regex("\\s+"))
                val pitches = notesInput.text.toString().split(",").map {
                    val fields = it.trim().split(":")
                    require(fields.size == 2)
                    fields[0].toInt() to fields[1].toDouble()
                }
                require(syllables.size == pitches.size)
                val dictionary = File(packDir, "dictionary.json")
                require(dictionary.isFile && dictionary.length() in 1..65536)
                val phrase = AiPulsePhraseBuilder.build(syllables.indices.map { i ->
                    AiPulsePhraseBuilder.Note(syllables[i], pitches[i].first, pitches[i].second)
                }, dictionary.readText(), org.json.JSONObject(File(packDir, "manifest.json").readText()).optInt("frame_rate", 100))
                tokenInput.setText(phrase.phonemeTokens.joinToString(","))
                framesInput.setText(phrase.phonemeFrames.joinToString(","))
                midiInput.setText(phrase.midiNotes.joinToString(",") { "${it.toInt()}x1" })
                status.text = "Prepared ${phrase.phonemeTokens.size} phonemes; check model frame rate"
            } catch (e: Exception) { status.text = "Phoneme preparation failed: ${e.message}" }
        }
        button("Render experimental neural WAV (offline)") {
            try {
                val tokens = tokenInput.text.toString().split(",").map { it.trim().toLong() }.toLongArray()
                val frames = framesInput.text.toString().split(",").map { it.trim().toLong() }.toLongArray()
                val notes = midiInput.text.toString().split(",").flatMap { part ->
                    val fields = part.trim().split("x")
                    require(fields.size == 2) { "Use pitch x frames, e.g. 60x40" }
                    val count = fields[1].toInt()
                    require(count in 1..100000) { "Invalid frame count" }
                    List(count) { fields[0].toFloat() }
                }.toFloatArray()
                val pack = AiPulseModelPack.load(packDir)
                status.text = "Running on-device ONNX inference…"
                Thread {
                    val result = runCatching {
                        AiPulseDiffSinger(pack).use { renderer ->
                            renderer.render(AiPulseDiffSinger.Phrase(tokens, frames, notes),
                                File(filesDir, "ai_pulse_neural_voice.wav"))
                        }
                    }
                    runOnUiThread {
                        status.text = result.fold(
                            onSuccess = { "Neural WAV created: ${it.absolutePath} (${it.length()} bytes)" },
                            onFailure = { "Neural render failed: ${it.message}" }
                        )
                    }
                }.start()
            } catch (e: Exception) { status.text = "Input error: ${e.message}" }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 701 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val name = android.provider.OpenableColumns.DISPLAY_NAME.let { column ->
            contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }
        if (name !in setOf("manifest.json", "acoustic.onnx", "vocoder.onnx", "dictionary.json")) {
            status.text = "Only manifest.json, acoustic.onnx, vocoder.onnx and dictionary.json accepted"
            return
        }
        try {
            packDir.mkdirs()
            val target = File(packDir, name!!)
            val temp = File(packDir, "$name.partial")
            contentResolver.openInputStream(uri)?.use { source ->
                temp.outputStream().use { destination -> source.copyTo(destination) }
            } ?: error("Cannot open selected file")
            require(temp.length() in 1..(512L * 1024 * 1024)) { "Invalid file size" }
            require(temp.renameTo(target)) { "Unable to save model" }
            status.text = "Imported $name. Validate model pack before rendering."
        } catch (e: Exception) { status.text = "Import failed: ${e.message}" }
    }
}
