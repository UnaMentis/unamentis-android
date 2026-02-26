package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KBRegionalConfig].
 *
 * Tests regional configurations, computed properties, display formatting,
 * key differences between regions, and validation strictness mapping.
 */
class KBRegionalConfigTest {
    private val json = Json { ignoreUnknownKeys = true }

    // MARK: - forRegion Factory Tests

    @Test
    fun `forRegion returns config for each region`() {
        for (region in KBRegion.entries) {
            val config = KBRegionalConfig.forRegion(region)
            assertEquals(region, config.region)
        }
    }

    // MARK: - Colorado Configuration Tests

    @Test
    fun `Colorado has 3 teams per match`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals(3, config.teamsPerMatch)
    }

    @Test
    fun `Colorado has team size 1 to 4`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals(1, config.minTeamSize)
        assertEquals(4, config.maxTeamSize)
    }

    @Test
    fun `Colorado has 4 active players in oral`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals(4, config.activePlayersInOral)
    }

    @Test
    fun `Colorado has 60 written questions`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals(60, config.writtenQuestionCount)
    }

    @Test
    fun `Colorado has 15-minute written time limit`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals(900, config.writtenTimeLimitSeconds)
        assertEquals(15, config.writtenTimeLimitMinutes)
    }

    @Test
    fun `Colorado has 1 point per written question`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals(1, config.writtenPointsPerCorrect)
    }

    @Test
    fun `Colorado has 50 oral questions`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals(50, config.oralQuestionCount)
    }

    @Test
    fun `Colorado has 5 points per oral question`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals(5, config.oralPointsPerCorrect)
    }

    @Test
    fun `Colorado has rebound enabled`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertTrue(config.reboundEnabled)
    }

    @Test
    fun `Colorado has 15 second conference time`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals(15, config.conferenceTimeSeconds)
        assertEquals(15, config.conferenceTime)
    }

    @Test
    fun `Colorado prohibits verbal conferring`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertFalse(config.verbalConferringAllowed)
    }

    @Test
    fun `Colorado allows hand signals`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertTrue(config.handSignalsAllowed)
    }

    @Test
    fun `Colorado has no negative scoring`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertFalse(config.negativeScoring)
    }

    @Test
    fun `Colorado has no SOS bonus`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertFalse(config.sosBonus)
        assertFalse(config.hasSOS)
    }

    // MARK: - Colorado Springs Configuration Tests

    @Test
    fun `Colorado Springs matches Colorado on core rules`() {
        val co = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        val cs = KBRegionalConfig.forRegion(KBRegion.COLORADO_SPRINGS)

        assertEquals(co.teamsPerMatch, cs.teamsPerMatch)
        assertEquals(co.writtenQuestionCount, cs.writtenQuestionCount)
        assertEquals(co.writtenTimeLimitSeconds, cs.writtenTimeLimitSeconds)
        assertEquals(co.writtenPointsPerCorrect, cs.writtenPointsPerCorrect)
        assertEquals(co.oralQuestionCount, cs.oralQuestionCount)
        assertEquals(co.oralPointsPerCorrect, cs.oralPointsPerCorrect)
        assertEquals(co.verbalConferringAllowed, cs.verbalConferringAllowed)
        assertEquals(co.sosBonus, cs.sosBonus)
    }

    @Test
    fun `Colorado Springs has correct region`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO_SPRINGS)
        assertEquals(KBRegion.COLORADO_SPRINGS, config.region)
    }

    // MARK: - Minnesota Configuration Tests

    @Test
    fun `Minnesota has team size 3 to 6`() {
        val config = KBRegionalConfig.forRegion(KBRegion.MINNESOTA)
        assertEquals(3, config.minTeamSize)
        assertEquals(6, config.maxTeamSize)
    }

    @Test
    fun `Minnesota has 2 points per written question`() {
        val config = KBRegionalConfig.forRegion(KBRegion.MINNESOTA)
        assertEquals(2, config.writtenPointsPerCorrect)
    }

    @Test
    fun `Minnesota allows verbal conferring`() {
        val config = KBRegionalConfig.forRegion(KBRegion.MINNESOTA)
        assertTrue(config.verbalConferringAllowed)
    }

    @Test
    fun `Minnesota has SOS bonus`() {
        val config = KBRegionalConfig.forRegion(KBRegion.MINNESOTA)
        assertTrue(config.sosBonus)
        assertTrue(config.hasSOS)
    }

    // MARK: - Washington Configuration Tests

    @Test
    fun `Washington has 50 written questions`() {
        val config = KBRegionalConfig.forRegion(KBRegion.WASHINGTON)
        assertEquals(50, config.writtenQuestionCount)
    }

    @Test
    fun `Washington has 45-minute written time limit`() {
        val config = KBRegionalConfig.forRegion(KBRegion.WASHINGTON)
        assertEquals(2700, config.writtenTimeLimitSeconds)
        assertEquals(45, config.writtenTimeLimitMinutes)
    }

    @Test
    fun `Washington has team size 3 to 5`() {
        val config = KBRegionalConfig.forRegion(KBRegion.WASHINGTON)
        assertEquals(3, config.minTeamSize)
        assertEquals(5, config.maxTeamSize)
    }

    @Test
    fun `Washington allows verbal conferring`() {
        val config = KBRegionalConfig.forRegion(KBRegion.WASHINGTON)
        assertTrue(config.verbalConferringAllowed)
    }

    @Test
    fun `Washington has no SOS bonus`() {
        val config = KBRegionalConfig.forRegion(KBRegion.WASHINGTON)
        assertFalse(config.sosBonus)
        assertFalse(config.hasSOS)
    }

    // MARK: - validationStrictness Tests

    @Test
    fun `Colorado uses STRICT validation`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals(KBValidationStrictness.STRICT, config.validationStrictness)
    }

    @Test
    fun `Colorado Springs uses STRICT validation`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO_SPRINGS)
        assertEquals(KBValidationStrictness.STRICT, config.validationStrictness)
    }

    @Test
    fun `Minnesota uses STANDARD validation`() {
        val config = KBRegionalConfig.forRegion(KBRegion.MINNESOTA)
        assertEquals(KBValidationStrictness.STANDARD, config.validationStrictness)
    }

    @Test
    fun `Washington uses STANDARD validation`() {
        val config = KBRegionalConfig.forRegion(KBRegion.WASHINGTON)
        assertEquals(KBValidationStrictness.STANDARD, config.validationStrictness)
    }

    // MARK: - Display Property Tests

    @Test
    fun `writtenTimeLimitDisplay formats correctly for Colorado`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals("15 min", config.writtenTimeLimitDisplay)
    }

    @Test
    fun `writtenTimeLimitDisplay formats correctly for Washington`() {
        val config = KBRegionalConfig.forRegion(KBRegion.WASHINGTON)
        assertEquals("45 min", config.writtenTimeLimitDisplay)
    }

    @Test
    fun `conferenceTimeDisplay formats correctly`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals("15 sec", config.conferenceTimeDisplay)
    }

    @Test
    fun `writtenPointsDisplay formats singular correctly`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals("1 pt", config.writtenPointsDisplay)
    }

    @Test
    fun `writtenPointsDisplay formats plural correctly`() {
        val config = KBRegionalConfig.forRegion(KBRegion.MINNESOTA)
        assertEquals("2 pts", config.writtenPointsDisplay)
    }

    @Test
    fun `oralPointsDisplay formats correctly`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals("5 pts", config.oralPointsDisplay)
    }

    // MARK: - conferringRuleDescription Tests

    @Test
    fun `conferringRuleDescription for verbal allowed`() {
        val config = KBRegionalConfig.forRegion(KBRegion.MINNESOTA)
        assertEquals("Verbal discussion allowed", config.conferringRuleDescription)
    }

    @Test
    fun `conferringRuleDescription for hand signals only`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        assertEquals("Hand signals only (no verbal)", config.conferringRuleDescription)
    }

    @Test
    fun `conferringRuleDescription for no conferring`() {
        // Create a config with both verbal and hand signals disabled
        val config =
            KBRegionalConfig(
                region = KBRegion.COLORADO,
                teamsPerMatch = 3,
                minTeamSize = 1,
                maxTeamSize = 4,
                activePlayersInOral = 4,
                writtenQuestionCount = 60,
                writtenTimeLimitSeconds = 900,
                writtenPointsPerCorrect = 1,
                oralQuestionCount = 50,
                oralPointsPerCorrect = 5,
                reboundEnabled = true,
                conferenceTimeSeconds = 15,
                verbalConferringAllowed = false,
                handSignalsAllowed = false,
                negativeScoring = false,
                sosBonus = false,
            )

        assertEquals("No conferring", config.conferringRuleDescription)
    }

    // MARK: - keyDifferences Tests

    @Test
    fun `keyDifferences detects written question count difference`() {
        val colorado = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        val washington = KBRegionalConfig.forRegion(KBRegion.WASHINGTON)

        val diffs = colorado.keyDifferences(washington)
        assertTrue(diffs.any { it.contains("Written") && it.contains("questions") })
    }

    @Test
    fun `keyDifferences detects written time limit difference`() {
        val colorado = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        val washington = KBRegionalConfig.forRegion(KBRegion.WASHINGTON)

        val diffs = colorado.keyDifferences(washington)
        assertTrue(diffs.any { it.contains("Written time") })
    }

    @Test
    fun `keyDifferences detects written points difference`() {
        val colorado = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        val minnesota = KBRegionalConfig.forRegion(KBRegion.MINNESOTA)

        val diffs = colorado.keyDifferences(minnesota)
        assertTrue(diffs.any { it.contains("points") })
    }

    @Test
    fun `keyDifferences detects conferring difference`() {
        val colorado = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        val minnesota = KBRegionalConfig.forRegion(KBRegion.MINNESOTA)

        val diffs = colorado.keyDifferences(minnesota)
        assertTrue(diffs.any { it.contains("Conferring") })
    }

    @Test
    fun `keyDifferences detects SOS difference`() {
        val colorado = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        val minnesota = KBRegionalConfig.forRegion(KBRegion.MINNESOTA)

        val diffs = colorado.keyDifferences(minnesota)
        assertTrue(diffs.any { it.contains("SOS") })
    }

    @Test
    fun `keyDifferences returns empty for identical configs`() {
        val co1 = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        val co2 = KBRegionalConfig.forRegion(KBRegion.COLORADO)

        val diffs = co1.keyDifferences(co2)
        assertTrue(diffs.isEmpty())
    }

    @Test
    fun `keyDifferences Colorado vs Colorado Springs has no differences`() {
        val co = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        val cs = KBRegionalConfig.forRegion(KBRegion.COLORADO_SPRINGS)

        val diffs = co.keyDifferences(cs)
        assertTrue(diffs.isEmpty())
    }

    // MARK: - DEFAULT Tests

    @Test
    fun `DEFAULT is Colorado config`() {
        val defaultConfig = KBRegionalConfig.DEFAULT
        assertEquals(KBRegion.COLORADO, defaultConfig.region)
    }

    // MARK: - Serialization Tests

    @Test
    fun `serialization roundtrip works for all regions`() {
        for (region in KBRegion.entries) {
            val original = KBRegionalConfig.forRegion(region)
            val encoded = json.encodeToString(original)
            val decoded = json.decodeFromString<KBRegionalConfig>(encoded)

            assertEquals(original.region, decoded.region)
            assertEquals(original.writtenQuestionCount, decoded.writtenQuestionCount)
            assertEquals(original.writtenTimeLimitSeconds, decoded.writtenTimeLimitSeconds)
            assertEquals(original.writtenPointsPerCorrect, decoded.writtenPointsPerCorrect)
            assertEquals(original.verbalConferringAllowed, decoded.verbalConferringAllowed)
            assertEquals(original.sosBonus, decoded.sosBonus)
        }
    }

    @Test
    fun `serialized JSON uses snake_case keys`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        val encoded = json.encodeToString(config)

        assertTrue(encoded.contains("\"teams_per_match\""))
        assertTrue(encoded.contains("\"written_question_count\""))
        assertTrue(encoded.contains("\"oral_points_per_correct\""))
        assertTrue(encoded.contains("\"conference_time_seconds\""))
        assertTrue(encoded.contains("\"verbal_conferring_allowed\""))
    }
}
