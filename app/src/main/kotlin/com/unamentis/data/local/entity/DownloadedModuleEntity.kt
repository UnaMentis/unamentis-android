package com.unamentis.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing downloaded modules.
 *
 * Stores module metadata and content for offline access.
 * Content includes questions, configurations, and other
 * module-specific data as JSON.
 *
 * @property id Module identifier (primary key)
 * @property name Module display name
 * @property version Module version string
 * @property description Brief module description
 * @property downloadedAt Unix timestamp of download
 * @property lastAccessedAt Unix timestamp of last access
 * @property contentJson Complete module content as JSON string
 * @property configJson Module configuration as JSON string (nullable)
 * @property sizeBytes Approximate content size in bytes
 */
@Entity(tableName = "downloaded_modules")
data class DownloadedModuleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val downloadedAt: Long,
    val lastAccessedAt: Long,
    val contentJson: String,
    val configJson: String?,
    val sizeBytes: Long,
)
