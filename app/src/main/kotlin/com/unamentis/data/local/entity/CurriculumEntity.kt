package com.unamentis.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing downloaded curricula.
 *
 * Stores the complete curriculum JSON for offline access.
 *
 * @property id Curriculum identifier (primary key)
 * @property title Curriculum title
 * @property description Brief description
 * @property version Curriculum version
 * @property contentJson Complete curriculum content as JSON string
 * @property downloadedAt Unix timestamp of download
 * @property lastAccessedAt Unix timestamp of last access
 * @property topicCount Number of topics in curriculum
 */
@Entity(tableName = "curricula")
data class CurriculumEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val version: String,
    val contentJson: String,
    val downloadedAt: Long,
    val lastAccessedAt: Long,
    val topicCount: Int
)
