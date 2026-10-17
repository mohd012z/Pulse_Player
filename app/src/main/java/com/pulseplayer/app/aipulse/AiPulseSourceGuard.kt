package com.pulseplayer.app.aipulse

/**
 * Permission policy for reference discovery. An absent license is NOT permission.
 * Separate metadata discovery from downloading, sampling, redistribution and AI training.
 */
object AiPulseSourceGuard {
    enum class License { CC0, CC_BY, CC_BY_SA, CC_BY_NC, PUBLIC_DOMAIN, PROPRIETARY, UNKNOWN }
    enum class Action { METADATA, SAMPLE, DISTRIBUTE, TRAIN }
    enum class Verdict { ALLOW, ATTRIBUTION_REQUIRED, REVIEW, DENY }
    data class Source(
        val provider: String,
        val sourceUrl: String,
        val license: License,
        val attribution: String = "",
        val aiTrainingAllowed: Boolean? = null,
        val explicitPermission: Boolean = false
    )
    data class Decision(val verdict: Verdict, val reason: String)

    fun decide(source: Source, action: Action, commercial: Boolean = false): Decision {
        if (!source.sourceUrl.startsWith("https://"))
            return Decision(Verdict.DENY, "Only HTTPS sources are accepted")
        if (action == Action.METADATA)
            return Decision(Verdict.ALLOW, "Metadata discovery only; follow provider API terms")
        if (source.license == License.UNKNOWN)
            return Decision(Verdict.REVIEW, "Missing license: do not download or reuse until verified")
        if (source.license == License.PROPRIETARY)
            return if (source.explicitPermission) Decision(Verdict.REVIEW, "Verify scope of written permission")
                else Decision(Verdict.DENY, "Proprietary media requires permission")
        if (commercial && source.license == License.CC_BY_NC)
            return Decision(Verdict.DENY, "Noncommercial license prohibits commercial reuse")
        if (action == Action.TRAIN) {
            if (source.aiTrainingAllowed != true)
                return Decision(Verdict.DENY, "AI training not explicitly authorized")
            if (source.license == License.CC_BY_NC && commercial)
                return Decision(Verdict.DENY, "Noncommercial source")
        }
        if (source.license in setOf(License.CC_BY, License.CC_BY_SA, License.CC_BY_NC)) {
            if (source.attribution.isBlank())
                return Decision(Verdict.REVIEW, "Attribution and license details required")
            return Decision(Verdict.ATTRIBUTION_REQUIRED, "Preserve creator, license and source URL; check share-alike obligations")
        }
        return Decision(Verdict.ALLOW, "Check provider terms and provenance before accessing media")
    }
}
