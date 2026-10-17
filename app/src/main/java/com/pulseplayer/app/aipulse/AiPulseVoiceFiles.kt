package com.pulseplayer.app.aipulse

import java.io.File

/**
 * Resolves locally saved vocals without requesting broad storage permissions.
 * Only files in the app-private directory are considered. Imported WAV data is
 * fully validated at import and checked again before audio export.
 */
internal object AiPulseVoiceFiles {
    private const val RECORDING_PREFIX = "ai_pulse_voice_"
    private const val RECORDING_SUFFIX = ".m4a"
    const val IMPORTED_WAV = "ai_pulse_authorized_voice.wav"

    fun latestRecording(directory: File): File? =
        directory.listFiles()
            ?.asSequence()
            ?.filter {
                it.isFile &&
                    it.name.startsWith(RECORDING_PREFIX) &&
                    it.name.endsWith(RECORDING_SUFFIX) &&
                    it.length() > 0L
            }
            ?.maxWithOrNull(compareBy<File>({ it.lastModified() }, { it.name }))

    fun importedWav(directory: File): File? =
        File(directory, IMPORTED_WAV).takeIf { it.isFile && it.length() >= 44L }
}
