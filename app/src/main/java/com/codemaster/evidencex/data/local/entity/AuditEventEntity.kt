package com.codemaster.evidencex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_event_entity")
data class AuditEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID v4

    @ColumnInfo(name = "case_id")
    val caseId: String,

    @ColumnInfo(name = "evidence_id")
    val evidenceId: String?,

    @ColumnInfo(name = "event_type")
    val eventType: String, // CREATE_CASE, CAPTURE_EVIDENCE, VERIFY, SYNC, EXPORT_PDF

    @ColumnInfo(name = "event_details")
    val eventDetails: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "prev_event_hash")
    val prevEventHash: String, // Hash of previous audit event in chain (genesis = 64 zeros)

    @ColumnInfo(name = "event_hash")
    val eventHash: String, // SHA-256(event_content || prev_event_hash)

    @ColumnInfo(name = "synced")
    val synced: Boolean = false
)
