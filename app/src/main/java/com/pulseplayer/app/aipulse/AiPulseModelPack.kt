package com.pulseplayer.app.aipulse

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Manifest-driven local voice model pack. Import only licensed, consented voices. */
internal data class AiPulseModelPack(
    val name: String,
    val license: String,
    val consent: String,
    val language: String,
    val sampleRate: Int,
    val frameRate: Int,
    val acoustic: File,
    val acousticSha256: String,
    val vocoder: File,
    val vocoderSha256: String
) {
    companion object {
        fun load(folder: File): AiPulseModelPack {
            require(folder.isDirectory) { "Model folder missing" }
            val manifest = File(folder, "manifest.json")
            require(manifest.isFile && manifest.length() in 1..65536) { "Missing manifest" }
            val json = JSONObject(manifest.readText())
            fun required(key: String): String =
                json.optString(key).trim().also { require(it.isNotBlank()) { "Missing $key" } }
            fun model(key: String): File {
                val filename = required(key)
                require(filename.matches(Regex("[A-Za-z0-9_.-]+")) && filename.endsWith(".onnx")) {
                    "Invalid model filename"
                }
                val file = File(folder, filename)
                require(file.canonicalFile.parentFile == folder.canonicalFile &&
                    file.isFile && file.length() in 1..(512L * 1024 * 1024)) {
                    "Missing or oversized $key"
                }
                return file
            }
            val license = required("license")
            val consent = required("voice_consent")
            require(consent == "owner" || consent == "licensed") {
                "Only self-owned or explicitly licensed voice packs are supported"
            }
            val rate = json.optInt("sample_rate")
            require(rate in 8000..192000) { "Invalid sample rate" }
            val frameRate = json.optInt("frame_rate", 100)
            require(frameRate in 20..1000) { "Invalid frame rate" }
            val acoustic = model("acoustic")
            val vocoder = model("vocoder")
            val acousticHash = required("acoustic_sha256")
            val vocoderHash = required("vocoder_sha256")
            verify(acoustic, acousticHash)
            verify(vocoder, vocoderHash)
            return AiPulseModelPack(required("name"), license, consent,
                required("language"), rate, frameRate, acoustic, acousticHash, vocoder, vocoderHash)
        }

        private fun verify(file: File, expected: String) {
            require(expected.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid SHA-256" }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(65536)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual.equals(expected, ignoreCase = true)) {
                "Model integrity verification failed"
            }
        }
    }
}
