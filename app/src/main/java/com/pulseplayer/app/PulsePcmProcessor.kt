package com.pulseplayer.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Stereo PCM16 processor. Runs inside Media3's sink (not on the UI thread).
 * Native DSP is deliberately bypassed for mono, multichannel and unsupported encodings.
 * Runtime toggles do not remove the processor from the chain.
 */
@UnstableApi
class PulsePcmProcessor : BaseAudioProcessor() {
    @Volatile var enabled: Boolean = false
    @Volatile var bassDb: Float = 0f
    @Volatile var clarityDb: Float = 0f
    @Volatile var safetyLimiterEnabled: Boolean = false
    @Volatile var dynamicsEnabled: Boolean = false
    private var dynamics: StereoDynamics? = null
    private var nativeHandle: Long = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount == 2) inputAudioFormat
        else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun onFlush() {
        releaseNative()
        dynamics = inputAudioFormat.sampleRate.takeIf { it in 8000..384000 }?.let(::StereoDynamics)
        if (inputAudioFormat.sampleRate > 0) {
            nativeHandle = runCatching { nativeCreate(inputAudioFormat.sampleRate.toDouble()) }.getOrDefault(0L)
        }
    }

    override fun onReset() { releaseNative(); dynamics = null }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val output = replaceOutputBuffer(bytes)
        // The input is normally frame-aligned; copy unaligned tails unchanged.
        if ((!enabled || nativeHandle == 0L) && !safetyLimiterEnabled && !dynamicsEnabled) {
            output.put(inputBuffer)
        } else {
            val sampleCount = bytes / 2
            val pcm = ShortArray(sampleCount)
            val view = inputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN)
            for (i in pcm.indices) pcm[i] = view.short
            if (enabled && nativeHandle != 0L && sampleCount >= 2) {
                nativeProcess(nativeHandle, pcm, sampleCount / 2, bassDb, clarityDb)
            }
            if ((safetyLimiterEnabled || dynamicsEnabled) && sampleCount >= 2) {
                dynamics?.process(pcm, sampleCount - sampleCount % 2,
                    compress = dynamicsEnabled, limit = safetyLimiterEnabled)
            }
            for (sample in pcm) {
                output.put((sample.toInt() and 255).toByte())
                output.put(((sample.toInt() ushr 8) and 255).toByte())
            }
            val tail = bytes - sampleCount * 2
            repeat(tail) { output.put(inputBuffer.get(inputBuffer.position() + sampleCount * 2 + it)) }
            inputBuffer.position(inputBuffer.limit())
        }
        output.flip()
    }

    private fun releaseNative() {
        if (nativeHandle != 0L) { nativeDestroy(nativeHandle); nativeHandle = 0L }
    }

    private external fun nativeCreate(sampleRate: Double): Long
    private external fun nativeProcess(handle: Long, pcm: ShortArray, frames: Int, bass: Float, clarity: Float)
    private external fun nativeDestroy(handle: Long)

    companion object {
        private val loaded = runCatching { System.loadLibrary("pulse_dsp") }.isSuccess
        fun available() = loaded
    }
}
