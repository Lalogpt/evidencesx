package com.codemaster.evidencex.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.evidencex.data.local.EvidenceDatabase
import com.codemaster.evidencex.data.local.entity.AuditEventEntity
import com.codemaster.evidencex.data.local.entity.CaseEntity
import com.codemaster.evidencex.data.local.entity.EvidenceEntity
import com.codemaster.evidencex.data.local.entity.UserEntity
import com.codemaster.evidencex.data.pdf.PdfReportGenerator
import com.codemaster.evidencex.data.repository.EvidenceRepositoryImpl
import com.codemaster.evidencex.data.sync.SyncWorker
import com.google.firebase.firestore.FirebaseFirestore
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

    // Role management: ADMIN can view all cases; PERITO only views their own.
    private val _isAdmin = MutableStateFlow(true)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    private val _currentUserId = MutableStateFlow("ADMIN-01")
    val currentUserId: StateFlow<String> = _currentUserId

    val users: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cases: StateFlow<List<CaseEntity>> = combine(
        repository.allCases,
        _isAdmin,
        _currentUserId
    ) { allCases, admin, userId ->
        if (admin) {
            allCases
        } else {
            allCases.filter { it.investigatorId.equals(userId, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditEvents: StateFlow<List<AuditEventEntity>> = repository.allAuditEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getEvidencesForCase(caseId: String): kotlinx.coroutines.flow.Flow<List<EvidenceEntity>> {
        return repository.getEvidencesForCase(caseId)
    }

    fun setUserRole(isAdminMode: Boolean, userId: String) {
        _isAdmin.value = isAdminMode
        _currentUserId.value = userId
    }

    fun createPeritoUser(name: String, idOrEmail: String, password: String) {
        viewModelScope.launch {
            repository.createPeritoUser(name, idOrEmail, password, "PERITO")
            triggerSync()
        }
    }

    suspend fun authenticateLocalUser(identifier: String, password: String): UserEntity? {
        return repository.authenticateLocalUser(identifier, password)
    }

    fun createCase(caseNumber: String, title: String, description: String, investigatorId: String) {
        viewModelScope.launch {
            repository.createCase(caseNumber, title, description, investigatorId)
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
        viewModelScope.launch {
            try {
                // 1. Pull data from cloud
                repository.syncPullFromCloud()

                // 2. Push pending tasks to cloud immediately
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
                            firestore.collection("cases").document(task.entityId)
                                .set(mapOf("id" to task.entityId, "payload" to task.payloadJson, "syncedAt" to System.currentTimeMillis()))
                                .await()
                        }
                        "EVIDENCE" -> {
                            val evidence = dao.getEvidenceById(task.entityId)
                            if (evidence != null) {
                                firestore.collection("evidences").document(task.entityId)
                                    .set(mapOf(
                                        "id" to evidence.id,
                                        "caseId" to evidence.caseId,
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
                    }
                    dao.updateSyncTaskStatus(task.id, "COMPLETED", task.retryCount, null)
                    dao.deleteSyncTask(task.id)
                }
            } catch (e: Exception) {
                // Ignore sync errors if offline
            }
            SyncWorker.enqueueSync(getApplication())
        }
    }
}
