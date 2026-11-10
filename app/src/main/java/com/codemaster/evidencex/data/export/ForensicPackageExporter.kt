package com.codemaster.evidencex.data.export

import android.content.Context
import com.codemaster.evidencex.data.local.entity.AuditEventEntity
import com.codemaster.evidencex.data.local.entity.CaseEntity
import com.codemaster.evidencex.data.local.entity.EvidenceEntity
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ForensicPackageExporter {

    /**
     * Exporta un paquete forense completo del caso en un archivo comprimido seguro `.evx`,
     * conteniendo metadatos del expediente, manifiesto de evidencias con hashes y la bitácora de auditoría.
     */
    fun exportForensicPackage(
        context: Context,
        case: CaseEntity,
        evidences: List<EvidenceEntity>,
        auditEvents: List<AuditEventEntity>
    ): File {
        val exportDir = File(context.filesDir, "forensic_packages").apply { mkdirs() }
        val evxFile = File(exportDir, "Expediente_${case.caseNumber}_${System.currentTimeMillis()}.evx")

        ZipOutputStream(FileOutputStream(evxFile)).use { zos ->
            // 1. Escribir metadatos del caso en JSON
            zos.putNextEntry(ZipEntry("case_metadata.json"))
            val caseJson = """
                {
                    "id": "${case.id}",
                    "caseNumber": "${case.caseNumber}",
                    "title": "${case.title}",
                    "description": "${case.description}",
                    "investigatorId": "${case.investigatorId}",
                    "createdAt": ${case.createdAt}
                }
            """.trimIndent()
            zos.write(caseJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Escribir manifiesto de evidencias con hashes SHA-256 y GPS
            zos.putNextEntry(ZipEntry("evidences_manifest.json"))
            val evidencesJson = buildString {
                append("[\n")
                evidences.forEachIndexed { index, ev ->
                    append("  {\n")
                    append("    \"id\": \"${ev.id}\",\n")
                    append("    \"fileName\": \"${ev.fileName}\",\n")
                    append("    \"fileHash\": \"${ev.fileHash}\",\n")
                    append("    \"metadataHash\": \"${ev.metadataHash}\",\n")
                    append("    \"evidenceHash\": \"${ev.evidenceHash}\",\n")
                    append("    \"gpsLat\": ${ev.gpsLat},\n")
                    append("    \"gpsLng\": ${ev.gpsLng},\n")
                    append("    \"sensitive\": ${ev.sensitive}\n")
                    append("  }${if (index < evidences.size - 1) "," else ""}\n")
                }
                append("]")
            }
            zos.write(evidencesJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. Escribir bitácora de auditoría encadenada
            zos.putNextEntry(ZipEntry("audit_chain.json"))
            val auditJson = buildString {
                append("[\n")
                auditEvents.forEachIndexed { index, audit ->
                    append("  {\n")
                    append("    \"id\": \"${audit.id}\",\n")
                    append("    \"eventType\": \"${audit.eventType}\",\n")
                    append("    \"prevEventHash\": \"${audit.prevEventHash}\",\n")
                    append("    \"eventHash\": \"${audit.eventHash}\"\n")
                    append("  }${if (index < auditEvents.size - 1) "," else ""}\n")
                }
                append("]")
            }
            zos.write(auditJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        return evxFile
    }
}
