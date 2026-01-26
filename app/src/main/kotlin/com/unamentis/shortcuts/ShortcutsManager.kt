package com.unamentis.shortcuts

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.unamentis.MainActivity
import com.unamentis.R
import com.unamentis.navigation.DeepLinkRoutes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages app shortcuts for Google Assistant integration and launcher shortcuts.
 *
 * Provides:
 * - Dynamic shortcuts for quick actions
 * - Shortcut info for Google Assistant App Actions
 * - Static shortcut management
 *
 * Shortcuts:
 * - Start Session: Begin a new learning session
 * - Resume Learning: Continue the last session
 * - Show Progress: View analytics dashboard
 * - View To-Dos: Check to-do list
 */
@Singleton
class ShortcutsManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            const val SHORTCUT_START_SESSION = "shortcut_start_session"
            const val SHORTCUT_RESUME_LEARNING = "shortcut_resume_learning"
            const val SHORTCUT_SHOW_PROGRESS = "shortcut_show_progress"
            const val SHORTCUT_VIEW_TODOS = "shortcut_view_todos"

            // Maximum number of shortcuts we can push
            const val MAX_SHORTCUTS = 4
        }

        /**
         * Initialize and publish all dynamic shortcuts.
         * Call this on app startup.
         *
         * Note: Shortcuts are already defined as static shortcuts in res/xml/shortcuts.xml.
         * We only push dynamic shortcuts if they don't conflict with manifest shortcuts.
         * On API 36+, manifest shortcuts cannot be manipulated via APIs.
         */
        fun publishShortcuts() {
            // Get existing manifest shortcut IDs to avoid conflicts
            val manifestShortcutIds =
                ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_MANIFEST)
                    .map { it.id }
                    .toSet()

            // Only create dynamic shortcuts for IDs not already in manifest
            val shortcuts =
                listOf(
                    createStartSessionShortcut(),
                    createResumeShortcut(),
                    createProgressShortcut(),
                    createTodosShortcut(),
                ).filter { it.id !in manifestShortcutIds }

            // Only set dynamic shortcuts if we have any that don't conflict
            if (shortcuts.isNotEmpty()) {
                ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
            }
        }

        /**
         * Create shortcut for starting a new session.
         */
        private fun createStartSessionShortcut(): ShortcutInfoCompat {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse(DeepLinkRoutes.URI_SESSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

            return ShortcutInfoCompat.Builder(context, SHORTCUT_START_SESSION)
                .setShortLabel(context.getString(R.string.shortcut_start_session_short))
                .setLongLabel(context.getString(R.string.shortcut_start_session_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_mic))
                .setIntent(intent)
                .setRank(0)
                .build()
        }

        /**
         * Create shortcut for resuming learning.
         */
        private fun createResumeShortcut(): ShortcutInfoCompat {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse(DeepLinkRoutes.URI_SESSION)
                    putExtra("resume", true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

            return ShortcutInfoCompat.Builder(context, SHORTCUT_RESUME_LEARNING)
                .setShortLabel(context.getString(R.string.shortcut_resume_short))
                .setLongLabel(context.getString(R.string.shortcut_resume_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_play))
                .setIntent(intent)
                .setRank(1)
                .build()
        }

        /**
         * Create shortcut for showing progress/analytics.
         */
        private fun createProgressShortcut(): ShortcutInfoCompat {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse(DeepLinkRoutes.URI_ANALYTICS)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

            return ShortcutInfoCompat.Builder(context, SHORTCUT_SHOW_PROGRESS)
                .setShortLabel(context.getString(R.string.shortcut_progress_short))
                .setLongLabel(context.getString(R.string.shortcut_progress_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_analytics))
                .setIntent(intent)
                .setRank(2)
                .build()
        }

        /**
         * Create shortcut for viewing to-dos.
         */
        private fun createTodosShortcut(): ShortcutInfoCompat {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse(DeepLinkRoutes.URI_TODO)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

            return ShortcutInfoCompat.Builder(context, SHORTCUT_VIEW_TODOS)
                .setShortLabel(context.getString(R.string.shortcut_todos_short))
                .setLongLabel(context.getString(R.string.shortcut_todos_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_checklist))
                .setIntent(intent)
                .setRank(3)
                .build()
        }

        /**
         * Report shortcut usage to improve ranking.
         */
        fun reportShortcutUsed(shortcutId: String) {
            ShortcutManagerCompat.reportShortcutUsed(context, shortcutId)
        }

        /**
         * Remove all dynamic shortcuts.
         */
        fun removeAllShortcuts() {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        }

        /**
         * Check if shortcuts are supported on this device.
         */
        fun isShortcutsSupported(): Boolean {
            return ShortcutManagerCompat.isRequestPinShortcutSupported(context)
        }

        /**
         * Request to pin a shortcut to the home screen.
         */
        fun requestPinShortcut(shortcutId: String): Boolean {
            val shortcut =
                when (shortcutId) {
                    SHORTCUT_START_SESSION -> createStartSessionShortcut()
                    SHORTCUT_RESUME_LEARNING -> createResumeShortcut()
                    SHORTCUT_SHOW_PROGRESS -> createProgressShortcut()
                    SHORTCUT_VIEW_TODOS -> createTodosShortcut()
                    else -> return false
                }

            return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }
    }
