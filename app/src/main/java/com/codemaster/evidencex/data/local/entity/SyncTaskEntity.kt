package com.codemaster.evidencex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_task_entity")
data class SyncTaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID v4

    @ColumnInfo(name = "entity_type")
    val entityType: String, // CASE, EVIDENCE, AUDIT

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "operation")
    val operation: String, // INSERT, UPDATE

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "status")
    val status: String, // PENDING, IN_PROGRESS, COMPLETED, FAILED

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
