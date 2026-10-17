package com.pulseplayer.app.aipulse

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.pow

/**
 * Offline DiffSinger inference for the common token/duration/f0/speedup ONNX
 * signature. Only model packs with matching input/output signatures can run.
 * Phonemes must already be aligned to notes by a compatible phonemizer.
 */
internal class AiPulseDiffSinger(private val pack: AiPulseModelPack) : AutoCloseable {
    data class Phrase(
        val phonemeTokens: LongArray,
        val phonemeFrames: LongArray,
        val midiNotes: FloatArray,
        val speedup: Long = 10L
    )
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
    private val acoustic: OrtSession
    private val vocoder: OrtSession

    init {
        try {
            acoustic = env.createSession(pack.acoustic.absolutePath, options)
            try {
                vocoder = env.createSession(pack.vocoder.absolutePath, options)
            } catch (e: Exception) {
                acoustic.close()
                throw e
            }
        } finally { options.close() }
    }

    fun render(phrase: Phrase, output: File): File {
        val n = phrase.phonemeTokens.size
        require(n in 1..512 && phrase.phonemeFrames.size == n) { "Invalid phoneme sequence" }
        require(phrase.phonemeFrames.all { it in 1..2000 }) { "Invalid phoneme durations" }
        val frames = phrase.phonemeFrames.sum()
        require(frames in 1..100000 && phrase.midiNotes.size == frames.toInt()) {
            "F0 curve must match the total phoneme frame count"
        }
        require(phrase.speedup in 1..1000) { "Invalid diffusion speedup" }
        val f0 = FloatArray(frames.toInt()) { i ->
            val note = phrase.midiNotes[i]
            require(note.isFinite() && note in 0f..127f) { "Invalid MIDI note" }
            if (note == 0f) 0f else (440.0 * 2.0.pow((note - 69.0) / 12.0)).toFloat()
        }
        require(acoustic.inputNames == setOf("tokens", "durations", "f0", "speedup")) {
            "Acoustic model signature unsupported: ${acoustic.inputNames}"
        }
        require(vocoder.inputNames == setOf("mel", "f0")) {
            "Vocoder model signature unsupported: ${vocoder.inputNames}"
        }
        val inputs = linkedMapOf<String, OnnxTensor>()
        try {
            inputs["tokens"] = OnnxTensor.createTensor(env,
                LongBuffer.wrap(phrase.phonemeTokens), longArrayOf(1, n.toLong()))
            inputs["durations"] = OnnxTensor.createTensor(env,
                LongBuffer.wrap(phrase.phonemeFrames), longArrayOf(1, n.toLong()))
            inputs["f0"] = OnnxTensor.createTensor(env,
                FloatBuffer.wrap(f0), longArrayOf(1, frames))
            inputs["speedup"] = OnnxTensor.createTensor(env,
                LongBuffer.wrap(longArrayOf(phrase.speedup)), longArrayOf(1))
            acoustic.run(inputs).use { generated ->
                require(generated.size() == 1) { "Expected one mel output" }
                val mel = generated[0] as? OnnxTensor ?: error("Mel output is not a tensor")
                val shape = mel.info.shape
                require(shape.size == 3 && shape[0] == 1L &&
                    shape[1] == frames && shape[2] in 1..512) {
                    "Unexpected mel tensor shape: ${shape.contentToString()}"
                }
                val flattened = flattenFloat(mel.value)
                val melInputs = linkedMapOf<String, OnnxTensor>()
                try {
                    melInputs["mel"] = OnnxTensor.createTensor(env,
                        FloatBuffer.wrap(flattened), shape)
                    melInputs["f0"] = OnnxTensor.createTensor(env,
                        FloatBuffer.wrap(f0), longArrayOf(1, frames))
                    vocoder.run(melInputs).use { audio ->
                        require(audio.size() == 1) { "Expected one waveform output" }
                        val tensor = audio[0] as? OnnxTensor ?: error("Waveform is not a tensor")
                        val samples = flattenFloat(tensor.value)
                        require(samples.isNotEmpty() && samples.size <= 20_000_000 &&
                            samples.all { it.isFinite() }) { "Invalid neural waveform" }
                        return AiPulseOnnxWav.write(output, samples, pack.sampleRate)
                    }
                } finally { melInputs.values.forEach { it.close() } }
            }
        } finally { inputs.values.forEach { it.close() } }
    }

    private fun flattenFloat(value: Any): FloatArray {
        val out = ArrayList<Float>()
        fun collect(item: Any?) {
            when (item) {
                is FloatArray -> out.addAll(item.toList())
                is Array<*> -> item.forEach { collect(it) }
                else -> error("Unsupported float32 tensor")
            }
            require(out.size <= 20_000_000) { "Tensor too large" }
        }
        collect(value)
        return out.toFloatArray()
    }

    override fun close() {
        vocoder.close()
        acoustic.close()
    }
}
