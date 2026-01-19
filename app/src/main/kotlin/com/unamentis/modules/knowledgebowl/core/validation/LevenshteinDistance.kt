package com.unamentis.modules.knowledgebowl.core.validation

/**
 * Levenshtein distance algorithm for fuzzy string matching.
 *
 * The Levenshtein distance is the minimum number of single-character edits
 * (insertions, deletions, or substitutions) required to change one word into another.
 */
object LevenshteinDistance {
    /**
     * Calculate the Levenshtein distance between two strings.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return The minimum number of edits required to transform s1 into s2
     */
    @Suppress("ReturnCount")
    fun calculate(
        s1: String,
        s2: String,
    ): Int {
        val m = s1.length
        val n = s2.length

        if (m == 0) return n
        if (n == 0) return m

        val s1Array = s1.toCharArray()
        val s2Array = s2.toCharArray()

        // Create a matrix of size (m+1) x (n+1)
        val matrix = Array(m + 1) { IntArray(n + 1) }

        // Initialize first column
        for (i in 0..m) {
            matrix[i][0] = i
        }

        // Initialize first row
        for (j in 0..n) {
            matrix[0][j] = j
        }

        // Fill in the rest of the matrix
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1Array[i - 1] == s2Array[j - 1]) 0 else 1
                // Calculate minimum of: deletion, insertion, substitution
                matrix[i][j] =
                    minOf(
                        matrix[i - 1][j] + 1,
                        matrix[i][j - 1] + 1,
                        matrix[i - 1][j - 1] + cost,
                    )
            }
        }

        return matrix[m][n]
    }

    /**
     * Calculate similarity as a ratio between 0.0 and 1.0.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Similarity ratio where 1.0 means identical and 0.0 means completely different
     */
    fun similarity(
        s1: String,
        s2: String,
    ): Float {
        val maxLength = maxOf(s1.length, s2.length)
        if (maxLength == 0) return 1.0f
        val distance = calculate(s1, s2)
        return 1.0f - (distance.toFloat() / maxLength)
    }

    /**
     * Check if two strings are within the given edit distance threshold.
     *
     * @param s1 First string
     * @param s2 Second string
     * @param threshold Maximum allowed edit distance
     * @return True if the strings are within the threshold
     */
    fun isWithinThreshold(
        s1: String,
        s2: String,
        threshold: Int,
    ): Boolean = calculate(s1, s2) <= threshold
}
