package com.pulseplayer.app.aipulse

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional user-configured ACE-Step HTTP client. Invoke on a worker thread only.
 * No recordings are sent by this adapter. Never embed API keys in the APK.
 */
class AiPulseAceStepClient(private val endpoint: String, private val token: String? = null) {
    init {
        val url = URL(endpoint)
        require(url.protocol == "https" || (url.protocol == "http" &&
            url.host in setOf("localhost", "127.0.0.1"))) {
            "Use HTTPS or local loopback; do not send prompts over cleartext LAN"
        }
        require(url.userInfo == null && url.query == null && url.ref == null)
    }
    private fun request(path: String, payload: JSONObject): JSONObject {
        val conn = URL(endpoint.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            require(conn.responseCode in 200..299) { "Generation server HTTP ${conn.responseCode}" }
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(response)
            require(root.optInt("code", 200) == 200) { root.optString("error", "Server error") }
            return root
        } finally { conn.disconnect() }
    }
    fun submit(spec: AiPulseGenerationSpec): String {
        spec.validate()
        val prompt = listOf(spec.description, spec.genre,
            spec.instruments.joinToString(", "), "${spec.bpm} BPM").filter { it.isNotBlank() }.joinToString("; ")
        val body = JSONObject().put("prompt", prompt).put("lyrics", spec.lyrics)
            .put("audio_duration", spec.seconds).put("bpm", spec.bpm)
            .put("thinking", true).put("batch_size", 1)
            .put("instrumental", spec.lyrics.isBlank())
        val data = request("/release_task", body).getJSONObject("data")
        return data.getString("task_id")
    }
    data class Status(val state: Int, val audioPath: String? = null, val error: String? = null)
    fun status(taskId: String): Status {
        require(taskId.isNotBlank())
        val data = request("/query_result", JSONObject().put("task_id_list", JSONArray().put(taskId)))
            .getJSONArray("data")
        require(data.length() > 0) { "No task status returned" }
        val item = data.getJSONObject(0)
        val state = item.getInt("status")
        val raw = item.optString("result", "")
        var path: String? = null
        if (state == 1 && raw.isNotBlank()) {
            val result = JSONArray(raw)
            if (result.length() > 0) path = result.getJSONObject(0).optString("file").takeIf { it.isNotBlank() }
        }
        return Status(state, path, item.optString("error").takeIf { it.isNotBlank() })
    }
    /** Only accept server-relative audio paths; never download an arbitrary external URL. */
    fun download(audioPath: String, destination: File) {
        require(audioPath.startsWith("/v1/audio?path=") && !audioPath.contains("://"))
        val conn = URL(endpoint.trimEnd('/') + audioPath).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
            require(conn.responseCode in 200..299)
            val temp = File(destination.absolutePath + ".part")
            try {
                conn.inputStream.use { source -> temp.outputStream().use { target ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= 100_000_000) { "Audio exceeds 100 MB" }
                        target.write(buffer, 0, n)
                    }
                } }
                require(temp.length() > 1024) { "Empty audio download" }
                if (!temp.renameTo(destination)) { temp.copyTo(destination, overwrite = true); temp.delete() }
            } catch (e: Exception) { temp.delete(); throw e }
        } finally { conn.disconnect() }
    }
}
