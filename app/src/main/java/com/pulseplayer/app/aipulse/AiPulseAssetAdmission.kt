package com.pulseplayer.app.aipulse

/**
 * Gate for external reference assets before they enter the creative workspace.
 * No network fetching or copying occurs in this class.
 */
object AiPulseAssetAdmission {
    enum class IntendedUse { LISTEN, REFERENCE, REMIX, TRAIN_MODEL }
    data class Request(
        val source: AiPulseSourceGuard.Source,
        val use: IntendedUse,
        val commercialProject: Boolean,
        val userConfirmsRights: Boolean
    )
    data class Result(val accepted: Boolean, val explanation: String)

    fun assess(request: Request): Result {
        if (!request.userConfirmsRights) {
            return Result(false, "Confirm you have the necessary rights before importing")
        }
        val action = when (request.use) {
            IntendedUse.LISTEN, IntendedUse.REFERENCE -> AiPulseSourceGuard.Action.METADATA
            IntendedUse.REMIX -> AiPulseSourceGuard.Action.SAMPLE
            IntendedUse.TRAIN_MODEL -> AiPulseSourceGuard.Action.TRAIN
        }
        val decision = AiPulseSourceGuard.decide(request.source, action, request.commercialProject)
        val allowed = decision.verdict == AiPulseSourceGuard.Verdict.ALLOW ||
            decision.verdict == AiPulseSourceGuard.Verdict.ATTRIBUTION_REQUIRED
        // Reference mode is metadata-only: it does not authorize importing recording bytes.
        return Result(allowed, decision.reason)
    }
}
