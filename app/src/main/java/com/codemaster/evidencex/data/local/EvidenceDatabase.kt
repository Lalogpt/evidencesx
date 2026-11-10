package com.codemaster.evidencex.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.codemaster.evidencex.data.local.dao.EvidenceDao
import com.codemaster.evidencex.data.local.entity.AuditEventEntity
import com.codemaster.evidencex.data.local.entity.CaseEntity
import com.codemaster.evidencex.data.local.entity.EvidenceEntity
import com.codemaster.evidencex.data.local.entity.SyncTaskEntity
import com.codemaster.evidencex.data.local.entity.UserEntity

@Database(
    entities = [
        CaseEntity::class,
        EvidenceEntity::class,
        AuditEventEntity::class,
        SyncTaskEntity::class,
        UserEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class EvidenceDatabase : RoomDatabase() {

    abstract fun evidenceDao(): EvidenceDao

    companion object {
        @Volatile
        private var INSTANCE: EvidenceDatabase? = null

        fun getDatabase(context: Context): EvidenceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EvidenceDatabase::class.java,
                    "evidence_x_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
