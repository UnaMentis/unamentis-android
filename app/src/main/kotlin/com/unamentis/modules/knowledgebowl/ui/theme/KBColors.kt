package com.unamentis.modules.knowledgebowl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.unamentis.modules.knowledgebowl.data.model.KBDomain

/**
 * Knowledge Bowl color system - accessibility-first design.
 *
 * Design Principles:
 * - Never Color Alone: Always pair color with icon + text label
 * - Colorblind-Safe: Teal replaces green (success), Magenta replaces red (urgency/errors)
 * - Multiple Cues: Urgency uses color + animation + haptics
 * - Constructive Language: "Focus Area" instead of "Weakness"
 */
object KBColors {
    // MARK: - Domain Colors (Light Mode Primary / Dark Mode Adjusted)

    /** Science domain color - Deep blue */
    val scienceLight = Color(0xFF1B4F72)
    val scienceDark = Color(0xFF5DADE2)

    /** Mathematics domain color - Purple */
    val mathematicsLight = Color(0xFF5856D6)
    val mathematicsDark = Color(0xFF8B80F9)

    /** Literature domain color - Dark purple */
    val literatureLight = Color(0xFF6C3483)
    val literatureDark = Color(0xFFBB8FCE)

    /** History domain color - Brown */
    val historyLight = Color(0xFF784212)
    val historyDark = Color(0xFFDC7633)

    /** Social Studies domain color - Teal */
    val socialStudiesLight = Color(0xFF0D9488)
    val socialStudiesDark = Color(0xFF5EEAD4)

    /** Arts domain color - Magenta */
    val artsLight = Color(0xFFBE185D)
    val artsDark = Color(0xFFF472B6)

    /** Current Events domain color - Dark orange */
    val currentEventsLight = Color(0xFFB9770E)
    val currentEventsDark = Color(0xFFF7DC6F)

    /** Language domain color - Slate */
    val languageLight = Color(0xFF4A5568)
    val languageDark = Color(0xFFA0AEC0)

    /** Technology domain color - Dark teal */
    val technologyLight = Color(0xFF0E6655)
    val technologyDark = Color(0xFF76D7C4)

    /** Pop Culture domain color - Crimson */
    val popCultureLight = Color(0xFFC0392B)
    val popCultureDark = Color(0xFFF5B7B1)

    /** Religion/Philosophy domain color - Dark purple */
    val religionPhilosophyLight = Color(0xFF4A235A)
    val religionPhilosophyDark = Color(0xFFD7BDE2)

    /** Miscellaneous domain color - Olive */
    val miscellaneousLight = Color(0xFF9A7D0A)
    val miscellaneousDark = Color(0xFFF9E79F)

    // MARK: - Status/Mastery Colors

    /** Not started - Gray */
    val notStartedLight = Color(0xFF6B7280)
    val notStartedDark = Color(0xFF9CA3AF)

    /** Beginner level - Orange */
    val beginnerLight = Color(0xFFD97706)
    val beginnerDark = Color(0xFFFBBF24)

    /** Intermediate level - Blue */
    val intermediateLight = Color(0xFF2563EB)
    val intermediateDark = Color(0xFF60A5FA)

    /** Advanced level - Cyan */
    val advancedLight = Color(0xFF0891B2)
    val advancedDark = Color(0xFF22D3EE)

    /** Mastered - Teal (replaces green for colorblind safety) */
    val masteredLight = Color(0xFF0D9488)
    val masteredDark = Color(0xFF5EEAD4)

    // MARK: - Performance Colors (Constructive Language)

    /** Focus Area (formerly "Weak") - Magenta */
    val focusAreaLight = Color(0xFFBE185D)
    val focusAreaDark = Color(0xFFF472B6)

    /** Improving - Orange */
    val improvingLight = Color(0xFFD97706)
    val improvingDark = Color(0xFFFBBF24)

    /** Strong - Blue */
    val strongLight = Color(0xFF2563EB)
    val strongDark = Color(0xFF60A5FA)

    /** Excellent - Teal */
    val excellentLight = Color(0xFF0D9488)
    val excellentDark = Color(0xFF5EEAD4)

    // MARK: - Competition Colors

    /** Buzzer ready - Teal glow */
    val buzzerReadyLight = Color(0xFF0D9488)
    val buzzerReadyDark = Color(0xFF5EEAD4)

    /** Buzzer disabled - Gray */
    val buzzerDisabledLight = Color(0xFF6B7280)
    val buzzerDisabledDark = Color(0xFF9CA3AF)

    /** Buzzer locked - Orange */
    val buzzerLockedLight = Color(0xFFD97706)
    val buzzerLockedDark = Color(0xFFFBBF24)

    /** Timer normal - Blue */
    val timerNormalLight = Color(0xFF2563EB)
    val timerNormalDark = Color(0xFF60A5FA)

    /** Timer urgent (10-30% remaining) - Orange with pulse */
    val timerUrgentLight = Color(0xFFD97706)
    val timerUrgentDark = Color(0xFFFBBF24)

    /** Timer critical (<10% remaining) - Magenta with fast pulse */
    val timerCriticalLight = Color(0xFFBE185D)
    val timerCriticalDark = Color(0xFFF472B6)

    // MARK: - Achievement Colors

    /** Bronze tier */
    val bronze = Color(0xFFCD7F32)

    /** Silver tier */
    val silver = Color(0xFFC0C0C0)

    /** Gold tier (with glow effect) */
    val gold = Color(0xFFFFD700)

    /** Diamond tier (with glow effect) */
    val diamond = Color(0xFFB9F2FF)

    // MARK: - Background Colors

    /** Primary background */
    val bgPrimaryLight = Color(0xFFFFFFFF)
    val bgPrimaryDark = Color(0xFF1F2937)

    /** Secondary background */
    val bgSecondaryLight = Color(0xFFF3F4F6)
    val bgSecondaryDark = Color(0xFF111827)

    // MARK: - Text Colors

    /** Primary text */
    val textPrimaryLight = Color(0xFF1F2937)
    val textPrimaryDark = Color(0xFFF9FAFB)

    /** Secondary text */
    val textSecondaryLight = Color(0xFF6B7280)
    val textSecondaryDark = Color(0xFFD1D5DB)

    /** Muted text */
    val textMuted = Color(0xFF9CA3AF)

    // MARK: - Border Color

    /** Border color */
    val borderLight = Color(0xFFE5E7EB)
    val borderDark = Color(0xFF374151)
}

/**
 * Composable functions to get theme-aware colors.
 */
object KBTheme {
    @Composable
    fun science(): Color = if (isSystemInDarkTheme()) KBColors.scienceDark else KBColors.scienceLight

    @Composable
    fun mathematics(): Color = if (isSystemInDarkTheme()) KBColors.mathematicsDark else KBColors.mathematicsLight

    @Composable
    fun literature(): Color = if (isSystemInDarkTheme()) KBColors.literatureDark else KBColors.literatureLight

    @Composable
    fun history(): Color = if (isSystemInDarkTheme()) KBColors.historyDark else KBColors.historyLight

    @Composable
    fun socialStudies(): Color = if (isSystemInDarkTheme()) KBColors.socialStudiesDark else KBColors.socialStudiesLight

    @Composable
    fun arts(): Color = if (isSystemInDarkTheme()) KBColors.artsDark else KBColors.artsLight

    @Composable
    fun currentEvents(): Color = if (isSystemInDarkTheme()) KBColors.currentEventsDark else KBColors.currentEventsLight

    @Composable
    fun language(): Color = if (isSystemInDarkTheme()) KBColors.languageDark else KBColors.languageLight

    @Composable
    fun technology(): Color = if (isSystemInDarkTheme()) KBColors.technologyDark else KBColors.technologyLight

    @Composable
    fun popCulture(): Color = if (isSystemInDarkTheme()) KBColors.popCultureDark else KBColors.popCultureLight

    @Composable
    fun religionPhilosophy(): Color = if (isSystemInDarkTheme()) KBColors.religionPhilosophyDark else KBColors.religionPhilosophyLight

    @Composable
    fun miscellaneous(): Color = if (isSystemInDarkTheme()) KBColors.miscellaneousDark else KBColors.miscellaneousLight

    @Composable
    fun mastered(): Color = if (isSystemInDarkTheme()) KBColors.masteredDark else KBColors.masteredLight

    @Composable
    fun focusArea(): Color = if (isSystemInDarkTheme()) KBColors.focusAreaDark else KBColors.focusAreaLight

    @Composable
    fun intermediate(): Color = if (isSystemInDarkTheme()) KBColors.intermediateDark else KBColors.intermediateLight

    @Composable
    fun beginner(): Color = if (isSystemInDarkTheme()) KBColors.beginnerDark else KBColors.beginnerLight

    @Composable
    fun timerNormal(): Color = if (isSystemInDarkTheme()) KBColors.timerNormalDark else KBColors.timerNormalLight

    @Composable
    fun timerUrgent(): Color = if (isSystemInDarkTheme()) KBColors.timerUrgentDark else KBColors.timerUrgentLight

    @Composable
    fun timerCritical(): Color = if (isSystemInDarkTheme()) KBColors.timerCriticalDark else KBColors.timerCriticalLight

    @Composable
    fun bgPrimary(): Color = if (isSystemInDarkTheme()) KBColors.bgPrimaryDark else KBColors.bgPrimaryLight

    @Composable
    fun bgSecondary(): Color = if (isSystemInDarkTheme()) KBColors.bgSecondaryDark else KBColors.bgSecondaryLight

    @Composable
    fun textPrimary(): Color = if (isSystemInDarkTheme()) KBColors.textPrimaryDark else KBColors.textPrimaryLight

    @Composable
    fun textSecondary(): Color = if (isSystemInDarkTheme()) KBColors.textSecondaryDark else KBColors.textSecondaryLight

    @Composable
    fun border(): Color = if (isSystemInDarkTheme()) KBColors.borderDark else KBColors.borderLight

    /** Gold color for achievements */
    @Composable
    fun gold(): Color = KBColors.gold

    /** Bronze color for achievements */
    @Composable
    fun bronze(): Color = KBColors.bronze

    /** Silver color for achievements */
    @Composable
    fun silver(): Color = KBColors.silver
}

/**
 * Get the theme-aware color for a domain.
 */
@Composable
fun KBDomain.color(): Color =
    when (this) {
        KBDomain.SCIENCE -> KBTheme.science()
        KBDomain.MATHEMATICS -> KBTheme.mathematics()
        KBDomain.LITERATURE -> KBTheme.literature()
        KBDomain.HISTORY -> KBTheme.history()
        KBDomain.SOCIAL_STUDIES -> KBTheme.socialStudies()
        KBDomain.ARTS -> KBTheme.arts()
        KBDomain.CURRENT_EVENTS -> KBTheme.currentEvents()
        KBDomain.LANGUAGE -> KBTheme.language()
        KBDomain.TECHNOLOGY -> KBTheme.technology()
        KBDomain.POP_CULTURE -> KBTheme.popCulture()
        KBDomain.RELIGION_PHILOSOPHY -> KBTheme.religionPhilosophy()
        KBDomain.MISCELLANEOUS -> KBTheme.miscellaneous()
    }

/**
 * Get the icon name for a domain.
 */
fun KBDomain.iconName(): String =
    when (this) {
        KBDomain.SCIENCE -> "science"
        KBDomain.MATHEMATICS -> "functions"
        KBDomain.LITERATURE -> "auto_stories"
        KBDomain.HISTORY -> "history"
        KBDomain.SOCIAL_STUDIES -> "public"
        KBDomain.ARTS -> "palette"
        KBDomain.CURRENT_EVENTS -> "newspaper"
        KBDomain.LANGUAGE -> "text_format"
        KBDomain.TECHNOLOGY -> "computer"
        KBDomain.POP_CULTURE -> "star"
        KBDomain.RELIGION_PHILOSOPHY -> "auto_awesome"
        KBDomain.MISCELLANEOUS -> "help_outline"
    }

/**
 * Timer state for visual urgency feedback.
 */
enum class KBTimerState {
    NORMAL,
    FOCUSED,
    URGENT,
    CRITICAL,
    ;

    @Composable
    fun color(): Color =
        when (this) {
            NORMAL, FOCUSED -> KBTheme.timerNormal()
            URGENT -> KBTheme.timerUrgent()
            CRITICAL -> KBTheme.timerCritical()
        }

    val pulseSpeedMs: Long?
        get() =
            when (this) {
                NORMAL, FOCUSED -> null
                URGENT -> 1000L
                CRITICAL -> 300L
            }

    companion object {
        /**
         * Determine timer state from remaining percentage.
         */
        fun from(remainingPercent: Float): KBTimerState =
            when {
                remainingPercent >= 0.6f -> NORMAL
                remainingPercent >= 0.3f -> FOCUSED
                remainingPercent >= 0.1f -> URGENT
                else -> CRITICAL
            }
    }
}
