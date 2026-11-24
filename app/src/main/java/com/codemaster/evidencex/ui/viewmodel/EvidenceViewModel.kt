package com.codemaster.evidencex.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.evidencex.data.local.EvidenceDatabase
import com.codemaster.evidencex.data.local.entity.AuditEventEntity
import com.codemaster.evidencex.data.local.entity.CaseEntity
import com.codemaster.evidencex.data.local.entity.ChatMessageEntity
import com.codemaster.evidencex.data.local.entity.EvidenceEntity
import com.codemaster.evidencex.data.local.entity.UserEntity
import com.codemaster.evidencex.data.pdf.PdfReportGenerator
import com.codemaster.evidencex.data.repository.EvidenceRepositoryImpl
import com.codemaster.evidencex.data.sync.SyncWorker
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class EvidenceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = EvidenceDatabase.getDatabase(application)
    private val repository = EvidenceRepositoryImpl(application, database.evidenceDao())

    init {
        startRealtimeSync()
        seedDefaultAdmins()
    }

    // Role management: SUPER_ADMIN (sees all), ADMIN_AREA (sees own area), PERITO (sees own cases)
    private val _currentUserRole = MutableStateFlow("SUPER_ADMIN")
    val currentUserRole: StateFlow<String> = _currentUserRole

    private val _currentUserId = MutableStateFlow("SUPER-01")
    val currentUserId: StateFlow<String> = _currentUserId

    private val _currentArea = MutableStateFlow("General")
    val currentArea: StateFlow<String> = _currentArea

    val users: StateFlow<List<UserEntity>> = combine(
        repository.allUsers,
        _currentUserRole,
        _currentArea,
        _currentUserId
    ) { allUsers, role, area, userId ->
        when (role) {
            "SUPER_ADMIN" -> allUsers
            "ADMIN_AREA" -> allUsers.filter { it.area.equals(area, ignoreCase = true) || it.id.equals(userId, ignoreCase = true) }
            else -> allUsers.filter { it.id.equals(userId, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cases: StateFlow<List<CaseEntity>> = combine(
        repository.allCases,
        _currentUserRole,
        _currentArea,
        _currentUserId
    ) { allCases, role, area, userId ->
        when (role) {
            "SUPER_ADMIN" -> allCases
            "ADMIN_AREA" -> allCases.filter { it.area.equals(area, ignoreCase = true) }
            else -> allCases.filter { it.investigatorId.equals(userId, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditEvents: StateFlow<List<AuditEventEntity>> = repository.allAuditEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getMessagesBetween(userId1: String, userId2: String): Flow<List<ChatMessageEntity>> {
        return repository.getMessagesBetween(userId1, userId2)
    }

    fun sendMessage(senderId: String, recipientId: String, area: String, messageText: String) {
        viewModelScope.launch {
            repository.sendMessage(senderId, recipientId, area, messageText)
            triggerSync()
        }
    }

    private fun seedDefaultAdmins() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val defaultAdmins = listOf(
                    UserEntity("superadmin@evidencex.com", "Super Administrador", "superadmin@evidencex.com", "super123", "SUPER_ADMIN", "General", true),
                    UserEntity("admin.balistica@evidencex.com", "Admin Balística", "admin.balistica@evidencex.com", "balistica123", "ADMIN_AREA", "Balística", true),
                    UserEntity("admin.informatica@evidencex.com", "Admin Informática", "admin.informatica@evidencex.com", "informatica123", "ADMIN_AREA", "Informática", true),
                    UserEntity("admin.dactilo@evidencex.com", "Admin Dactiloscopia", "admin.dactilo@evidencex.com", "dactilo123", "ADMIN_AREA", "Dactiloscopia", true)
                )
                for (admin in defaultAdmins) {
                    repository.createPeritoUser(admin.name, admin.id, admin.password, admin.area, admin.role)
                }
            } catch (e: Exception) {}
        }
    }

    private fun startRealtimeSync() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val firestore = FirebaseFirestore.getInstance()

                firestore.collection("cases").addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try { repository.syncPullFromCloud() } catch (e: Exception) {}
                        }
                    }
                }

                firestore.collection("evidences").addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try { repository.syncPullFromCloud() } catch (e: Exception) {}
                        }
                    }
                }

                firestore.collection("chat_messages").addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try { repository.syncPullFromCloud() } catch (e: Exception) {}
                        }
                    }
                }

                repository.syncPullFromCloud()
            } catch (e: Exception) {}
        }
    }

    suspend fun getEvidenceAndCaseDetails(evidenceId: String): Pair<EvidenceEntity?, CaseEntity?> {
        val dao = database.evidenceDao()
        val evidence = dao.getEvidenceById(evidenceId)
        val case = if (evidence != null) dao.getCaseById(evidence.caseId) else null
        return Pair(evidence, case)
    }

    fun setUserCredentials(role: String, userId: String, area: String) {
        _currentUserRole.value = role
        _currentUserId.value = userId
        _currentArea.value = area
    }

    fun createPeritoUser(name: String, idOrEmail: String, password: String, area: String) {
        viewModelScope.launch {
            repository.createPeritoUser(name, idOrEmail, password, area, "PERITO")
            triggerSync()
        }
    }

    fun createAreaAdminUser(name: String, idOrEmail: String, password: String, area: String) {
        viewModelScope.launch {
            repository.createPeritoUser(name, idOrEmail, password, area, "ADMIN_AREA")
            triggerSync()
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            repository.deleteUser(userId)
            triggerSync()
        }
    }

    suspend fun authenticateLocalUser(identifier: String, password: String): UserEntity? {
        return repository.authenticateLocalUser(identifier, password)
    }

    fun createCase(caseNumber: String, title: String, description: String, investigatorId: String, area: String) {
        viewModelScope.launch {
            repository.createCase(caseNumber, title, description, investigatorId, area)
            triggerSync()
        }
    }

    fun updateCase(caseId: String, title: String, description: String, status: String, adminUserId: String) {
        viewModelScope.launch {
            repository.updateCase(caseId, title, description, status, adminUserId)
            triggerSync()
        }
    }

    fun deleteCase(caseId: String, adminUserId: String) {
        viewModelScope.launch {
            repository.deleteCase(caseId, adminUserId)
            triggerSync()
        }
    }

    fun getEvidencesForCase(caseId: String): Flow<List<EvidenceEntity>> {
        return repository.getEvidencesForCase(caseId)
    }

    fun captureEvidence(
        caseId: String,
        sourceFile: File,
        fileName: String,
        notes: String,
        lat: Double,
        lng: Double,
        investigatorId: String,
        sensitive: Boolean
    ) {
        viewModelScope.launch {
            repository.captureEvidence(caseId, sourceFile, fileName, notes, lat, lng, investigatorId, sensitive)
            triggerSync()
        }
    }

    fun generatePdfReport(case: CaseEntity, evidences: List<EvidenceEntity>, auditEvents: List<AuditEventEntity>): File {
        return PdfReportGenerator.generateCaseReport(getApplication(), case, evidences, auditEvents)
    }

    fun triggerSync() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.syncPullFromCloud()

                val dao = database.evidenceDao()
                val pendingTasks = dao.getPendingSyncTasks()
                val firestore = FirebaseFirestore.getInstance()

                for (task in pendingTasks) {
                    dao.updateSyncTaskStatus(task.id, "IN_PROGRESS", task.retryCount, null)
                    when (task.entityType) {
                        "USER" -> {
                            firestore.collection("users").document(task.entityId)
                                .set(mapOf("id" to task.entityId, "payload" to task.payloadJson, "syncedAt" to System.currentTimeMillis()))
                                .await()
                        }
                        "CASE" -> {
                            val caseEntity = dao.getCaseById(task.entityId)
                            if (caseEntity != null) {
                                firestore.collection("cases").document(task.entityId)
                                    .set(mapOf(
                                        "id" to caseEntity.id,
                                        "caseNumber" to caseEntity.caseNumber,
                                        "title" to caseEntity.title,
                                        "description" to caseEntity.description,
                                        "investigatorId" to caseEntity.investigatorId,
                                        "area" to caseEntity.area,
                                        "status" to caseEntity.status,
                                        "createdAt" to caseEntity.createdAt,
                                        "updatedAt" to caseEntity.updatedAt
                                    ))
                                    .await()
                            }
                        }
                        "EVIDENCE" -> {
                            val evidence = dao.getEvidenceById(task.entityId)
                            if (evidence != null) {
                                firestore.collection("evidences").document(task.entityId)
                                    .set(mapOf(
                                        "id" to evidence.id,
                                        "caseId" to evidence.caseId,
                                        "fileName" to evidence.fileName,
                                        "notes" to evidence.notes,
                                        "evidenceHash" to evidence.evidenceHash,
                                        "metadataHash" to evidence.metadataHash,
                                        "fileHash" to evidence.fileHash,
                                        "gpsLat" to evidence.gpsLat,
                                        "gpsLng" to evidence.gpsLng,
                                        "timestamp" to evidence.timestamp,
                                        "sensitive" to evidence.sensitive
                                    ))
                                    .await()
                            }
                        }
                        "AUDIT" -> {
                            firestore.collection("audit_events").document(task.entityId)
                                .set(mapOf("id" to task.entityId, "payload" to task.payloadJson, "timestamp" to System.currentTimeMillis()))
                                .await()
                        }
                        "CHAT" -> {
                            firestore.collection("chat_messages").document(task.entityId)
                                .set(mapOf("id" to task.entityId, "payload" to task.payloadJson, "timestamp" to System.currentTimeMillis()))
                                .await()
                        }
                    }
                    dao.updateSyncTaskStatus(task.id, "COMPLETED", task.retryCount, null)
                    dao.deleteSyncTask(task.id)
                }
            } catch (e: Exception) {
                android.util.Log.e("TriggerSync", "Error en sincronización manual: ${e.localizedMessage}", e)
            }
            SyncWorker.enqueueSync(getApplication())
        }
    }
}
