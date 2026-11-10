package com.codemaster.evidencex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "evidence_entity",
    foreignKeys = [
        ForeignKey(
            entity = CaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["case_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["case_id"])]
)
data class EvidenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID v4

    @ColumnInfo(name = "case_id")
    val caseId: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "file_uri")
    val fileUri: String, // Internal private storage path

    @ColumnInfo(name = "file_hash")
    val fileHash: String, // SHA-256 of binary

    @ColumnInfo(name = "metadata_hash")
    val metadataHash: String, // SHA-256 of canonical metadata

    @ColumnInfo(name = "evidence_hash")
    val evidenceHash: String, // SHA-256(file_hash || metadata_hash)

    @ColumnInfo(name = "notes")
    val notes: String,

    @ColumnInfo(name = "gps_lat")
    val gpsLat: Double,

    @ColumnInfo(name = "gps_lng")
    val gpsLng: Double,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "sensitive")
    val sensitive: Boolean = false, // Flag for sensitive forensic imagery

    @ColumnInfo(name = "version")
    val version: Int = 1,

    @ColumnInfo(name = "synced")
    val synced: Boolean = false
)
