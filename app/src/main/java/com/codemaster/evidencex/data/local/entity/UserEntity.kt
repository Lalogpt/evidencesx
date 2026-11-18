package com.codemaster.evidencex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_entity")
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // ID o Email

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "email")
    val email: String,

    @ColumnInfo(name = "password")
    val password: String,

    @ColumnInfo(name = "role")
    val role: String, // SUPER_ADMIN, ADMIN_AREA, PERITO

    @ColumnInfo(name = "area")
    val area: String, // e.g. "Balística", "Informática", "Dactiloscopia", "General"

    @ColumnInfo(name = "synced")
    val synced: Boolean = false
)
