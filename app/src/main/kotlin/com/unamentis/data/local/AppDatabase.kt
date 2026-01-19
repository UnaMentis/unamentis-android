package com.unamentis.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.unamentis.data.local.dao.CurriculumDao
import com.unamentis.data.local.dao.SessionDao
import com.unamentis.data.local.dao.TodoDao
import com.unamentis.data.local.dao.TopicProgressDao
import com.unamentis.data.local.entity.CurriculumEntity
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
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun curriculumDao(): CurriculumDao

    abstract fun sessionDao(): SessionDao

    abstract fun topicProgressDao(): TopicProgressDao

    abstract fun todoDao(): TodoDao

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
                        .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
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
