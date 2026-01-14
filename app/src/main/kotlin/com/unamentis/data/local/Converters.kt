package com.unamentis.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Type converters for Room database.
 *
 * Handles conversion between complex types and primitive types
 * for storage in SQLite.
 */
class Converters {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @TypeConverter
    fun fromStringList(value: String): List<String> {
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun toStringList(list: List<String>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun fromStringMap(value: String): Map<String, String> {
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun toStringMap(map: Map<String, String>): String {
        return json.encodeToString(map)
    }
}
