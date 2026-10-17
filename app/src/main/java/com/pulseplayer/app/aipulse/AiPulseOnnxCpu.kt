package com.pulseplayer.app.aipulse

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest

/**
 * Real CPU ONNX inference adapter. This is deliberately model-agnostic:
 * it can execute a verified one-input/one-output float32 model, but does NOT
 * pretend that an arbitrary model is a DiffSinger acoustic/vocoder pipeline.
 */
internal class AiPulseOnnxCpu(
    private val model: File,
    expectedSha256: String
) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        require(model.isFile && model.length() in 1..(512L * 1024 * 1024)) {
            "Missing or oversized ONNX model"
        }
        require(expectedSha256.matches(Regex("[a-fA-F0-9]{64}"))) { "SHA-256 required" }
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().use { input ->
            val bytes = ByteArray(65536)
            while (true) {
                val n = input.read(bytes)
                if (n < 0) break
                digest.update(bytes, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual.equals(expectedSha256, ignoreCase = true)) { "Model hash mismatch" }
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(2)
            session = environment.createSession(model.absolutePath, options)
        } finally {
            options.close()
        }
    }

    fun inputNames(): Set<String> = session.inputNames
    fun outputNames(): Set<String> = session.outputNames

    /** Execute float32 tensor inference using the selected model's actual input name. */
    fun runFloat(inputName: String, input: FloatArray, shape: LongArray): FloatArray {
        require(inputName in session.inputNames) { "Unknown model input" }
        require(input.isNotEmpty() && input.size <= 8_000_000) { "Invalid tensor size" }
        require(shape.isNotEmpty() && shape.all { it > 0 } &&
            shape.fold(1L) { a, v ->
                require(a <= input.size / v) { "Tensor shape exceeds input" }
                a * v
            } == input.size.toLong()) { "Tensor shape mismatch" }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                require(result.size() == 1) { "Expected one output; model adapter required" }
                val value = result[0].value
                val output = when (value) {
                    is Array<*> -> flatten(value)
                    is FloatArray -> value
                    else -> error("Unsupported output type: ${value.javaClass.simpleName}")
                }
                require(output.isNotEmpty() && output.size <= 20_000_000 &&
                    output.all { it.isFinite() }) { "Invalid ONNX output" }
                return output
            }
        }
    }

    private fun flatten(value: Any): FloatArray {
        val out = ArrayList<Float>()
        fun walk(item: Any?) {
            when (item) {
                is FloatArray -> for (x in item) out.add(x)
                is Array<*> -> item.forEach { walk(it) }
                else -> error("Non-float output tensor")
            }
            require(out.size <= 20_000_000) { "Output too large" }
        }
        walk(value)
        return out.toFloatArray()
    }

    override fun close() = session.close()
}
