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

    fun generate(baseUrl: String, request: AiPulseNeuralBridge.Request, cancelled: () -> Boolean): File {
        val base = URL(baseUrl.trim().trimEnd('/') + "/")
        require(base.protocol == "https" && base.userInfo == null && base.ref == null) {
            "ACE-Step server requires an HTTPS base URL"
        }
        require(request.seconds in 10..600) { "ACE-Step duration must be 10–600 seconds" }
        val prompt = listOf(request.genre, request.description).filter { it.isNotBlank() }.joinToString(", ")
        val lyrics = if (request.instrumental) "[inst]" else request.lyrics
        val submission = post(URL(base, "release_task"), JSONObject().apply {
            put("caption", prompt)
            put("lyrics", lyrics)
            put("audio_format", "wav")
            put("use_format", false)
            put("duration", request.seconds)
            put("vocal_language", when {
                request.language.contains("English", true) && !request.language.contains("BM", true) -> "en"
                else -> "unknown"
            })
            put("thinking", true)
        })
        val data = submission.optJSONObject("data")
        val taskId = data?.optString("task_id").orEmpty().ifBlank {
            submission.optString("task_id")
        }
        require(taskId.isNotBlank()) { "ACE-Step returned no task_id" }
        val deadline = System.currentTimeMillis() + 12 * 60 * 1000L
        while (System.currentTimeMillis() < deadline) {
            if (cancelled()) throw InterruptedException("Cancelled")
            val status = post(URL(base, "query_result"), JSONObject().apply {
                put("task_id_list", JSONArray().put(taskId))
            }).optJSONArray("data")?.optJSONObject(0)
                ?: error("ACE-Step returned no task status")
            when (status.optInt("status", 0)) {
                2 -> error("ACE-Step generation failed: ${status.optString("error")}")
                1 -> {
                    val raw = status.optString("result")
                    val files = JSONArray(raw)
                    val path = files.getJSONObject(0).getString("file")
                    val audioUrl = URL(base, path)
                    require(audioUrl.protocol == "https" && audioUrl.host == base.host &&
                        audioUrl.port == base.port) { "Untrusted audio download location" }
                    val output = File.createTempFile("ace-step-", ".wav", context.cacheDir)
                    try {
                        val conn = connection(audioUrl, "GET")
                        try {
                            check(conn.responseCode == 200) { "Audio download HTTP ${conn.responseCode}" }
                            conn.inputStream.use { input ->
                                output.outputStream().use { sink ->
                                    val buffer = ByteArray(32768)
                                    var total = 0L
                                    while (true) {
                                        if (cancelled()) throw InterruptedException("Cancelled")
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        require(total <= 200L * 1024 * 1024) { "Audio exceeds 200 MB" }
                                        sink.write(buffer, 0, count)
                                    }
                                }
                            }
                        } finally { conn.disconnect() }
                        require(AiPulseGeneratedAudioVerifier.inspect(output).valid) {
                            "ACE-Step output failed WAV verification"
                        }
                        return output
                    } catch (error: Exception) {
                        output.delete()
                        throw error
                    }
                }
            }
            Thread.sleep(2500)
        }
        error("ACE-Step timed out after 12 minutes")
    }
}
