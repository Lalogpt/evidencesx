package com.codemaster.evidencex.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.codemaster.evidencex.data.local.EvidenceDatabase
import com.codemaster.evidencex.data.repository.EvidenceRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
            } catch (e: Exception) {
                // Ignorar si no hay red
            }
        }

        val database = EvidenceDatabase.getDatabase(applicationContext)
        val dao = database.evidenceDao()
        val repository = EvidenceRepositoryImpl(applicationContext, dao)

        // 1. Pull latest data from Cloud (Cloud-to-Local)
        try {
            repository.syncPullFromCloud()
        } catch (e: Exception) {
            // Ignorar si no hay red
        }

        // 2. Push pending local tasks to Cloud (Local-to-Cloud)
        val pendingTasks = dao.getPendingSyncTasks()
        if (pendingTasks.isEmpty()) {
            return@withContext Result.success()
        }

        val firestore = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()

        for (task in pendingTasks) {
            try {
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
                            val file = File(evidence.fileUri)
                            if (file.exists()) {
                                val storageRef = storage.reference.child("evidence/${evidence.caseId}/${file.name}")
                                storageRef.putFile(android.net.Uri.fromFile(file)).await()
                            }
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
                }

                dao.updateSyncTaskStatus(task.id, "COMPLETED", task.retryCount, null)
                dao.deleteSyncTask(task.id)

            } catch (e: Exception) {
                android.util.Log.e("SyncWorker", "Error al subir tarea ${task.entityType}: ${e.localizedMessage}", e)
                val newRetry = task.retryCount + 1
                if (newRetry > 5) {
                    dao.updateSyncTaskStatus(task.id, "FAILED", newRetry, e.localizedMessage)
                } else {
                    dao.updateSyncTaskStatus(task.id, "PENDING", newRetry, e.localizedMessage)
                    return@withContext Result.retry()
                }
            }
        }

        Result.success()
    }

    companion object {
        fun enqueueSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
