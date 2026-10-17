package com.pulseplayer.app.aipulse

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import androidx.webkit.JavaScriptReplyProxy
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.zip.CRC32

/** Serial, ACK-driven, bounded transfer; all session state belongs to the I/O executor. */
class PulseNativeExport(private val context: Context, private val requestLegacySave: (String) -> Unit) {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var session = ""
    private var reply: JavaScriptReplyProxy? = null
    private var temp: File? = null
    private var stream: FileOutputStream? = null
    private var expected = 0
    private var received = 0
    private var index = 0
    private var checksum = 0L
    private val crc = CRC32()
    private var legacyPending = false
    private var deadline = 0L
    private var generation = 0L
    @Volatile private var closed = false

    private fun respond(proxy: JavaScriptReplyProxy, id: String, status: String, extra: JSONObject = JSONObject()) {
        val msg = JSONObject().put("sessionId", id).put("status", status).put("data", extra)
        main.post { if (!closed) proxy.postMessage(msg.toString()) }
    }
    private fun error(proxy: JavaScriptReplyProxy, id: String, reason: String) {
        val msg = JSONObject().put("sessionId", id).put("status", "ERROR").put("reason", reason)
        main.post { if (!closed) proxy.postMessage(msg.toString()) }
    }
    private fun reset() {
        generation++
        legacyPending = false
        runCatching { stream?.close() }
        stream = null
        temp?.delete()
        temp = null
        session = ""
        reply = null
        expected = 0
        received = 0
        index = 0
        checksum = 0
        deadline = 0
        crc.reset()
    }
    private fun scheduleTimeout() {
        deadline = android.os.SystemClock.elapsedRealtime() + 30000
        val token = generation
        main.postDelayed({
            if (!closed) try {
                io.execute {
                    if (generation == token && session.isNotEmpty()) {
                        val remaining = deadline - android.os.SystemClock.elapsedRealtime()
                        if (remaining <= 0) {
                            val p = reply
                            val id = session
                            reset()
                            if (p != null) error(p, id, "TIMEOUT")
                        } else scheduleTimeout()
                    }
                }
            } catch (_: RejectedExecutionException) {}
        }, 30000)
    }
    fun accept(json: String, proxy: JavaScriptReplyProxy) {
        if (closed || json.length > 13000) return
        try {
            io.execute {
                val msg = try { JSONObject(json) } catch (_: Exception) { return@execute }
                val id = msg.optString("sessionId").take(96)
                val command = msg.optString("command")
                val data = msg.optJSONObject("payload") ?: JSONObject()
                try {
                    when (command) {
                        "START" -> {
                            if (session.isNotEmpty()) { error(proxy, id, "BUSY"); return@execute }
                            val size = data.optInt("totalSize", -1)
                            val sum = data.optLong("checksum", -1)
                            if (id.isBlank() || size < 46 || size > PulseWavValidator.MAX_BYTES || sum !in 0..0xffffffffL) {
                                error(proxy, id, "INVALID_START"); return@execute
                            }
                            reset()
                            temp = File.createTempFile("pulse_wav_", ".tmp", context.cacheDir)
                            stream = FileOutputStream(temp!!)
                            session = id; reply = proxy; expected = size; checksum = sum
                            scheduleTimeout()
                            respond(proxy, id, "STARTED")
                        }
                        "CHUNK" -> {
                            if (id != session || proxy !== reply) { error(proxy, id, "INVALID_SESSION"); return@execute }
                            val n = data.optInt("chunkIndex", -1)
                            val encoded = data.optString("data")
                            if (n != index || encoded.length > 11000 || encoded.isEmpty()) {
                                reset(); error(proxy, id, "INVALID_CHUNK"); return@execute
                            }
                            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                            if (bytes.isEmpty() || bytes.size > 8192 || bytes.size > expected - received) {
                                reset(); error(proxy, id, "SIZE_LIMIT"); return@execute
                            }
                            stream!!.write(bytes)
                            crc.update(bytes)
                            received += bytes.size; index++
                            deadline = android.os.SystemClock.elapsedRealtime() + 30000
                            respond(proxy, id, "ACK", JSONObject().put("chunkIndex", n))
                        }
                        "FINISH" -> {
                            if (id != session || proxy !== reply) { error(proxy, id, "INVALID_SESSION"); return@execute }
                            stream!!.close(); stream = null
                            if (received != expected || crc.value != checksum) {
                                reset(); error(proxy, id, "INTEGRITY_FAILURE"); return@execute
                            }
                            val file = temp!!
                            val result = PulseWavValidator.validate(file, expected)
                            if (!result.valid) {
                                reset(); error(proxy, id, "INVALID_WAV: ${result.reason}"); return@execute
                            }
                            val name = data.optString("fileName", "PulseNova")
                            if (android.os.Build.VERSION.SDK_INT < 29) {
                                legacyPending = true
                                generation++ // Invalidate pending transfer timeout while SAF picker is open
                                deadline = 0
                                main.post { if (!closed) requestLegacySave(name) }
                                return@execute
                            }
                            val uri = publish(file, name)
                            reset()
                            if (uri != null) respond(proxy, id, "COMPLETED", JSONObject().put("uri", uri.toString()))
                            else error(proxy, id, "SAVE_FAILED")
                        }
                        "CANCEL" -> {
                            if (id == session && proxy === reply) { reset(); respond(proxy, id, "CANCELLED") }
                        }
                        else -> error(proxy, id, "UNKNOWN_COMMAND")
                    }
                } catch (e: Exception) {
                    reset()
                    error(proxy, id, "TRANSFER_FAILED: ${e.javaClass.simpleName}")
                }
            }
        } catch (_: RejectedExecutionException) {}
    }

    fun finishLegacySave(uri: Uri?) {
        if (closed) return
        try { io.execute {
            if (!legacyPending || session.isEmpty()) return@execute
            val p = reply ?: return@execute
            val id = session
            if (uri == null) {
                reset(); respond(p, id, "CANCELLED"); return@execute
            }
            val success = try {
                val output = context.contentResolver.openOutputStream(uri, "w")
                    ?: throw IllegalStateException("Cannot open selected document")
                output.use { out -> temp!!.inputStream().use { it.copyTo(out) } }
                true
            } catch (_: Exception) { false }
            reset()
            if (success) respond(p, id, "COMPLETED", JSONObject().put("uri", uri.toString()))
            else error(p, id, "SAF_WRITE_FAILED")
        } } catch (_: RejectedExecutionException) {}
    }

    private fun publish(file: File, name: String): Uri? {
        // Android 8/9: SAF export requires an ActivityResult launcher; never write unpermissioned public paths.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val safe = name.take(60).replace(Regex("[^a-zA-Z0-9 _-]"), "_").ifBlank { "PulseNova" }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${safe}-${System.currentTimeMillis()}.wav")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return null
        return try {
            val output = resolver.openOutputStream(uri) ?: throw IllegalStateException("Cannot open MediaStore output")
            output.use { out -> file.inputStream().use { it.copyTo(out) } }
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            if (resolver.update(uri, done, null, null) != 1) throw IllegalStateException("Cannot publish pending media")
            uri
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }
    fun shutdown() {
        closed = true
        try { io.execute { reset() } } catch (_: RejectedExecutionException) {}
        io.shutdown()
    }
}
