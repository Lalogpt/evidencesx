package com.codemaster.evidencex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.codemaster.evidencex.data.local.entity.AuditEventEntity
import com.codemaster.evidencex.data.local.entity.CaseEntity
import com.codemaster.evidencex.data.local.entity.ChatMessageEntity
import com.codemaster.evidencex.data.local.entity.EvidenceEntity
import com.codemaster.evidencex.data.local.entity.SyncTaskEntity
import com.codemaster.evidencex.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceDao {

    // --- Cases ---
    @Query("SELECT * FROM case_entity ORDER BY created_at DESC")
    fun getAllCases(): Flow<List<CaseEntity>>

    @Query("SELECT * FROM case_entity")
    suspend fun getAllCasesSync(): List<CaseEntity>

    @Query("SELECT * FROM case_entity WHERE id = :caseId")
    suspend fun getCaseById(caseId: String): CaseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCase(caseEntity: CaseEntity)

    @Update
    suspend fun updateCase(caseEntity: CaseEntity)

    @Query("DELETE FROM case_entity WHERE id = :caseId")
    suspend fun deleteCaseById(caseId: String)

    // --- Users (Peritos / Admins) ---
    @Query("SELECT * FROM user_entity")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM user_entity WHERE (email = :identifier OR id = :identifier) AND password = :password")
    suspend fun getUserByCredentials(identifier: String, password: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(userEntity: UserEntity)

    @Query("DELETE FROM user_entity WHERE id = :userId")
    suspend fun deleteUserById(userId: String)

    // --- Evidences ---
    @Query("SELECT * FROM evidence_entity WHERE case_id = :caseId ORDER BY timestamp DESC")
    fun getEvidencesForCase(caseId: String): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidence_entity WHERE case_id = :caseId ORDER BY timestamp DESC")
    suspend fun getEvidencesForCaseSync(caseId: String): List<EvidenceEntity>

    @Query("SELECT * FROM evidence_entity")
    suspend fun getAllEvidencesSync(): List<EvidenceEntity>

    @Query("SELECT * FROM evidence_entity WHERE id = :evidenceId")
    suspend fun getEvidenceById(evidenceId: String): EvidenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidenceEntity: EvidenceEntity)

    // --- Chat Messages ---
    @Query("SELECT * FROM chat_message_entity WHERE (LOWER(sender_id) = LOWER(:userId1) AND LOWER(recipient_id) = LOWER(:userId2)) OR (LOWER(sender_id) = LOWER(:userId2) AND LOWER(recipient_id) = LOWER(:userId1)) ORDER BY timestamp ASC")
    fun getMessagesBetween(userId1: String, userId2: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_message_entity WHERE LOWER(recipient_id) = LOWER(:userId) OR LOWER(sender_id) = LOWER(:userId) ORDER BY timestamp DESC")
    fun getAllMessagesForUser(userId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(messageEntity: ChatMessageEntity)

    @Query("SELECT * FROM chat_message_entity WHERE id = :msgId")
    suspend fun getMessageById(msgId: String): ChatMessageEntity?

    // --- Audit Events ---
    @Query("SELECT * FROM audit_event_entity ORDER BY timestamp ASC")
    fun getAllAuditEvents(): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_event_entity ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestAuditEvent(): AuditEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditEvent(auditEventEntity: AuditEventEntity)

    // --- Sync Tasks ---
    @Query("SELECT * FROM sync_task_entity WHERE status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingSyncTasks(): List<SyncTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncTask(syncTaskEntity: SyncTaskEntity)

    @Query("UPDATE sync_task_entity SET status = :status, retry_count = :retryCount, last_error = :error WHERE id = :taskId")
    suspend fun updateSyncTaskStatus(taskId: String, status: String, retryCount: Int, error: String?)

    @Query("DELETE FROM sync_task_entity WHERE id = :taskId")
    suspend fun deleteSyncTask(taskId: String)

    // --- ATOMIC TRANSACTIONS ---
    @Transaction
    suspend fun insertUserAtomically(
        userEntity: UserEntity,
        syncTaskEntity: SyncTaskEntity
    ) {
        insertUser(userEntity)
        insertSyncTask(syncTaskEntity)
    }

    @Transaction
    suspend fun deleteUserAtomically(
        userId: String,
        syncTaskEntity: SyncTaskEntity
    ) {
        deleteUserById(userId)
        insertSyncTask(syncTaskEntity)
    }

    @Transaction
    suspend fun insertMessageAtomically(
        messageEntity: ChatMessageEntity,
        syncTaskEntity: SyncTaskEntity
    ) {
        insertMessage(messageEntity)
        insertSyncTask(syncTaskEntity)
    }

    @Transaction
    suspend fun insertEvidenceAtomically(
        caseEntity: CaseEntity?,
        evidenceEntity: EvidenceEntity,
        auditEventEntity: AuditEventEntity,
        syncTaskEntity: SyncTaskEntity,
        auditSyncTaskEntity: SyncTaskEntity?
    ) {
        if (caseEntity != null) {
            insertCase(caseEntity)
        }
        insertEvidence(evidenceEntity)
        insertAuditEvent(auditEventEntity)
        insertSyncTask(syncTaskEntity)
        if (auditSyncTaskEntity != null) {
            insertSyncTask(auditSyncTaskEntity)
        }
    }

    @Transaction
    suspend fun insertCaseAtomically(
        caseEntity: CaseEntity,
        auditEventEntity: AuditEventEntity,
        syncTaskEntity: SyncTaskEntity
    ) {
        insertCase(caseEntity)
        insertAuditEvent(auditEventEntity)
        insertSyncTask(syncTaskEntity)
    }

    @Transaction
    suspend fun updateCaseAtomically(
        caseEntity: CaseEntity,
        auditEventEntity: AuditEventEntity,
        syncTaskEntity: SyncTaskEntity
    ) {
        updateCase(caseEntity)
        insertAuditEvent(auditEventEntity)
        insertSyncTask(syncTaskEntity)
    }

    @Transaction
    suspend fun deleteCaseAtomically(
        caseId: String,
        auditEventEntity: AuditEventEntity,
        syncTaskEntity: SyncTaskEntity
    ) {
        deleteCaseById(caseId)
        insertAuditEvent(auditEventEntity)
        insertSyncTask(syncTaskEntity)
    }
}
