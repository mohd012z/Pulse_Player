package com.pulseplayer.app.aipulse

/**
 * Backend-neutral generation contract. Register only engines actually installed and licensed.
 * An advertised backend is not evidence of working neural inference.
 */
object AiPulseGenerationRouter {
    enum class Kind { PROCEDURAL_OFFLINE, NEURAL_LOCAL, NEURAL_REMOTE }
    enum class Task { INSTRUMENTAL, VOCAL_SONG, SPEECH }
    data class Backend(
        val id: String,
        val kind: Kind,
        val supportedTasks: Set<Task>,
        val installed: Boolean,
        val licenseApproved: Boolean,
        val requiresNetwork: Boolean,
        val minimumRamMb: Int = 0
    )
    data class Request(
        val prompt: String,
        val task: Task,
        val offlineOnly: Boolean = false,
        val allowRemoteUpload: Boolean = false
    )
    data class Device(val availableRamMb: Int, val online: Boolean)
    data class Decision(val backend: Backend?, val reason: String)

    fun choose(request: Request, device: Device, backends: List<Backend>): Decision {
        if (request.prompt.isBlank()) return Decision(null, "Prompt is required")
        val candidates = backends.filter { backend ->
            backend.installed && backend.licenseApproved &&
                request.task in backend.supportedTasks &&
                backend.minimumRamMb <= device.availableRamMb &&
                (!backend.requiresNetwork || device.online) &&
                (!request.offlineOnly || !backend.requiresNetwork) &&
                (backend.kind != Kind.NEURAL_REMOTE || request.allowRemoteUpload)
        }
        val chosen = candidates.sortedWith(
            compareBy<Backend> {
                when (it.kind) {
                    Kind.NEURAL_LOCAL -> 0
                    Kind.PROCEDURAL_OFFLINE -> 1
                    Kind.NEURAL_REMOTE -> 2
                }
            }.thenBy { it.id }
        ).firstOrNull()
        return if (chosen == null) Decision(null, "No installed, licensed backend supports this request and device")
        else Decision(chosen, "Selected eligible backend: ${chosen.id}")
    }
}
