package com.pulseplayer.app.aipulse

/**
 * Pure, deterministic editor for an existing Brain plan.
 * Reindexes bars on every edit; disallows gaps, overlapping sections,
 * non-four-bar durations and arrangements larger than the Brain limit.
 */
object AiPulseArrangementEditor {
    fun update(
        plan: AiPulseBrain.Plan,
        index: Int,
        section: AiPulseBrain.Section? = null,
        bars: Int? = null,
        energy: Double? = null
    ): AiPulseBrain.Plan {
        require(index in plan.segments.indices) { "Unknown section index" }
        val old = plan.segments[index]
        val replacement = old.copy(section = section ?: old.section, bars = bars ?: old.bars, energy = energy ?: old.energy)
        return rebuild(plan.copy(segments = plan.segments.toMutableList().also { it[index] = replacement }))
    }

    fun move(plan: AiPulseBrain.Plan, from: Int, to: Int): AiPulseBrain.Plan {
        require(from in plan.segments.indices && to in plan.segments.indices)
        val reordered = plan.segments.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return rebuild(plan.copy(segments = reordered))
    }

    fun insert(
        plan: AiPulseBrain.Plan,
        afterIndex: Int,
        section: AiPulseBrain.Section = AiPulseBrain.Section.CHORUS,
        bars: Int = 4
    ): AiPulseBrain.Plan {
        require(afterIndex in plan.segments.indices)
        require(bars in 4..32 && bars % 4 == 0)
        val template = plan.segments[afterIndex]
        val level = when (section) {
            AiPulseBrain.Section.INTRO -> 0.40
            AiPulseBrain.Section.VERSE -> 0.65
            AiPulseBrain.Section.CHORUS -> 0.95
            AiPulseBrain.Section.BRIDGE -> 0.55
            AiPulseBrain.Section.OUTRO -> 0.35
        }
        val sections = plan.segments.toMutableList()
        sections.add(afterIndex + 1, template.copy(
            section = section, bars = bars, energy = level
        ))
        return rebuild(plan.copy(segments = sections))
    }

    fun remove(plan: AiPulseBrain.Plan, index: Int): AiPulseBrain.Plan {
        require(index in plan.segments.indices)
        val sections = plan.segments.toMutableList()
        sections.removeAt(index)
        return rebuild(plan.copy(segments = sections))
    }

    fun rebuild(plan: AiPulseBrain.Plan): AiPulseBrain.Plan {
        require(plan.segments.isNotEmpty())
        require(plan.segments.all { it.bars > 0 && it.bars % 4 == 0 && it.energy.isFinite() && it.energy in 0.0..1.0 })
        val total = plan.segments.sumOf { it.bars }
        require(total in 8..128 && total % 4 == 0) { "Arrangement must contain 8–128 bars" }
        var cursor = 0
        val normalized = plan.segments.map { part ->
            part.copy(startBar = cursor).also { cursor += part.bars }
        }
        return plan.copy(segments = normalized)
    }
}
