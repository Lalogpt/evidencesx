package com.codemaster.evidencex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_entity")
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // PERITO-007 or Email

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "email")
    val email: String,

    @ColumnInfo(name = "password")
    val password: String, // Password for local offline login

    @ColumnInfo(name = "role")
    val role: String, // ADMIN or PERITO

    @ColumnInfo(name = "synced")
    val synced: Boolean = false
)
