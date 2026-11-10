package com.codemaster.evidencex.data.repository

import android.content.Context
import com.codemaster.evidencex.data.local.dao.EvidenceDao
import com.codemaster.evidencex.data.local.entity.AuditEventEntity
import com.codemaster.evidencex.data.local.entity.CaseEntity
import com.codemaster.evidencex.data.local.entity.EvidenceEntity
import com.codemaster.evidencex.data.local.entity.SyncTaskEntity
import com.codemaster.evidencex.data.local.entity.UserEntity
import com.codemaster.evidencex.domain.crypto.CryptoUtils
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.io.File

class EvidenceRepositoryImpl(
    private val context: Context,
    private val evidenceDao: EvidenceDao
) {

    val allCases: Flow<List<CaseEntity>> = evidenceDao.getAllCases()
    val allUsers: Flow<List<UserEntity>> = evidenceDao.getAllUsers()

    fun getEvidencesForCase(caseId: String): Flow<List<EvidenceEntity>> {
        return evidenceDao.getEvidencesForCase(caseId)
    }

    val allAuditEvents: Flow<List<AuditEventEntity>> = evidenceDao.getAllAuditEvents()

    suspend fun createPeritoUser(name: String, idOrEmail: String, password: String, role: String = "PERITO") {
        val userEntity = UserEntity(
            id = idOrEmail,
            name = name,
            email = idOrEmail,
            password = password,
            role = role,
            synced = false
        )

        val syncTask = SyncTaskEntity(
            id = CryptoUtils.generateUuid(),
            entityType = "USER",
            entityId = idOrEmail,
            operation = "INSERT",
            payloadJson = "{\"userId\":\"$idOrEmail\",\"name\":\"$name\",\"email\":\"$idOrEmail\",\"password\":\"$password\",\"role\":\"$role\"}",
            status = "PENDING",
            createdAt = System.currentTimeMillis()
        )

        evidenceDao.insertUserAtomically(userEntity, syncTask)
    }

    suspend fun authenticateLocalUser(identifier: String, password: String): UserEntity? {
        return evidenceDao.getUserByCredentials(identifier, password)
    }

    suspend fun createCase(
        caseNumber: String,
        title: String,
        description: String,
        investigatorId: String
    ): String {
        val caseId = CryptoUtils.generateUuid()
        val now = System.currentTimeMillis()
        val caseEntity = CaseEntity(
            id = caseId,
            caseNumber = caseNumber,
            title = title,
            description = description,
            investigatorId = investigatorId,
            status = "ACTIVE",
            createdAt = now,
            updatedAt = now,
            synced = false
        )

        val latestAudit = evidenceDao.getLatestAuditEvent()
        val prevHash = latestAudit?.eventHash ?: CryptoUtils.GENESIS_HASH
        val eventContent = "CREATE_CASE:$caseId:$caseNumber:$investigatorId:$now"
        val eventHash = CryptoUtils.calculateAuditEventHash(eventContent, prevHash)
        val auditId = CryptoUtils.generateUuid()

        val auditEntity = AuditEventEntity(
            id = auditId,
            caseId = caseId,
            evidenceId = null,
            eventType = "CREATE_CASE",
            eventDetails = "Case created: $title ($caseNumber)",
            userId = investigatorId,
            timestamp = now,
            prevEventHash = prevHash,
            eventHash = eventHash,
            synced = false
        )

        val syncTask = SyncTaskEntity(
            id = CryptoUtils.generateUuid(),
            entityType = "CASE",
            entityId = caseId,
            operation = "INSERT",
            payloadJson = "{\"caseId\":\"$caseId\",\"caseNumber\":\"$caseNumber\",\"title\":\"$title\",\"description\":\"$description\",\"investigatorId\":\"$investigatorId\",\"status\":\"ACTIVE\",\"createdAt\":$now,\"updatedAt\":$now}",
            status = "PENDING",
            createdAt = now
        )

        evidenceDao.insertCaseAtomically(caseEntity, auditEntity, syncTask)
        return caseId
    }

    suspend fun updateCase(
        caseId: String,
        newTitle: String,
        newDescription: String,
        newStatus: String,
        adminUserId: String
    ) {
        val existingCase = evidenceDao.getCaseById(caseId) ?: return
        val now = System.currentTimeMillis()
        val updatedCase = existingCase.copy(
            title = newTitle,
            description = newDescription,
            status = newStatus,
            updatedAt = now,
            synced = false
        )

        val latestAudit = evidenceDao.getLatestAuditEvent()
        val prevHash = latestAudit?.eventHash ?: CryptoUtils.GENESIS_HASH
        val eventContent = "EDIT_CASE:$caseId:$newTitle:$newStatus:$now"
        val eventHash = CryptoUtils.calculateAuditEventHash(eventContent, prevHash)
        val auditId = CryptoUtils.generateUuid()

        val auditEntity = AuditEventEntity(
            id = auditId,
            caseId = caseId,
            evidenceId = null,
            eventType = "EDIT_CASE",
            eventDetails = "Case edited by Admin ($adminUserId): $newTitle (Status: $newStatus)",
            userId = adminUserId,
            timestamp = now,
            prevEventHash = prevHash,
            eventHash = eventHash,
            synced = false
        )

        val syncTask = SyncTaskEntity(
            id = CryptoUtils.generateUuid(),
            entityType = "CASE",
            entityId = caseId,
            operation = "UPDATE",
            payloadJson = "{\"caseId\":\"$caseId\",\"title\":\"$newTitle\",\"description\":\"$newDescription\",\"status\":\"$newStatus\",\"updatedAt\":$now}",
            status = "PENDING",
            createdAt = now
        )

        evidenceDao.updateCaseAtomically(updatedCase, auditEntity, syncTask)
    }

    suspend fun deleteCase(caseId: String, adminUserId: String) {
        val now = System.currentTimeMillis()
        val latestAudit = evidenceDao.getLatestAuditEvent()
        val prevHash = latestAudit?.eventHash ?: CryptoUtils.GENESIS_HASH
        val eventContent = "DELETE_CASE:$caseId:$now"
        val eventHash = CryptoUtils.calculateAuditEventHash(eventContent, prevHash)
        val auditId = CryptoUtils.generateUuid()

        val auditEntity = AuditEventEntity(
            id = auditId,
            caseId = caseId,
            evidenceId = null,
            eventType = "DELETE_CASE",
            eventDetails = "Case deleted by Admin ($adminUserId)",
            userId = adminUserId,
            timestamp = now,
            prevEventHash = prevHash,
            eventHash = eventHash,
            synced = false
        )

        val syncTask = SyncTaskEntity(
            id = CryptoUtils.generateUuid(),
            entityType = "CASE",
            entityId = caseId,
            operation = "DELETE",
            payloadJson = "{\"caseId\":\"$caseId\"}",
            status = "PENDING",
            createdAt = now
        )

        evidenceDao.deleteCaseAtomically(caseId, auditEntity, syncTask)
    }

    suspend fun captureEvidence(
        caseId: String,
        sourceFile: File,
        originalFileName: String,
        notes: String,
        gpsLat: Double,
        gpsLng: Double,
        investigatorId: String,
        sensitive: Boolean
    ): String {
        val evidenceDir = File(context.filesDir, "secure_evidence").apply { mkdirs() }
        val savedFile = File(evidenceDir, "${CryptoUtils.generateUuid()}_$originalFileName")
        sourceFile.copyTo(savedFile, overwrite = true)
        sourceFile.delete()

        val fileHash = CryptoUtils.calculateFileHash(savedFile)
        val timestamp = System.currentTimeMillis()
        val metadataHash = CryptoUtils.calculateMetadataHash(
            caseId = caseId,
            fileName = savedFile.name,
            notes = notes,
            gpsLat = gpsLat,
            gpsLng = gpsLng,
            timestamp = timestamp,
            investigatorId = investigatorId
        )
        val evidenceHash = CryptoUtils.calculateEvidenceHash(fileHash, metadataHash)
        val evidenceId = CryptoUtils.generateUuid()

        val evidenceEntity = EvidenceEntity(
            id = evidenceId,
            caseId = caseId,
            fileName = savedFile.name,
            fileUri = savedFile.absolutePath,
            fileHash = fileHash,
            metadataHash = metadataHash,
            evidenceHash = evidenceHash,
            notes = notes,
            gpsLat = gpsLat,
            gpsLng = gpsLng,
            timestamp = timestamp,
            sensitive = sensitive,
            version = 1,
            synced = false
        )

        val latestAudit = evidenceDao.getLatestAuditEvent()
        val prevHash = latestAudit?.eventHash ?: CryptoUtils.GENESIS_HASH
        val eventContent = "CAPTURE_EVIDENCE:$evidenceId:$caseId:$evidenceHash:$timestamp"
        val eventHash = CryptoUtils.calculateAuditEventHash(eventContent, prevHash)
        val auditId = CryptoUtils.generateUuid()

        val auditEntity = AuditEventEntity(
            id = auditId,
            caseId = caseId,
            evidenceId = evidenceId,
            eventType = "CAPTURE_EVIDENCE",
            eventDetails = "Evidence captured securely (Sensitive: $sensitive). Hash: $evidenceHash",
            userId = investigatorId,
            timestamp = timestamp,
            prevEventHash = prevHash,
            eventHash = eventHash,
            synced = false
        )

        val evidenceSyncTask = SyncTaskEntity(
            id = CryptoUtils.generateUuid(),
            entityType = "EVIDENCE",
            entityId = evidenceId,
            operation = "INSERT",
            payloadJson = "{\"evidenceId\":\"$evidenceId\",\"caseId\":\"$caseId\",\"fileName\":\"${savedFile.name}\",\"fileHash\":\"$fileHash\",\"metadataHash\":\"$metadataHash\",\"evidenceHash\":\"$evidenceHash\",\"notes\":\"$notes\",\"gpsLat\":$gpsLat,\"gpsLng\":$gpsLng,\"timestamp\":$timestamp,\"sensitive\":$sensitive}",
            status = "PENDING",
            createdAt = timestamp
        )

        val auditSyncTask = SyncTaskEntity(
            id = CryptoUtils.generateUuid(),
            entityType = "AUDIT",
            entityId = auditId,
            operation = "INSERT",
            payloadJson = "{\"auditId\":\"$auditId\",\"caseId\":\"$caseId\",\"eventType\":\"CAPTURE_EVIDENCE\",\"eventDetails\":\"Evidence captured\",\"userId\":\"$investigatorId\",\"timestamp\":$timestamp,\"prevEventHash\":\"$prevHash\",\"eventHash\":\"$eventHash\"}",
            status = "PENDING",
            createdAt = timestamp
        )

        evidenceDao.insertEvidenceAtomically(
            caseEntity = null,
            evidenceEntity = evidenceEntity,
            auditEventEntity = auditEntity,
            syncTaskEntity = evidenceSyncTask,
            auditSyncTaskEntity = auditSyncTask
        )

        return evidenceId
    }

    private fun extractJsonField(json: String?, key: String): String? {
        if (json == null) return null
        return try {
            val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            pattern.find(json)?.groupValues?.get(1) ?: run {
                val numPattern = "\"$key\"\\s*:\\s*([0-9]+)".toRegex()
                numPattern.find(json)?.groupValues?.get(1)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sincronización de bajada (Cloud-to-Local Pull): Descarga usuarios, expedientes y evidencias desde Firestore hacia Room.
     */
    suspend fun syncPullFromCloud() {
        try {
            val firestore = FirebaseFirestore.getInstance()

            // 1. Pull Users
            val usersSnap = firestore.collection("users").get().await()
            for (doc in usersSnap.documents) {
                val id = doc.getString("id") ?: doc.id
                val payload = doc.getString("payload")
                val name = doc.getString("name") ?: extractJsonField(payload, "name") ?: "Perito"
                val email = doc.getString("email") ?: extractJsonField(payload, "email") ?: id
                val password = doc.getString("password") ?: extractJsonField(payload, "password") ?: "123456"
                val role = doc.getString("role") ?: extractJsonField(payload, "role") ?: "PERITO"
                val user = UserEntity(id = id, name = name, email = email, password = password, role = role, synced = true)
                evidenceDao.insertUser(user)
            }

            // 2. Pull Cases
            val casesSnap = firestore.collection("cases").get().await()
            for (doc in casesSnap.documents) {
                val id = doc.getString("id") ?: doc.id
                val payload = doc.getString("payload")
                val caseNumber = doc.getString("caseNumber") ?: extractJsonField(payload, "caseNumber") ?: "EXP-001"
                val title = doc.getString("title") ?: extractJsonField(payload, "title") ?: "Expediente Nube"
                val description = doc.getString("description") ?: extractJsonField(payload, "description") ?: ""
                val investigatorId = doc.getString("investigatorId") ?: extractJsonField(payload, "investigatorId") ?: "PERITO-001"
                val status = doc.getString("status") ?: extractJsonField(payload, "status") ?: "ACTIVE"
                val createdAt = doc.getLong("createdAt") ?: extractJsonField(payload, "createdAt")?.toLongOrNull() ?: System.currentTimeMillis()
                val updatedAt = doc.getLong("updatedAt") ?: extractJsonField(payload, "updatedAt")?.toLongOrNull() ?: createdAt

                val caseEntity = CaseEntity(
                    id = id,
                    caseNumber = caseNumber,
                    title = title,
                    description = description,
                    investigatorId = investigatorId,
                    status = status,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    synced = true
                )
                evidenceDao.insertCase(caseEntity)
            }

            // 3. Pull Evidences
            val evidencesSnap = firestore.collection("evidences").get().await()
            for (doc in evidencesSnap.documents) {
                val id = doc.getString("id") ?: doc.id
                val payload = doc.getString("payload")
                val caseId = doc.getString("caseId") ?: extractJsonField(payload, "caseId") ?: continue
                val fileName = doc.getString("fileName") ?: extractJsonField(payload, "fileName") ?: "evidence.jpg"
                val fileHash = doc.getString("fileHash") ?: extractJsonField(payload, "fileHash") ?: ""
                val metadataHash = doc.getString("metadataHash") ?: extractJsonField(payload, "metadataHash") ?: ""
                val evidenceHash = doc.getString("evidenceHash") ?: extractJsonField(payload, "evidenceHash") ?: ""
                val notes = doc.getString("notes") ?: extractJsonField(payload, "notes") ?: ""
                val gpsLat = doc.getDouble("gpsLat") ?: extractJsonField(payload, "gpsLat")?.toDoubleOrNull() ?: 19.4326
                val gpsLng = doc.getDouble("gpsLng") ?: extractJsonField(payload, "gpsLng")?.toDoubleOrNull() ?: -99.1332
                val timestamp = doc.getLong("timestamp") ?: extractJsonField(payload, "timestamp")?.toLongOrNull() ?: System.currentTimeMillis()
                val sensitive = doc.getBoolean("sensitive") ?: extractJsonField(payload, "sensitive")?.toBoolean() ?: false

                val evidenceEntity = EvidenceEntity(
                    id = id,
                    caseId = caseId,
                    fileName = fileName,
                    fileUri = "",
                    fileHash = fileHash,
                    metadataHash = metadataHash,
                    evidenceHash = evidenceHash,
                    notes = notes,
                    gpsLat = gpsLat,
                    gpsLng = gpsLng,
                    timestamp = timestamp,
                    sensitive = sensitive,
                    synced = true
                )
                evidenceDao.insertEvidence(evidenceEntity)
            }

            // 4. Pull Audit Events
            val auditSnap = firestore.collection("audit_events").get().await()
            for (doc in auditSnap.documents) {
                val id = doc.getString("id") ?: doc.id
                val payload = doc.getString("payload")
                val caseId = doc.getString("caseId") ?: extractJsonField(payload, "caseId") ?: ""
                val eventType = doc.getString("eventType") ?: extractJsonField(payload, "eventType") ?: "SYNC"
                val eventDetails = doc.getString("eventDetails") ?: extractJsonField(payload, "eventDetails") ?: ""
                val userId = doc.getString("userId") ?: extractJsonField(payload, "userId") ?: "SYSTEM"
                val timestamp = doc.getLong("timestamp") ?: extractJsonField(payload, "timestamp")?.toLongOrNull() ?: System.currentTimeMillis()
                val prevEventHash = doc.getString("prevEventHash") ?: extractJsonField(payload, "prevEventHash") ?: CryptoUtils.GENESIS_HASH
                val eventHash = doc.getString("eventHash") ?: extractJsonField(payload, "eventHash") ?: CryptoUtils.sha256(id)

                val auditEntity = AuditEventEntity(
                    id = id,
                    caseId = caseId,
                    evidenceId = null,
                    eventType = eventType,
                    eventDetails = eventDetails,
                    userId = userId,
                    timestamp = timestamp,
                    prevEventHash = prevEventHash,
                    eventHash = eventHash,
                    synced = true
                )
                evidenceDao.insertAuditEvent(auditEntity)
            }

        } catch (e: Exception) {
            // Ignorar si no hay red
        }
    }
}
