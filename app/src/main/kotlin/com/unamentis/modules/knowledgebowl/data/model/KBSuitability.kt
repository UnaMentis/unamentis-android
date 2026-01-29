package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.Serializable

/**
 * Flags indicating which round types a question is suitable for.
 *
 * @property forWritten Can be used in written (MCQ) round
 * @property forOral Can be used in oral (spoken) round
 * @property mcqPossible Can be converted to multiple choice format
 * @property requiresVisual Requires visual elements (diagrams, equations, maps)
 */
@Serializable
data class KBSuitability(
    val forWritten: Boolean = true,
    val forOral: Boolean = true,
    val mcqPossible: Boolean = true,
    val requiresVisual: Boolean = false,
) {
    companion object {
        /**
         * Default suitability - suitable for all round types.
         */
        val DEFAULT = KBSuitability()

        /**
         * Visual-only question (maps, diagrams, etc.).
         */
        val VISUAL_ONLY =
            KBSuitability(
                forWritten = true,
                forOral = false,
                mcqPossible = true,
                requiresVisual = true,
            )

        /**
         * Oral-only question (pronunciation, audio, etc.).
         */
        val ORAL_ONLY =
            KBSuitability(
                forWritten = false,
                forOral = true,
                mcqPossible = false,
                requiresVisual = false,
            )
    }
}
