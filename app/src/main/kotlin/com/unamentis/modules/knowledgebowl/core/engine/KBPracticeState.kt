package com.unamentis.modules.knowledgebowl.core.engine

/**
 * State of the practice engine during a session.
 */
enum class KBPracticeState {
    /**
     * No session has started.
     */
    NOT_STARTED,

    /**
     * Session is actively in progress, waiting for answer.
     */
    IN_PROGRESS,

    /**
     * Showing the answer after an attempt.
     */
    SHOWING_ANSWER,

    /**
     * Session is paused.
     */
    PAUSED,

    /**
     * Session completed.
     */
    COMPLETED,
}
