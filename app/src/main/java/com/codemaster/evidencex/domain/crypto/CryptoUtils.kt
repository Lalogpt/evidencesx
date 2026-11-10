package com.codemaster.evidencex.domain.crypto

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object CryptoUtils {

    const val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"

    fun generateUuid(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Calculates SHA-256 hash of a file by reading in constant memory blocks (8KB)
     * to support arbitrarily large evidence files (photos, audio, video).
     */
    fun calculateFileHash(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().toHex()
    }

    /**
     * Generates a strict canonical representation of metadata for hashing:
     * - Fixed order of fields
     * - UTF-8 encoding
     * - ISO 8601 UTC timestamp
     * - Fixed GPS precision (6 decimals)
     */
    fun calculateMetadataHash(
        caseId: String,
        fileName: String,
        notes: String,
        gpsLat: Double,
        gpsLng: Double,
        timestamp: Long,
        investigatorId: String
    ): String {
        val formattedLat = String.format(Locale.US, "%.6f", gpsLat)
        val formattedLng = String.format(Locale.US, "%.6f", gpsLng)
        val utcTime = DateTimeFormatter.ISO_INSTANT
            .format(Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC))

        // Canonical string with strict alphabetical / defined key-value order
        val canonicalString = buildString {
            append("case_id:").append(caseId).append(";")
            append("file_name:").append(fileName).append(";")
            append("investigator_id:").append(investigatorId).append(";")
            append("lat:").append(formattedLat).append(";")
            append("lng:").append(formattedLng).append(";")
            append("notes:").append(notes.trim()).append(";")
            append("timestamp:").append(utcTime)
        }

        return sha256(canonicalString)
    }

    /**
     * Combines file hash and metadata hash into the definitive evidence chain hash.
     */
    fun calculateEvidenceHash(fileHash: String, metadataHash: String): String {
        val combined = "$fileHash$metadataHash"
        return sha256(combined)
    }

    /**
     * Computes chained audit event hash: SHA-256(eventContent || prevEventHash)
     */
    fun calculateAuditEventHash(eventContent: String, prevEventHash: String): String {
        val combined = "$eventContent$prevEventHash"
        return sha256(combined)
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.toHex()
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
