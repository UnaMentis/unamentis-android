package com.unamentis.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 2,
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
