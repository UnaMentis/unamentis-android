package com.unamentis.modules.knowledgebowl.data.model

/**
 * Domain-specific synonym dictionaries for Knowledge Bowl answer validation.
 *
 * Handles common abbreviations and alternative names across different
 * academic domains. Each dictionary maps a canonical form to a set of
 * equivalent terms.
 */
object KBSynonymDictionaries {

    /**
     * Place-related synonyms: country abbreviations, city nicknames,
     * geographic feature abbreviations.
     */
    val places: Map<String, Set<String>> = mapOf(
        "usa" to setOf("united states", "united states of america", "us", "america"),
        "uk" to setOf("united kingdom", "great britain", "britain", "england"),
        "uae" to setOf("united arab emirates"),
        "nyc" to setOf("new york city", "new york"),
        "la" to setOf("los angeles"),
        "sf" to setOf("san francisco"),
        "dc" to setOf("washington dc", "washington d.c.", "district of columbia"),
        "nz" to setOf("new zealand"),
        "ussr" to setOf("soviet union", "union of soviet socialist republics"),
        "prc" to setOf("peoples republic of china", "china"),
        "drc" to setOf("democratic republic of congo", "congo"),
        "mount" to setOf("mt"),
        "saint" to setOf("st"),
        "fort" to setOf("ft"),
        "lake" to setOf("lk"),
        "river" to setOf("riv"),
        "mountain" to setOf("mt", "mtn"),
    )

    /**
     * Scientific synonyms: chemical formulas, element symbols,
     * biological abbreviations.
     */
    val scientific: Map<String, Set<String>> = mapOf(
        "h2o" to setOf("water", "dihydrogen monoxide"),
        "co2" to setOf("carbon dioxide"),
        "o2" to setOf("oxygen", "dioxygen"),
        "h2" to setOf("hydrogen", "dihydrogen"),
        "n2" to setOf("nitrogen", "dinitrogen"),
        "nacl" to setOf("sodium chloride", "table salt", "salt"),
        "h2so4" to setOf("sulfuric acid"),
        "hcl" to setOf("hydrochloric acid"),
        "nh3" to setOf("ammonia"),
        "ch4" to setOf("methane"),
        "c6h12o6" to setOf("glucose"),
        "dna" to setOf("deoxyribonucleic acid"),
        "rna" to setOf("ribonucleic acid"),
        "atp" to setOf("adenosine triphosphate"),
        "co" to setOf("carbon monoxide"),
        "no2" to setOf("nitrogen dioxide"),
        "so2" to setOf("sulfur dioxide"),
        "caco3" to setOf("calcium carbonate"),
        "fe2o3" to setOf("iron oxide", "rust"),
        "au" to setOf("gold"),
        "ag" to setOf("silver"),
        "fe" to setOf("iron"),
        "cu" to setOf("copper"),
        "pb" to setOf("lead"),
        "hg" to setOf("mercury"),
        "k" to setOf("potassium"),
        "na" to setOf("sodium"),
        "ca" to setOf("calcium"),
        "mg" to setOf("magnesium"),
    )

    /**
     * Historical synonyms: war abbreviations, historical figure nicknames,
     * era designations, political entity abbreviations.
     */
    val historical: Map<String, Set<String>> = mapOf(
        "wwi" to setOf(
            "world war i", "world war one", "first world war",
            "great war", "world war 1",
        ),
        "wwii" to setOf(
            "world war ii", "world war two", "second world war",
            "world war 2",
        ),
        "usa" to setOf("united states", "united states of america", "us", "america"),
        "ussr" to setOf("soviet union", "union of soviet socialist republics"),
        "bc" to setOf("bce", "before common era", "before christ"),
        "ad" to setOf("ce", "common era", "anno domini"),
        "fdr" to setOf(
            "franklin delano roosevelt", "franklin roosevelt",
            "franklin d roosevelt",
        ),
        "jfk" to setOf("john f kennedy", "john fitzgerald kennedy"),
        "mlk" to setOf("martin luther king", "martin luther king jr"),
        "abe" to setOf("abraham lincoln", "lincoln"),
        "gw" to setOf("george washington", "washington"),
        "potus" to setOf("president of the united states", "president"),
        "scotus" to setOf("supreme court of the united states", "supreme court"),
        "nato" to setOf("north atlantic treaty organization"),
        "un" to setOf("united nations"),
        "eu" to setOf("european union"),
    )

    /**
     * Mathematics synonyms: constants, function names,
     * inverse trigonometric functions.
     */
    val mathematics: Map<String, Set<String>> = mapOf(
        "pi" to setOf("\u03C0", "3.14159", "3.14"),
        "e" to setOf("eulers number", "2.71828", "2.718"),
        "phi" to setOf("golden ratio", "\u03C6", "1.618"),
        "sqrt" to setOf("square root"),
        "cbrt" to setOf("cube root"),
        "log" to setOf("logarithm"),
        "ln" to setOf("natural logarithm", "natural log"),
        "sin" to setOf("sine"),
        "cos" to setOf("cosine"),
        "tan" to setOf("tangent"),
        "arcsin" to setOf("inverse sine", "asin"),
        "arccos" to setOf("inverse cosine", "acos"),
        "arctan" to setOf("inverse tangent", "atan"),
    )

    /**
     * Get the appropriate dictionary for a given answer type.
     *
     * @param answerType The type of answer being validated
     * @return The corresponding synonym dictionary, or null for types
     *         that don't use synonym matching
     */
    fun dictionaryForType(answerType: KBAnswerType): Map<String, Set<String>>? {
        return when (answerType) {
            KBAnswerType.PLACE -> places
            KBAnswerType.SCIENTIFIC -> scientific
            KBAnswerType.PERSON, KBAnswerType.TITLE -> historical
            KBAnswerType.NUMBER, KBAnswerType.NUMERIC, KBAnswerType.DATE -> mathematics
            KBAnswerType.TEXT, KBAnswerType.MULTIPLE_CHOICE -> null
        }
    }
}
