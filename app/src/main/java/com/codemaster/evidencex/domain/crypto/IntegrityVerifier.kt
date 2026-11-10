package com.codemaster.evidencex.domain.crypto

import com.codemaster.evidencex.data.local.entity.AuditEventEntity
import com.codemaster.evidencex.data.local.entity.EvidenceEntity
import java.io.File

data class VerificationResult(
    val isChainIntact: Boolean,
    val tamperedEvidenceIds: List<String>,
    val verifiedEvidencesCount: Int,
    val verifiedAuditEventsCount: Int
)

object IntegrityVerifier {

    /**
     * Verifica la integridad del sistema forense recalculando los hashes criptográficos
     * de las evidencias y validando la continuidad de la cdena
     */
    fun verifySystemIntegrity(
        evidences: List<EvidenceEntity>,
        auditEvents: List<AuditEventEntity>
    ): VerificationResult {
        val tamperedList = mutableListOf<String>()

        // 1. Verificar el hash SHA-256 de cada archivo de evidencia
        for (evidence in evidences) {
            val file = File(evidence.fileUri)
            if (file.exists()) {
                val recalculatedFileHash = CryptoUtils.calculateFileHash(file)
                val recalculatedMetadataHash = CryptoUtils.calculateMetadataHash(
                    caseId = evidence.caseId,
                    fileName = evidence.fileName,
                    notes = evidence.notes,
                    gpsLat = evidence.gpsLat,
                    gpsLng = evidence.gpsLng,
                    timestamp = evidence.timestamp,
                    investigatorId = evidence.fileName
                )
                val recalculatedEvidenceHash = CryptoUtils.calculateEvidenceHash(recalculatedFileHash, recalculatedMetadataHash)

                if (recalculatedEvidenceHash != evidence.evidenceHash) {
                    tamperedList.add(evidence.id)
                }
            }
        }

        // 2. Verificar la cadena de auditoría (hashes encadenados)
        var isChainValid = true
        var expectedPrevHash = CryptoUtils.GENESIS_HASH

        val sortedAuditEvents = auditEvents.sortedBy { it.timestamp }
        for ((index, event) in sortedAuditEvents.withIndex()) {
            if (index == 0) {
                expectedPrevHash = event.prevEventHash
            } else {
                if (event.prevEventHash != expectedPrevHash) {
                    isChainValid = false
                    break
                }
            }
            expectedPrevHash = event.eventHash
        }

        return VerificationResult(
            isChainIntact = isChainValid && tamperedList.isEmpty(),
            tamperedEvidenceIds = tamperedList,
            verifiedEvidencesCount = evidences.size,
            verifiedAuditEventsCount = auditEvents.size
        )
    }
}
