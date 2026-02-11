package com.unamentis.data.model

import android.util.Base64
import android.util.Log

/**
 * Parser for converting UMCF (Una Mentis Curriculum Format) documents
 * to the internal Curriculum model.
 *
 * This parser handles the UMCF 2.0 format returned by the server's
 * `/api/curricula/{id}/full-with-assets` endpoint.
 */
@Suppress("TooManyFunctions")
object UMCFParser {
    private const val TAG = "UMCFParser"

    private val TOPIC_NODE_TYPES = listOf("topic", "subtopic", "lesson")

    /**
     * Convert a UMCF document to the internal Curriculum model.
     *
     * @param document The UMCF document from the server
     * @param selectedTopicIds Optional set of topic IDs to include. If empty, includes all topics.
     * @return The converted Curriculum model
     */
    fun convertToCurriculum(
        document: UMCFDocument,
        selectedTopicIds: Set<String> = emptySet(),
    ): Curriculum {
        Log.d(TAG, "Converting UMCF document: ${document.title}")

        val topics = extractAllTopics(document, selectedTopicIds)
        val glossaryTerms = extractGlossaryTerms(document.glossary)
        val learningObjectives = extractTopLevelObjectives(document)

        Log.d(TAG, "Converted curriculum with ${topics.size} topics")

        return Curriculum(
            id = document.id.value,
            title = document.title,
            description = document.description ?: "",
            version = document.version.number,
            topics = topics,
            glossaryTerms = glossaryTerms,
            learningObjectives = learningObjectives,
            difficulty = document.educational?.difficulty,
            ageRange = document.educational?.typicalAgeRange,
            duration =
                document.educational?.typicalLearningTime
                    ?: document.educational?.estimatedDuration,
            keywords = document.metadata?.keywords ?: emptyList(),
        )
    }

    private fun extractAllTopics(
        document: UMCFDocument,
        selectedTopicIds: Set<String>,
    ): List<Topic> {
        val topics = mutableListOf<Topic>()
        var orderIndex = 0
        for (contentNode in document.content) {
            orderIndex =
                extractTopicsFromNode(
                    node = contentNode,
                    topics = topics,
                    startingIndex = orderIndex,
                    selectedTopicIds = selectedTopicIds,
                    parentObjectives = emptyList(),
                )
        }
        return topics
    }

    private fun extractGlossaryTerms(glossary: UMCFGlossary?): List<GlossaryTerm> =
        glossary?.terms?.map { term ->
            GlossaryTerm(
                term = term.term,
                definition = term.definition ?: "",
                examples = term.examples ?: emptyList(),
            )
        } ?: emptyList()

    private fun extractTopLevelObjectives(document: UMCFDocument): List<String> =
        document.content.flatMap { node ->
            node.learningObjectives?.map { it.statement } ?: emptyList()
        }

    /**
     * Recursively extract topics from a content node and its children.
     */
    @Suppress("LongParameterList")
    private fun extractTopicsFromNode(
        node: UMCFContentNode,
        topics: MutableList<Topic>,
        startingIndex: Int,
        selectedTopicIds: Set<String>,
        parentObjectives: List<String>,
    ): Int {
        var currentIndex = startingIndex
        val nodeIdValue = node.id.value
        val isTopicNode = node.type in TOPIC_NODE_TYPES
        val shouldInclude = selectedTopicIds.isEmpty() || selectedTopicIds.contains(nodeIdValue)

        if (isTopicNode && shouldInclude) {
            val topic = createTopicFromNode(node, nodeIdValue, currentIndex, parentObjectives)
            topics.add(topic)
            currentIndex++
            Log.d(TAG, "Extracted topic: ${node.title} with ${topic.transcript.size} segments")
        }

        currentIndex = processChildNodes(node, topics, currentIndex, selectedTopicIds, parentObjectives)
        return currentIndex
    }

    private fun createTopicFromNode(
        node: UMCFContentNode,
        nodeIdValue: String,
        orderIndex: Int,
        parentObjectives: List<String>,
    ): Topic {
        val transcriptSegments = extractTranscriptSegments(node.transcript)
        val visualAssets = extractVisualAssets(node.media)
        val objectives = combineObjectives(parentObjectives, node.learningObjectives)

        return Topic(
            id = nodeIdValue,
            title = node.title,
            orderIndex = orderIndex,
            transcript = transcriptSegments,
            learningObjectives = objectives,
            documents = emptyList(),
            visualAssets = visualAssets,
            description = node.description,
            duration = node.timeEstimates?.intermediate ?: node.timeEstimates?.overview,
        )
    }

    private fun extractTranscriptSegments(transcript: UMCFTranscript?): List<TranscriptSegment> =
        transcript?.segments?.map { seg ->
            TranscriptSegment(
                id = seg.id,
                type = seg.type,
                content = seg.content,
                spokenText = null,
                stoppingPoint = extractStoppingPoint(seg),
                visualAssetId = null,
            )
        } ?: emptyList()

    private fun extractStoppingPoint(seg: UMCFTranscriptSegment): StoppingPoint? =
        seg.stoppingPoint?.let { sp ->
            StoppingPoint(
                type = sp.type ?: "natural",
                prompt = sp.suggestedPrompt ?: "",
                expectedConcepts = emptyList(),
                hints = emptyList(),
            )
        } ?: seg.checkpoint?.let { cp ->
            StoppingPoint(
                type = cp.type ?: "comprehension_check",
                prompt = cp.question ?: cp.prompt ?: "",
                expectedConcepts = cp.expectedResponsePatterns ?: emptyList(),
                hints = emptyList(),
            )
        }

    private fun extractVisualAssets(media: UMCFMediaCollection?): List<VisualAsset> {
        val assets = mutableListOf<VisualAsset>()
        media?.embedded?.forEach { asset -> assets.add(convertMediaAsset(asset)) }
        media?.reference?.forEach { asset -> assets.add(convertMediaAsset(asset)) }
        return assets
    }

    private fun combineObjectives(
        parentObjectives: List<String>,
        nodeObjectives: List<UMCFLearningObjective>?,
    ): List<String> {
        val objectives = parentObjectives.toMutableList()
        nodeObjectives?.forEach { obj -> objectives.add(obj.statement) }
        return objectives
    }

    private fun processChildNodes(
        node: UMCFContentNode,
        topics: MutableList<Topic>,
        startingIndex: Int,
        selectedTopicIds: Set<String>,
        inheritedObjectives: List<String>,
    ): Int {
        var currentIndex = startingIndex
        node.children?.forEach { child ->
            val nodeObjectives =
                inheritedObjectives + (node.learningObjectives?.map { it.statement } ?: emptyList())
            currentIndex =
                extractTopicsFromNode(
                    node = child,
                    topics = topics,
                    startingIndex = currentIndex,
                    selectedTopicIds = selectedTopicIds,
                    parentObjectives = nodeObjectives,
                )
        }
        return currentIndex
    }

    private fun convertMediaAsset(asset: UMCFMediaAsset): VisualAsset =
        VisualAsset(
            id = asset.id,
            filename = asset.title ?: asset.id,
            mimeType = asset.mimeType ?: guessMimeType(asset.type),
            url = asset.url ?: asset.localPath,
            caption = asset.caption ?: asset.alt,
        )

    private fun guessMimeType(type: String): String =
        when (type.lowercase()) {
            "image" -> "image/png"
            "diagram" -> "image/svg+xml"
            "equation" -> "image/svg+xml"
            "chart" -> "image/png"
            "slideimage" -> "image/png"
            "slidedeck" -> "application/pdf"
            else -> "application/octet-stream"
        }

    /**
     * Extract asset IDs that belong to specific topics.
     *
     * @param document The UMCF document
     * @param topicIds Set of topic IDs to get assets for (empty = all)
     * @return Set of asset IDs
     */
    fun getAssetIdsForTopics(
        document: UMCFDocument,
        topicIds: Set<String>,
    ): Set<String> {
        val assetIds = mutableSetOf<String>()

        fun collectFromNode(node: UMCFContentNode) {
            val shouldInclude = topicIds.isEmpty() || topicIds.contains(node.id.value)
            if (shouldInclude) {
                node.media?.embedded?.forEach { asset -> assetIds.add(asset.id) }
                node.media?.reference?.forEach { asset -> assetIds.add(asset.id) }
            }
            node.children?.forEach { child -> collectFromNode(child) }
        }

        document.content.forEach { node -> collectFromNode(node) }
        return assetIds
    }

    /**
     * Decode base64 asset data.
     *
     * @param base64String The base64 encoded string
     * @return Decoded bytes or null if decoding fails
     */
    fun decodeAssetData(base64String: String): ByteArray? =
        try {
            Base64.decode(base64String, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode asset data: ${e.message}")
            null
        }
}
