package com.pulseplayer.app.aipulse

/**
 * Independent creative controls inspired by documented music-production workflows.
 * Does not call Suno or assume access to proprietary model internals.
 */
object AiPulseCreativeSettings {
    enum class Mode { SIMPLE, ADVANCED, SPEECH }
    enum class Voice { NONE, OWN_RECORDING, LICENSED_SYNTHETIC }
    data class Settings(
        val mode: Mode = Mode.SIMPLE,
        val description: String = "",
        val lyrics: String = "",
        val style: String = "",
        val exclusions: String = "",
        val instrumental: Boolean = true,
        val voice: Voice = Voice.NONE,
        val weirdness: Int = 50,
        val styleInfluence: Int = 50,
        val audioInfluence: Int = 0,
        val referenceAudioUri: String? = null,
        val referenceAuthorized: Boolean = false,
        val bpm: Int? = null,
        val durationSeconds: Int = 30,
        val seed: Long? = null
    )

    data class Plan(
        val task: AiPulseGenerationRouter.Task,
        val prompt: String,
        val negativePrompt: String,
        val guidance: Map<String, String>,
        val warnings: List<String>
    )

    fun plan(settings: Settings): Plan {
        require(settings.weirdness in 0..100) { "Weirdness must be 0–100" }
        require(settings.styleInfluence in 0..100) { "Style influence must be 0–100" }
        require(settings.audioInfluence in 0..100) { "Audio influence must be 0–100" }
        require(settings.durationSeconds in 1..480) { "Duration must be 1–480 seconds" }
        require(settings.bpm == null || settings.bpm in 40..240) { "BPM must be 40–240" }
        require(settings.referenceAudioUri == null || settings.referenceAuthorized) {
            "Reference audio requires confirmation of usage rights"
        }
        require(settings.referenceAudioUri != null || settings.audioInfluence == 0) {
            "Audio influence requires reference audio"
        }
        val task = when {
            settings.mode == Mode.SPEECH -> AiPulseGenerationRouter.Task.SPEECH
            settings.instrumental -> AiPulseGenerationRouter.Task.INSTRUMENTAL
            else -> AiPulseGenerationRouter.Task.VOCAL_SONG
        }
        val prompt = listOf(settings.description.trim(), settings.style.trim(),
            if (settings.mode == Mode.ADVANCED) settings.lyrics.trim() else "")
            .filter { it.isNotEmpty() }.joinToString("\n")
        require(prompt.isNotBlank()) { "Describe the requested audio" }
        val warnings = buildList {
            if (task == AiPulseGenerationRouter.Task.VOCAL_SONG && settings.voice == Voice.NONE)
                add("A singing-capable backend is required; voice identity is unspecified")
            if (settings.referenceAudioUri != null)
                add("Only send reference audio to an explicitly authorized backend")
        }
        return Plan(task, prompt, settings.exclusions.trim(), mapOf(
            "weirdness" to settings.weirdness.toString(),
            "styleInfluence" to settings.styleInfluence.toString(),
            "audioInfluence" to settings.audioInfluence.toString(),
            "durationSeconds" to settings.durationSeconds.toString(),
            "voice" to settings.voice.name,
            "bpm" to (settings.bpm?.toString() ?: "auto"),
            "seed" to (settings.seed?.toString() ?: "auto")
        ), warnings)
    }
}
