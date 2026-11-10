package com.codemaster.evidencex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "case_entity")
data class CaseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID v4

    @ColumnInfo(name = "case_number")
    val caseNumber: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "investigator_id")
    val investigatorId: String,

    @ColumnInfo(name = "status")
    val status: String, // ACTIVE, CLOSED, ARCHIVED

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "synced")
    val synced: Boolean = false
)
