package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Type of answer for specialized matching logic.
 *
 * Different answer types require different validation strategies
 * to handle variations in how answers can be expressed.
 */
@Serializable
enum class KBAnswerType {
    /** Generic text answer */
    @SerialName("text")
    TEXT,

    /** Person's name - handle first/last order, titles (Dr., Mr., etc.) */
    @SerialName("person")
    PERSON,

    /** Geographic location - handle "the", abbreviations (USA, US) */
    @SerialName("place")
    PLACE,

    /** Numeric answer - parse written numbers ("five" -> 5) */
    @SerialName("number")
    NUMBER,

    /** Numeric answer (alternative name used in some sources) */
    @SerialName("numeric")
    NUMERIC,

    /** Date answer - handle multiple formats (1776, July 4th 1776) */
    @SerialName("date")
    DATE,

    /** Book/movie/work title - handle "The" at beginning */
    @SerialName("title")
    TITLE,

    /** Scientific term - handle formulas, abbreviations (H2O, water) */
    @SerialName("scientific")
    SCIENTIFIC,

    /** Multiple choice answer (A, B, C, D) */
    @SerialName("multipleChoice")
    MULTIPLE_CHOICE,
    ;

    companion object {
        /**
         * Default answer type.
         */
        val DEFAULT = TEXT
    }
}
