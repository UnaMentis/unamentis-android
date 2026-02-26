package com.unamentis.modules.knowledgebowl.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KBSynonymDictionaries].
 *
 * Validates dictionary contents, answer type mappings, and structural
 * integrity of all synonym dictionaries.
 */
class KBSynonymDictionariesTest {
    // MARK: - Places Dictionary Tests

    @Test
    fun `places dictionary is not empty`() {
        assertTrue(KBSynonymDictionaries.places.isNotEmpty())
    }

    @Test
    fun `places dictionary contains country abbreviations`() {
        assertNotNull(KBSynonymDictionaries.places["usa"])
        assertNotNull(KBSynonymDictionaries.places["uk"])
        assertNotNull(KBSynonymDictionaries.places["uae"])
        assertNotNull(KBSynonymDictionaries.places["nz"])
    }

    @Test
    fun `places dictionary contains city abbreviations`() {
        assertNotNull(KBSynonymDictionaries.places["nyc"])
        assertNotNull(KBSynonymDictionaries.places["la"])
        assertNotNull(KBSynonymDictionaries.places["sf"])
        assertNotNull(KBSynonymDictionaries.places["dc"])
    }

    @Test
    fun `places dictionary contains geographic prefix abbreviations`() {
        assertNotNull(KBSynonymDictionaries.places["mount"])
        assertNotNull(KBSynonymDictionaries.places["saint"])
        assertNotNull(KBSynonymDictionaries.places["fort"])
        assertNotNull(KBSynonymDictionaries.places["lake"])
    }

    @Test
    fun `places usa entry has all expected synonyms`() {
        val usaSynonyms = KBSynonymDictionaries.places["usa"]!!
        assertTrue(usaSynonyms.contains("united states"))
        assertTrue(usaSynonyms.contains("united states of america"))
        assertTrue(usaSynonyms.contains("us"))
        assertTrue(usaSynonyms.contains("america"))
    }

    @Test
    fun `places uk entry has all expected synonyms`() {
        val ukSynonyms = KBSynonymDictionaries.places["uk"]!!
        assertTrue(ukSynonyms.contains("united kingdom"))
        assertTrue(ukSynonyms.contains("great britain"))
        assertTrue(ukSynonyms.contains("britain"))
        assertTrue(ukSynonyms.contains("england"))
    }

    // MARK: - Scientific Dictionary Tests

    @Test
    fun `scientific dictionary is not empty`() {
        assertTrue(KBSynonymDictionaries.scientific.isNotEmpty())
    }

    @Test
    fun `scientific dictionary contains chemical formulas`() {
        assertNotNull(KBSynonymDictionaries.scientific["h2o"])
        assertNotNull(KBSynonymDictionaries.scientific["co2"])
        assertNotNull(KBSynonymDictionaries.scientific["nacl"])
        assertNotNull(KBSynonymDictionaries.scientific["h2so4"])
    }

    @Test
    fun `scientific dictionary contains element symbols`() {
        assertNotNull(KBSynonymDictionaries.scientific["au"])
        assertNotNull(KBSynonymDictionaries.scientific["ag"])
        assertNotNull(KBSynonymDictionaries.scientific["fe"])
        assertNotNull(KBSynonymDictionaries.scientific["cu"])
        assertNotNull(KBSynonymDictionaries.scientific["pb"])
        assertNotNull(KBSynonymDictionaries.scientific["hg"])
    }

    @Test
    fun `scientific dictionary contains biological abbreviations`() {
        assertNotNull(KBSynonymDictionaries.scientific["dna"])
        assertNotNull(KBSynonymDictionaries.scientific["rna"])
        assertNotNull(KBSynonymDictionaries.scientific["atp"])
    }

    @Test
    fun `scientific h2o entry has all expected synonyms`() {
        val h2oSynonyms = KBSynonymDictionaries.scientific["h2o"]!!
        assertTrue(h2oSynonyms.contains("water"))
        assertTrue(h2oSynonyms.contains("dihydrogen monoxide"))
    }

    @Test
    fun `scientific nacl entry has all expected synonyms`() {
        val naclSynonyms = KBSynonymDictionaries.scientific["nacl"]!!
        assertTrue(naclSynonyms.contains("sodium chloride"))
        assertTrue(naclSynonyms.contains("table salt"))
        assertTrue(naclSynonyms.contains("salt"))
    }

    // MARK: - Historical Dictionary Tests

    @Test
    fun `historical dictionary is not empty`() {
        assertTrue(KBSynonymDictionaries.historical.isNotEmpty())
    }

    @Test
    fun `historical dictionary contains war abbreviations`() {
        assertNotNull(KBSynonymDictionaries.historical["wwi"])
        assertNotNull(KBSynonymDictionaries.historical["wwii"])
    }

    @Test
    fun `historical dictionary contains president abbreviations`() {
        assertNotNull(KBSynonymDictionaries.historical["fdr"])
        assertNotNull(KBSynonymDictionaries.historical["jfk"])
        assertNotNull(KBSynonymDictionaries.historical["mlk"])
        assertNotNull(KBSynonymDictionaries.historical["abe"])
        assertNotNull(KBSynonymDictionaries.historical["gw"])
    }

    @Test
    fun `historical dictionary contains organization abbreviations`() {
        assertNotNull(KBSynonymDictionaries.historical["nato"])
        assertNotNull(KBSynonymDictionaries.historical["un"])
        assertNotNull(KBSynonymDictionaries.historical["eu"])
    }

    @Test
    fun `historical dictionary contains era designations`() {
        assertNotNull(KBSynonymDictionaries.historical["bc"])
        assertNotNull(KBSynonymDictionaries.historical["ad"])
    }

    @Test
    fun `historical wwii entry has all expected synonyms`() {
        val wwiiSynonyms = KBSynonymDictionaries.historical["wwii"]!!
        assertTrue(wwiiSynonyms.contains("world war ii"))
        assertTrue(wwiiSynonyms.contains("world war two"))
        assertTrue(wwiiSynonyms.contains("second world war"))
        assertTrue(wwiiSynonyms.contains("world war 2"))
    }

    @Test
    fun `historical bc entry maps to bce and before christ`() {
        val bcSynonyms = KBSynonymDictionaries.historical["bc"]!!
        assertTrue(bcSynonyms.contains("bce"))
        assertTrue(bcSynonyms.contains("before common era"))
        assertTrue(bcSynonyms.contains("before christ"))
    }

    // MARK: - Mathematics Dictionary Tests

    @Test
    fun `mathematics dictionary is not empty`() {
        assertTrue(KBSynonymDictionaries.mathematics.isNotEmpty())
    }

    @Test
    fun `mathematics dictionary contains constants`() {
        assertNotNull(KBSynonymDictionaries.mathematics["pi"])
        assertNotNull(KBSynonymDictionaries.mathematics["e"])
        assertNotNull(KBSynonymDictionaries.mathematics["phi"])
    }

    @Test
    fun `mathematics dictionary contains function names`() {
        assertNotNull(KBSynonymDictionaries.mathematics["sqrt"])
        assertNotNull(KBSynonymDictionaries.mathematics["cbrt"])
        assertNotNull(KBSynonymDictionaries.mathematics["log"])
        assertNotNull(KBSynonymDictionaries.mathematics["ln"])
    }

    @Test
    fun `mathematics dictionary contains trig functions`() {
        assertNotNull(KBSynonymDictionaries.mathematics["sin"])
        assertNotNull(KBSynonymDictionaries.mathematics["cos"])
        assertNotNull(KBSynonymDictionaries.mathematics["tan"])
    }

    @Test
    fun `mathematics dictionary contains inverse trig functions`() {
        assertNotNull(KBSynonymDictionaries.mathematics["arcsin"])
        assertNotNull(KBSynonymDictionaries.mathematics["arccos"])
        assertNotNull(KBSynonymDictionaries.mathematics["arctan"])
    }

    @Test
    fun `mathematics pi entry has unicode and decimal approximations`() {
        val piSynonyms = KBSynonymDictionaries.mathematics["pi"]!!
        assertTrue(piSynonyms.contains("\u03C0"))
        assertTrue(piSynonyms.contains("3.14159"))
        assertTrue(piSynonyms.contains("3.14"))
    }

    @Test
    fun `mathematics arcsin entry includes alternative names`() {
        val arcsinSynonyms = KBSynonymDictionaries.mathematics["arcsin"]!!
        assertTrue(arcsinSynonyms.contains("inverse sine"))
        assertTrue(arcsinSynonyms.contains("asin"))
    }

    // MARK: - dictionaryForType Tests

    @Test
    fun `dictionaryForType returns places for PLACE`() {
        assertEquals(KBSynonymDictionaries.places, KBSynonymDictionaries.dictionaryForType(KBAnswerType.PLACE))
    }

    @Test
    fun `dictionaryForType returns scientific for SCIENTIFIC`() {
        assertEquals(
            KBSynonymDictionaries.scientific,
            KBSynonymDictionaries.dictionaryForType(KBAnswerType.SCIENTIFIC),
        )
    }

    @Test
    fun `dictionaryForType returns historical for PERSON`() {
        assertEquals(
            KBSynonymDictionaries.historical,
            KBSynonymDictionaries.dictionaryForType(KBAnswerType.PERSON),
        )
    }

    @Test
    fun `dictionaryForType returns historical for TITLE`() {
        assertEquals(
            KBSynonymDictionaries.historical,
            KBSynonymDictionaries.dictionaryForType(KBAnswerType.TITLE),
        )
    }

    @Test
    fun `dictionaryForType returns mathematics for NUMBER`() {
        assertEquals(
            KBSynonymDictionaries.mathematics,
            KBSynonymDictionaries.dictionaryForType(KBAnswerType.NUMBER),
        )
    }

    @Test
    fun `dictionaryForType returns mathematics for NUMERIC`() {
        assertEquals(
            KBSynonymDictionaries.mathematics,
            KBSynonymDictionaries.dictionaryForType(KBAnswerType.NUMERIC),
        )
    }

    @Test
    fun `dictionaryForType returns mathematics for DATE`() {
        assertEquals(
            KBSynonymDictionaries.mathematics,
            KBSynonymDictionaries.dictionaryForType(KBAnswerType.DATE),
        )
    }

    @Test
    fun `dictionaryForType returns null for TEXT`() {
        assertNull(KBSynonymDictionaries.dictionaryForType(KBAnswerType.TEXT))
    }

    @Test
    fun `dictionaryForType returns null for MULTIPLE_CHOICE`() {
        assertNull(KBSynonymDictionaries.dictionaryForType(KBAnswerType.MULTIPLE_CHOICE))
    }

    // MARK: - Dictionary Structural Integrity

    @Test
    fun `all dictionary values are non-empty sets`() {
        for ((_, synonyms) in KBSynonymDictionaries.places) {
            assertTrue("Place synonym set should not be empty", synonyms.isNotEmpty())
        }
        for ((_, synonyms) in KBSynonymDictionaries.scientific) {
            assertTrue("Scientific synonym set should not be empty", synonyms.isNotEmpty())
        }
        for ((_, synonyms) in KBSynonymDictionaries.historical) {
            assertTrue("Historical synonym set should not be empty", synonyms.isNotEmpty())
        }
        for ((_, synonyms) in KBSynonymDictionaries.mathematics) {
            assertTrue("Mathematics synonym set should not be empty", synonyms.isNotEmpty())
        }
    }

    @Test
    fun `all dictionary keys are lowercase`() {
        for (key in KBSynonymDictionaries.places.keys) {
            assertEquals("Place key should be lowercase: $key", key.lowercase(), key)
        }
        for (key in KBSynonymDictionaries.scientific.keys) {
            assertEquals("Scientific key should be lowercase: $key", key.lowercase(), key)
        }
        for (key in KBSynonymDictionaries.historical.keys) {
            assertEquals("Historical key should be lowercase: $key", key.lowercase(), key)
        }
        for (key in KBSynonymDictionaries.mathematics.keys) {
            assertEquals("Mathematics key should be lowercase: $key", key.lowercase(), key)
        }
    }

    @Test
    fun `all dictionary values are lowercase`() {
        fun checkDictionary(
            name: String,
            dict: Map<String, Set<String>>,
        ) {
            for ((key, synonyms) in dict) {
                for (synonym in synonyms) {
                    // Skip unicode characters like pi symbol
                    if (synonym.all { it.isLetterOrDigit() || it.isWhitespace() || it == '.' }) {
                        assertEquals(
                            "$name synonym of '$key' should be lowercase: $synonym",
                            synonym.lowercase(),
                            synonym,
                        )
                    }
                }
            }
        }

        checkDictionary("Places", KBSynonymDictionaries.places)
        checkDictionary("Scientific", KBSynonymDictionaries.scientific)
        checkDictionary("Historical", KBSynonymDictionaries.historical)
        checkDictionary("Mathematics", KBSynonymDictionaries.mathematics)
    }
}
