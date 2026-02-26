package com.unamentis.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.unamentis.data.local.dao.CurriculumDao
import com.unamentis.data.local.dao.ModuleDao
import com.unamentis.data.local.dao.QueuedMetricsDao
import com.unamentis.data.local.dao.ReadingListDao
import com.unamentis.data.local.dao.SessionDao
import com.unamentis.data.local.dao.TodoDao
import com.unamentis.data.local.dao.TopicProgressDao
import com.unamentis.data.local.entity.CurriculumEntity
import com.unamentis.data.local.entity.DownloadedModuleEntity
import com.unamentis.data.local.entity.QueuedMetricsEntity
import com.unamentis.data.local.entity.ReadingBookmarkEntity
import com.unamentis.data.local.entity.ReadingChunkEntity
import com.unamentis.data.local.entity.ReadingListItemEntity
import com.unamentis.data.local.entity.ReadingVisualAssetEntity
import com.unamentis.data.local.entity.SessionEntity
import com.unamentis.data.local.entity.TopicProgressEntity
import com.unamentis.data.local.entity.TranscriptEntryEntity
import com.unamentis.data.model.Todo

/**
 * Room database for UnaMentis.
 *
 * This database stores:
 * - Downloaded curricula
 * - Session history and transcripts
 * - Topic progress tracking
 *
 * The database uses in-memory mode for tests and persistent mode
 * for production.
 */
@Database(
    entities = [
        CurriculumEntity::class,
        SessionEntity::class,
        TranscriptEntryEntity::class,
        TopicProgressEntity::class,
        Todo::class,
        DownloadedModuleEntity::class,
        ReadingListItemEntity::class,
        ReadingChunkEntity::class,
        ReadingBookmarkEntity::class,
        ReadingVisualAssetEntity::class,
        QueuedMetricsEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun curriculumDao(): CurriculumDao

    abstract fun sessionDao(): SessionDao

    abstract fun topicProgressDao(): TopicProgressDao

    abstract fun todoDao(): TodoDao

    abstract fun moduleDao(): ModuleDao

    abstract fun readingListDao(): ReadingListDao

    abstract fun queuedMetricsDao(): QueuedMetricsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration from version 2 to 3: Add isStarred column to sessions table.
         */
        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE sessions ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        /**
         * Migration from version 3 to 4: Add AI suggestion and due date columns to todos table.
         */
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE todos ADD COLUMN dueDate INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE todos ADD COLUMN isAISuggested INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE todos ADD COLUMN suggestionReason TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE todos ADD COLUMN suggestionConfidence REAL DEFAULT NULL")
                }
            }

        /**
         * Migration from version 4 to 5: Add downloaded_modules table.
         */
        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS downloaded_modules (
                            id TEXT PRIMARY KEY NOT NULL,
                            name TEXT NOT NULL,
                            version TEXT NOT NULL,
                            description TEXT NOT NULL,
                            downloadedAt INTEGER NOT NULL,
                            lastAccessedAt INTEGER NOT NULL,
                            contentJson TEXT NOT NULL,
                            configJson TEXT,
                            sizeBytes INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                }
            }

        /**
         * Migration from version 5 to 6: Add reading list tables.
         */
        private val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    createReadingListItemsTable(db)
                    createReadingChunksTable(db)
                    createReadingBookmarksTable(db)
                    createReadingVisualAssetsTable(db)
                }
            }

        /**
         * Migration from version 6 to 7: Add queued_metrics table.
         */
        private val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS queued_metrics (
                            id TEXT PRIMARY KEY NOT NULL,
                            payloadJson TEXT NOT NULL,
                            queuedAt INTEGER NOT NULL,
                            retryCount INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent(),
                    )
                }
            }

        private fun createReadingListItemsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reading_list_items (
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    author TEXT,
                    sourceType TEXT NOT NULL DEFAULT 'text',
                    status TEXT NOT NULL DEFAULT 'unread',
                    fileUrl TEXT,
                    fileHash TEXT,
                    fileSizeBytes INTEGER NOT NULL DEFAULT 0,
                    currentChunkIndex INTEGER NOT NULL DEFAULT 0,
                    percentComplete REAL NOT NULL DEFAULT 0.0,
                    addedAt INTEGER NOT NULL,
                    lastReadAt INTEGER,
                    completedAt INTEGER,
                    audioPreGenStatus TEXT NOT NULL DEFAULT 'none'
                )
                """.trimIndent(),
            )
        }

        private fun createReadingChunksTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reading_chunks (
                    id TEXT PRIMARY KEY NOT NULL,
                    readingListItemId TEXT NOT NULL,
                    `index` INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    characterOffset INTEGER NOT NULL DEFAULT 0,
                    estimatedDurationSeconds REAL NOT NULL DEFAULT 0.0,
                    cachedAudioSampleRate REAL NOT NULL DEFAULT 0.0,
                    FOREIGN KEY (readingListItemId)
                        REFERENCES reading_list_items(id)
                        ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                    index_reading_chunks_readingListItemId
                    ON reading_chunks(readingListItemId)
                """.trimIndent(),
            )
        }

        private fun createReadingBookmarksTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reading_bookmarks (
                    id TEXT PRIMARY KEY NOT NULL,
                    readingListItemId TEXT NOT NULL,
                    chunkIndex INTEGER NOT NULL,
                    note TEXT,
                    snippetPreview TEXT,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY (readingListItemId)
                        REFERENCES reading_list_items(id)
                        ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                    index_reading_bookmarks_readingListItemId
                    ON reading_bookmarks(readingListItemId)
                """.trimIndent(),
            )
        }

        private fun createReadingVisualAssetsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reading_visual_assets (
                    id TEXT PRIMARY KEY NOT NULL,
                    readingListItemId TEXT NOT NULL,
                    chunkIndex INTEGER NOT NULL DEFAULT 0,
                    pageIndex INTEGER NOT NULL DEFAULT 0,
                    positionOnPage REAL NOT NULL DEFAULT 0.0,
                    mimeType TEXT,
                    width INTEGER NOT NULL DEFAULT 0,
                    height INTEGER NOT NULL DEFAULT 0,
                    altText TEXT,
                    localPath TEXT,
                    FOREIGN KEY (readingListItemId)
                        REFERENCES reading_list_items(id)
                        ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                    index_reading_visual_assets_readingListItemId
                    ON reading_visual_assets(readingListItemId)
                """.trimIndent(),
            )
        }

        /**
         * Migration from version 7 to 8: Add enhanced todo fields for iOS parity.
         *
         * Adds: sortOrder, itemType, source, curriculumId, granularity,
         * resumeTopicId, resumeSegmentIndex, resumeConversationContext,
         * suggestedCurriculumIds, archivedAt.
         */
        private val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE todos ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE todos ADD COLUMN itemType TEXT NOT NULL DEFAULT 'LEARNING_TARGET'")
                    db.execSQL("ALTER TABLE todos ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
                    db.execSQL("ALTER TABLE todos ADD COLUMN curriculumId TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE todos ADD COLUMN granularity TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE todos ADD COLUMN resumeTopicId TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE todos ADD COLUMN resumeSegmentIndex INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE todos ADD COLUMN resumeConversationContext TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE todos ADD COLUMN suggestedCurriculumIds TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE todos ADD COLUMN archivedAt INTEGER DEFAULT NULL")
                }
            }

        /**
         * Get the singleton database instance.
         *
         * @param context Application context
         * @return Database instance
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "unamentis.db",
                    )
                        .addMigrations(
                            MIGRATION_2_3,
                            MIGRATION_3_4,
                            MIGRATION_4_5,
                            MIGRATION_5_6,
                            MIGRATION_6_7,
                            MIGRATION_7_8,
                        )
                        .fallbackToDestructiveMigration()
                        .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Create an in-memory database for testing.
         *
         * @param context Test context
         * @return In-memory database instance
         */
        fun createInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
            )
                .allowMainThreadQueries() // Only for tests
                .build()
        }
    }
}
