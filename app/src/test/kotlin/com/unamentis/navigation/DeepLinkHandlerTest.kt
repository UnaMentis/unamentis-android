package com.unamentis.navigation

import android.content.Intent
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for DeepLinkHandler.
 *
 * Tests:
 * - URI parsing for all deep link routes
 * - Parameter extraction and validation
 * - Security (input sanitization, injection prevention)
 * - Edge cases (null, empty, malformed URIs)
 *
 * Uses Robolectric for Android URI parsing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeepLinkHandlerTest {
    private lateinit var deepLinkHandler: DeepLinkHandler

    @Before
    fun setup() {
        deepLinkHandler = DeepLinkHandler()
    }

    // ==================== Basic Route Tests ====================

    @Test
    fun `parseDeepLink returns Session for session route`() {
        val uri = Uri.parse("unamentis://session")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertEquals(DeepLinkDestination.Session, result)
    }

    @Test
    fun `parseDeepLink returns Curriculum for curriculum route`() {
        val uri = Uri.parse("unamentis://curriculum")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertEquals(DeepLinkDestination.Curriculum, result)
    }

    @Test
    fun `parseDeepLink returns Todo for todo route`() {
        val uri = Uri.parse("unamentis://todo")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertEquals(DeepLinkDestination.Todo, result)
    }

    @Test
    fun `parseDeepLink returns History for history route`() {
        val uri = Uri.parse("unamentis://history")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertEquals(DeepLinkDestination.History, result)
    }

    @Test
    fun `parseDeepLink returns Analytics for analytics route`() {
        val uri = Uri.parse("unamentis://analytics")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertEquals(DeepLinkDestination.Analytics, result)
    }

    @Test
    fun `parseDeepLink returns Settings for settings route`() {
        val uri = Uri.parse("unamentis://settings")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertEquals(DeepLinkDestination.Settings(), result)
    }

    // ==================== Parameterized Route Tests ====================

    @Test
    fun `parseDeepLink returns SessionStart with curriculum_id parameter`() {
        val uri = Uri.parse("unamentis://session/start?curriculum_id=abc123")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertTrue(result is DeepLinkDestination.SessionStart)
        assertEquals("abc123", (result as DeepLinkDestination.SessionStart).curriculumId)
        assertNull(result.topicId)
    }

    @Test
    fun `parseDeepLink returns SessionStart with both parameters`() {
        val uri = Uri.parse("unamentis://session/start?curriculum_id=abc123&topic_id=topic456")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertTrue(result is DeepLinkDestination.SessionStart)
        assertEquals("abc123", (result as DeepLinkDestination.SessionStart).curriculumId)
        assertEquals("topic456", result.topicId)
    }

    @Test
    fun `parseDeepLink returns SessionStart with no parameters`() {
        val uri = Uri.parse("unamentis://session/start")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertTrue(result is DeepLinkDestination.SessionStart)
        assertNull((result as DeepLinkDestination.SessionStart).curriculumId)
        assertNull(result.topicId)
    }

    @Test
    fun `parseDeepLink returns CurriculumDetail with valid id`() {
        val uri = Uri.parse("unamentis://curriculum/curriculum-id-123")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertTrue(result is DeepLinkDestination.CurriculumDetail)
        assertEquals("curriculum-id-123", (result as DeepLinkDestination.CurriculumDetail).id)
    }

    @Test
    fun `parseDeepLink returns HistoryDetail with valid id`() {
        val uri = Uri.parse("unamentis://history/session-id-456")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertTrue(result is DeepLinkDestination.HistoryDetail)
        assertEquals("session-id-456", (result as DeepLinkDestination.HistoryDetail).id)
    }

    @Test
    fun `parseDeepLink returns Settings with section parameter`() {
        val uri = Uri.parse("unamentis://settings?section=providers")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertTrue(result is DeepLinkDestination.Settings)
        assertEquals("providers", (result as DeepLinkDestination.Settings).section)
    }

    // ==================== Security Tests ====================

    @Test
    fun `parseDeepLink rejects SQL injection attempt in id`() {
        val uri = Uri.parse("unamentis://curriculum/'; DROP TABLE users; --")
        val result = deepLinkHandler.parseDeepLink(uri)
        // Should return Curriculum (no detail) because the ID has invalid characters
        assertEquals(DeepLinkDestination.Curriculum, result)
    }

    @Test
    fun `parseDeepLink rejects path traversal attempt`() {
        val uri = Uri.parse("unamentis://curriculum/../../../etc/passwd")
        val result = deepLinkHandler.parseDeepLink(uri)
        // Path traversal characters should be rejected
        assertEquals(DeepLinkDestination.Curriculum, result)
    }

    @Test
    fun `parseDeepLink rejects script injection in parameters`() {
        val uri = Uri.parse("unamentis://session/start?curriculum_id=<script>alert('xss')</script>")
        val result = deepLinkHandler.parseDeepLink(uri)
        // Script tags should be rejected, returning SessionStart with null curriculum
        assertTrue(result is DeepLinkDestination.SessionStart)
        assertNull((result as DeepLinkDestination.SessionStart).curriculumId)
    }

    @Test
    fun `parseDeepLink validates UUID format when expected`() {
        val validUuid = "123e4567-e89b-12d3-a456-426614174000"
        val uri = Uri.parse("unamentis://history/$validUuid")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertTrue(result is DeepLinkDestination.HistoryDetail)
        assertEquals(validUuid, (result as DeepLinkDestination.HistoryDetail).id)
    }

    @Test
    fun `parseDeepLink handles excessively long parameters`() {
        val longId = "a".repeat(1000)
        val uri = Uri.parse("unamentis://curriculum/$longId")
        val result = deepLinkHandler.parseDeepLink(uri)
        // Excessively long IDs should be rejected
        assertEquals(DeepLinkDestination.Curriculum, result)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `parseDeepLink returns Unknown for null URI`() {
        val result = deepLinkHandler.parseDeepLink(null)
        assertTrue(result is DeepLinkDestination.Unknown)
    }

    @Test
    fun `parseDeepLink returns Unknown for wrong scheme`() {
        val uri = Uri.parse("https://example.com/session")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertTrue(result is DeepLinkDestination.Unknown)
    }

    @Test
    fun `parseDeepLink returns Unknown for unknown path`() {
        val uri = Uri.parse("unamentis://nonexistent")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertTrue(result is DeepLinkDestination.Unknown)
    }

    @Test
    fun `parseDeepLink returns Unknown for empty host`() {
        val uri = Uri.parse("unamentis://")
        val result = deepLinkHandler.parseDeepLink(uri)
        assertTrue(result is DeepLinkDestination.Unknown)
    }

    // ==================== Intent Parsing Tests ====================

    @Test
    fun `parseFromIntent extracts URI from VIEW action`() {
        val uri = Uri.parse("unamentis://session")
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_VIEW
        every { intent.data } returns uri

        val result = deepLinkHandler.parseFromIntent(intent)
        assertEquals(DeepLinkDestination.Session, result)
    }

    @Test
    fun `parseFromIntent returns null for non-VIEW action`() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_MAIN
        every { intent.data } returns null

        val result = deepLinkHandler.parseFromIntent(intent)
        assertNull(result)
    }

    @Test
    fun `hasDeepLink returns true for valid deep link intent`() {
        val uri = Uri.parse("unamentis://session")
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_VIEW
        every { intent.data } returns uri

        assertTrue(deepLinkHandler.hasDeepLink(intent))
    }

    @Test
    fun `hasDeepLink returns false for null intent`() {
        assertFalse(deepLinkHandler.hasDeepLink(null))
    }

    @Test
    fun `hasDeepLink returns false for intent without data`() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_VIEW
        every { intent.data } returns null

        assertFalse(deepLinkHandler.hasDeepLink(intent))
    }

    // ==================== Route Generation Tests ====================

    @Test
    fun `DeepLinkDestination Session generates correct route`() {
        val route = DeepLinkDestination.Session.toNavigationRoute()
        assertEquals("session", route)
    }

    @Test
    fun `DeepLinkDestination SessionStart generates correct route with params`() {
        val destination = DeepLinkDestination.SessionStart("curr123", "topic456")
        val route = destination.toNavigationRoute()
        assertEquals("session/start?curriculum_id=curr123&topic_id=topic456", route)
    }

    @Test
    fun `DeepLinkDestination CurriculumDetail generates correct route`() {
        val destination = DeepLinkDestination.CurriculumDetail("curriculum123")
        val route = destination.toNavigationRoute()
        assertEquals("curriculum/curriculum123", route)
    }

    @Test
    fun `DeepLinkDestination Settings generates correct route with section`() {
        val destination = DeepLinkDestination.Settings("providers")
        val route = destination.toNavigationRoute()
        assertEquals("settings?section=providers", route)
    }

    @Test
    fun `DeepLinkDestination HistoryDetail generates correct route`() {
        val destination = DeepLinkDestination.HistoryDetail("session123")
        val route = destination.toNavigationRoute()
        assertEquals("history/session123", route)
    }

    @Test
    fun `DeepLinkDestination Settings without section generates correct route`() {
        val destination = DeepLinkDestination.Settings(null)
        val route = destination.toNavigationRoute()
        assertEquals("settings", route)
    }

    @Test
    fun `DeepLinkDestination SessionStart with only curriculum generates correct route`() {
        val destination = DeepLinkDestination.SessionStart("curr123", null)
        val route = destination.toNavigationRoute()
        assertEquals("session/start?curriculum_id=curr123", route)
    }
}
