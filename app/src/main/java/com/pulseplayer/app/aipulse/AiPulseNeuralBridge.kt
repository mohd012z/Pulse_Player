package com.pulseplayer.app.aipulse

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional neural music provider protocol. A separately installed server/model must
 * implement POST /generate and return audio/wav bytes. No model weights are bundled.
 * Never run this on the Android main thread.
 */
internal class AiPulseNeuralBridge(private val context: Context) {
    data class Request(val title: String, val description: String, val lyrics: String,
                       val genre: String, val language: String, val instrumental: Boolean,
                       val seconds: Int)

    fun generate(endpoint: String, request: Request, cancelled: () -> Boolean): File {
        val url = URL(endpoint.trim())
        require(url.protocol == "https") { "Neural provider must use HTTPS" }
        require(url.userInfo == null && url.ref == null) { "Invalid provider URL" }
        require(request.seconds in 5..600) { "Duration must be 5–600 seconds" }
        val output = File.createTempFile("pulse-neural-", ".wav", context.cacheDir)
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 180000
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "audio/wav")
            }
            val payload = JSONObject().apply {
                put("title", request.title)
                put("prompt", request.description)
                put("lyrics", request.lyrics)
                put("genre", request.genre)
                put("language", request.language)
                put("instrumental", request.instrumental)
                put("duration_seconds", request.seconds)
            }.toString().toByteArray(Charsets.UTF_8)
            require(payload.size <= 64 * 1024) { "Prompt too large" }
            connection.outputStream.use { it.write(payload) }
            check(connection.responseCode == 200) {
                "Neural provider returned HTTP ${connection.responseCode}"
            }
            val mime = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
            require(mime in setOf("audio/wav", "audio/x-wav", "audio/wave", "application/octet-stream")) {
                "Provider returned ${connection.contentType}, not WAV"
            }
            connection.inputStream.use { input ->
                output.outputStream().use { sink ->
                    val buffer = ByteArray(32768)
                    var total = 0L
                    while (true) {
                        if (cancelled()) throw InterruptedException("Generation cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 200L * 1024 * 1024) { "Neural audio exceeds 200 MB" }
                        sink.write(buffer, 0, count)
                    }
                }
            }
            require(AiPulseGeneratedAudioVerifier.inspect(output).valid) {
                "Neural provider output is not a valid WAV"
            }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            connection?.disconnect()
        }
    }
}
