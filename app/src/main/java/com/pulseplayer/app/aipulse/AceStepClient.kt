package com.pulseplayer.app.aipulse

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Native ACE-Step 1.5 asynchronous API client (not the older direct-WAV bridge). */
internal class AceStepClient(private val context: Context) {
    private fun connection(url: URL, method: String): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }

    private fun post(url: URL, payload: JSONObject): JSONObject {
        val conn = connection(url, "POST")
        try {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            check(conn.responseCode == 200) { "ACE-Step HTTP ${conn.responseCode}" }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            check(json.optInt("code", 200) == 200) { json.optString("error", "ACE-Step error") }
            return json
        } finally { conn.disconnect() }
    }

    fun checkHealth(baseUrl: String): String {
        val base = URL(baseUrl.trim().trimEnd('/') + "/")
        require(base.protocol == "https" && base.userInfo == null && base.ref == null) {
            "Use an HTTPS ACE-Step server URL"
        }
        val conn = connection(URL(base, "health"), "GET")
        try {
            check(conn.responseCode == 200) { "Health check HTTP ${conn.responseCode}" }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            check(json.optInt("code", 200) == 200) { "Server reported unhealthy status" }
            return "ACE-Step server reachable · ${json.optJSONObject("data")?.optString("status", "OK") ?: "OK"}"
        } finally {
            conn.disconnect()
        }
    }

    /**
     * ACE-Step audio-editing request contract. Editing requires an uploaded source
     * reference from the user's own recording; a local Android path is not a server path.
     * Keep editing disabled until the backend upload response supplies a trusted reference.
     */
    internal data class EditSpec(
        val taskType: String,
        val sourceAudioReference: String,
        val caption: String,
        val repaintStart: Double? = null,
        val repaintEnd: Double? = null
    ) {
        fun validate() {
            require(taskType in setOf("cover", "repaint", "lego", "extract", "complete")) {
                "Unsupported ACE-Step editing task"
            }
            require(sourceAudioReference.isNotBlank() &&
                !sourceAudioReference.startsWith("file:") &&
                !sourceAudioReference.startsWith("/storage/")) {
                "Upload source audio first; local file paths are not server references"
            }
            if (taskType == "repaint") {
                require(repaintStart != null && repaintEnd != null &&
                    repaintStart.isFinite() && repaintEnd.isFinite() &&
                    repaintStart >= 0.0 && repaintEnd > repaintStart) {
                    "Repaint requires a valid time interval"
                }
            }
        }
        fun payload(): JSONObject {
            validate()
            return JSONObject().apply {
                put("task_type", taskType)
                put("caption", caption)
                put("audio_format", "wav")
                put("reference_audio", sourceAudioReference)
                if (repaintStart != null) put("repainting_start", repaintStart)
                if (repaintEnd != null) put("repainting_end", repaintEnd)
            }
        }
    }

    /**
     * ACE-Step v1.5 current /v1 asynchronous API.
     * No GPU server is owned by the Android app: users can supply a hosted HTTPS endpoint.
     */
    fun generate(baseUrl: String, request: AiPulseNeuralBridge.Request, cancelled: () -> Boolean): File {
        val base = URL(baseUrl.trim().trimEnd('/') + "/")
        require(base.protocol == "https" && base.userInfo == null && base.ref == null) {
            "Use a trusted HTTPS inference endpoint"
        }
        require(request.seconds in 10..600) { "Duration must be 10–600 seconds" }
        val prompt = listOf(request.genre, request.description)
            .filter { it.isNotBlank() }.joinToString(", ")
        require(prompt.isNotBlank()) { "Music description required" }
        val submission = post(URL(base, "v1/music/generate"), JSONObject().apply {
            put("caption", prompt)
            put("lyrics", if (request.instrumental) "[inst]" else request.lyrics)
            put("audio_format", "wav")
            put("duration", request.seconds)
            put("thinking", true)
        })
        val jobId = submission.optString("job_id")
        require(jobId.isNotBlank()) { "Server returned no job_id" }
        val deadline = System.currentTimeMillis() + 12 * 60 * 1000L
        while (System.currentTimeMillis() < deadline) {
            if (cancelled()) throw InterruptedException("Cancelled")
            val conn = connection(URL(base, "v1/jobs/" + java.net.URLEncoder.encode(jobId, "UTF-8")), "GET")
            val job = try {
                check(conn.responseCode == 200) { "Job polling HTTP ${conn.responseCode}" }
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } finally { conn.disconnect() }
            when (job.optString("status")) {
                "failed" -> error("Generation failed: ${job.optString("error", "unknown")}")
                "succeeded" -> {
                    val result = job.optJSONObject("result") ?: error("Missing generation result")
                    val files = result.optJSONArray("audio_files")
                        ?: result.optJSONArray("files")
                        ?: error("No audio files in result")
                    require(files.length() > 0) { "Empty generated audio list" }
                    val item = files.getJSONObject(0)
                    val rawPath = item.optString("url").ifBlank {
                        item.optString("file").ifBlank { item.optString("path") }
                    }
                    require(rawPath.isNotBlank()) { "No generated audio path" }
                    val audioUrl = URL(base, rawPath)
                    require(audioUrl.protocol == base.protocol && audioUrl.host == base.host &&
                        audioUrl.port == base.port && audioUrl.userInfo == null) {
                        "Untrusted audio download URL"
                    }
                    val output = File.createTempFile("ace-step-", ".wav", context.cacheDir)
                    try {
                        val download = connection(audioUrl, "GET")
                        try {
                            download.setRequestProperty("Accept", "audio/wav")
                            check(download.responseCode == 200) { "Download HTTP ${download.responseCode}" }
                            download.inputStream.use { input ->
                                output.outputStream().use { sink ->
                                    val buffer = ByteArray(32768)
                                    var size = 0L
                                    while (true) {
                                        if (cancelled()) throw InterruptedException("Cancelled")
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        size += n
                                        require(size <= 200L * 1024 * 1024) { "Audio exceeds 200 MB" }
                                        sink.write(buffer, 0, n)
                                    }
                                }
                            }
                        } finally { download.disconnect() }
                        require(AiPulseGeneratedAudioVerifier.inspect(output).valid) {
                            "Generated file is not a valid WAV"
                        }
                        return output
                    } catch (e: Exception) {
                        output.delete()
                        throw e
                    }
                }
            }
            Thread.sleep(2500)
        }
        error("Neural generation timed out")
    }
}
