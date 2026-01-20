package com.unamentis.core.export

import com.unamentis.data.model.Session
import com.unamentis.data.model.TranscriptEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles exporting session data to various formats.
 *
 * Supports JSON, plain text, Markdown, and CSV formats for sharing
 * session transcripts and metadata.
 */
@Singleton
class SessionExporter
    @Inject
    constructor() {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        private val filenameDateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())

        /**
         * Exports a session with its transcript to the specified format.
         *
         * @param session The session to export
         * @param transcript The transcript entries for the session
         * @param format The desired export format
         * @return ExportResult indicating success with content or an error
         */
        fun export(
            session: Session,
            transcript: List<TranscriptEntry>,
            format: ExportFormat,
        ): ExportResult =
            try {
                val content =
                    when (format) {
                        ExportFormat.JSON -> exportToJson(session, transcript)
                        ExportFormat.TEXT -> exportToText(session, transcript)
                        ExportFormat.MARKDOWN -> exportToMarkdown(session, transcript)
                        ExportFormat.CSV -> exportToCsv(session, transcript)
                    }

                val filename = generateFilename(session, format)
                ExportResult.Success(content, format, filename)
            } catch (e: Exception) {
                ExportResult.Error("Export failed: ${e.message}")
            }

        private fun exportToJson(
            session: Session,
            transcript: List<TranscriptEntry>,
        ): String =
            buildString {
                appendLine("{")
                appendLine("  \"session\": {")
                appendLine("    \"id\": \"${escapeJson(session.id)}\",")
                appendLine("    \"curriculumId\": ${session.curriculumId?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
                appendLine("    \"topicId\": ${session.topicId?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
                appendLine("    \"startTime\": ${session.startTime},")
                appendLine("    \"startTimeFormatted\": \"${dateFormat.format(Date(session.startTime))}\",")
                appendLine("    \"endTime\": ${session.endTime ?: "null"},")
                session.endTime?.let {
                    appendLine("    \"endTimeFormatted\": \"${dateFormat.format(Date(it))}\",")
                    appendLine("    \"durationMinutes\": ${(it - session.startTime) / 60000},")
                }
                appendLine("    \"turnCount\": ${session.turnCount}")
                appendLine("  },")
                appendLine("  \"transcript\": [")
                transcript.forEachIndexed { index, entry ->
                    appendLine("    {")
                    appendLine("      \"id\": \"${escapeJson(entry.id)}\",")
                    appendLine("      \"role\": \"${escapeJson(entry.role)}\",")
                    appendLine("      \"text\": \"${escapeJson(entry.text)}\",")
                    appendLine("      \"timestamp\": ${entry.timestamp},")
                    appendLine("      \"timestampFormatted\": \"${timeFormat.format(Date(entry.timestamp))}\"")
                    append("    }")
                    if (index < transcript.size - 1) {
                        appendLine(",")
                    } else {
                        appendLine()
                    }
                }
                appendLine("  ]")
                append("}")
            }

        private fun exportToText(
            session: Session,
            transcript: List<TranscriptEntry>,
        ): String =
            buildString {
                appendLine("UnaMentis Session Export")
                appendLine("=".repeat(50))
                appendLine()
                appendLine("Session Information")
                appendLine("-".repeat(50))
                appendLine("Session ID: ${session.id}")
                session.curriculumId?.let { appendLine("Curriculum: $it") }
                session.topicId?.let { appendLine("Topic: $it") }
                appendLine("Started: ${dateFormat.format(Date(session.startTime))}")
                session.endTime?.let {
                    appendLine("Ended: ${dateFormat.format(Date(it))}")
                    val durationMinutes = (it - session.startTime) / 60000
                    appendLine("Duration: $durationMinutes minutes")
                }
                appendLine("Turns: ${session.turnCount}")
                appendLine()
                appendLine("Transcript")
                appendLine("-".repeat(50))
                appendLine()

                transcript.forEach { entry ->
                    val role = if (entry.role == "user") "You" else "AI Tutor"
                    val timestamp = timeFormat.format(Date(entry.timestamp))
                    appendLine("[$timestamp] $role:")
                    appendLine(entry.text)
                    appendLine()
                }
            }

        private fun exportToMarkdown(
            session: Session,
            transcript: List<TranscriptEntry>,
        ): String =
            buildString {
                appendLine("# UnaMentis Session Export")
                appendLine()
                appendLine("## Session Information")
                appendLine()
                appendLine("| Property | Value |")
                appendLine("|----------|-------|")
                appendLine("| Session ID | `${session.id}` |")
                session.curriculumId?.let { appendLine("| Curriculum | $it |") }
                session.topicId?.let { appendLine("| Topic | $it |") }
                appendLine("| Started | ${dateFormat.format(Date(session.startTime))} |")
                session.endTime?.let {
                    appendLine("| Ended | ${dateFormat.format(Date(it))} |")
                    val durationMinutes = (it - session.startTime) / 60000
                    appendLine("| Duration | $durationMinutes minutes |")
                }
                appendLine("| Turns | ${session.turnCount} |")
                appendLine()
                appendLine("## Transcript")
                appendLine()

                transcript.forEach { entry ->
                    val role = if (entry.role == "user") "**You**" else "**AI Tutor**"
                    val timestamp = timeFormat.format(Date(entry.timestamp))
                    appendLine("### $role ($timestamp)")
                    appendLine()
                    appendLine(entry.text)
                    appendLine()
                }
            }

        private fun exportToCsv(
            _session: Session,
            transcript: List<TranscriptEntry>,
        ): String =
            buildString {
                // Header
                appendLine("timestamp,role,text")

                // Data rows
                transcript.forEach { entry ->
                    val timestamp = dateFormat.format(Date(entry.timestamp))
                    val role = if (entry.role == "user") "User" else "AI Tutor"
                    val text = escapeCsv(entry.text)
                    appendLine("$timestamp,$role,$text")
                }
            }

        private fun generateFilename(
            session: Session,
            format: ExportFormat,
        ): String {
            val dateStr = filenameDateFormat.format(Date(session.startTime))
            val prefix =
                session.topicId?.take(20)?.replace(Regex("[^a-zA-Z0-9-_]"), "_")
                    ?: "session"
            return "unamentis_${prefix}_$dateStr.${format.extension}"
        }

        private fun escapeJson(value: String): String =
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")

        private fun escapeCsv(value: String): String {
            val needsQuoting =
                value.contains(",") ||
                    value.contains("\"") ||
                    value.contains("\n") ||
                    value.contains("\r")

            return if (needsQuoting) {
                "\"${value.replace("\"", "\"\"")}\""
            } else {
                value
            }
        }
    }
