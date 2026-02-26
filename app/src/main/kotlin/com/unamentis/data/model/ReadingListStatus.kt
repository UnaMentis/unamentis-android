package com.unamentis.data.model

/**
 * Status of a reading list item.
 */
enum class ReadingListStatus(val rawValue: String) {
    UNREAD("unread"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    ARCHIVED("archived"),
    ;

    /**
     * Whether the item is in an active (non-archived) state.
     */
    val isActive: Boolean
        get() = this != ARCHIVED

    /**
     * Whether the item can be played (has content ready for TTS).
     */
    val isPlayable: Boolean
        get() = this == IN_PROGRESS || this == COMPLETED

    /**
     * Sort priority (lower = higher priority in lists).
     */
    val sortPriority: Int
        get() =
            when (this) {
                IN_PROGRESS -> 0
                UNREAD -> 1
                COMPLETED -> 2
                ARCHIVED -> 3
            }

    companion object {
        fun fromRawValue(value: String): ReadingListStatus {
            return entries.firstOrNull { it.rawValue == value } ?: UNREAD
        }
    }
}

/**
 * Status of audio pre-generation for a reading list item.
 */
enum class AudioPreGenStatus(val rawValue: String) {
    NONE("none"),
    GENERATING("generating"),
    READY("ready"),
    FAILED("failed"),
    ;

    companion object {
        fun fromRawValue(value: String): AudioPreGenStatus {
            return entries.firstOrNull { it.rawValue == value } ?: NONE
        }
    }
}
