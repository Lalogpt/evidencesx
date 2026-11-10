package com.codemaster.evidencex

import com.codemaster.evidencex.domain.crypto.CryptoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CryptoUtilsTest {

    @Test
    fun testSha256Determinism() {
        val input = "EVIDENCE_X_FORENSIC_TEST"
        val hash1 = CryptoUtils.sha256(input)
        val hash2 = CryptoUtils.sha256(input)
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // SHA-256 hex string is 64 chars
    }

    @Test
    fun testMetadataCanonicalHashing() {
        val hashA = CryptoUtils.calculateMetadataHash(
            caseId = "CASE-123",
            fileName = "evidence.jpg",
            notes = "Test notes",
            gpsLat = 19.4326,
            gpsLng = -99.1332,
            timestamp = 1711900800000L,
            investigatorId = "PERITO-001"
        )

        val hashB = CryptoUtils.calculateMetadataHash(
            caseId = "CASE-123",
            fileName = "evidence.jpg",
            notes = "Test notes",
            gpsLat = 19.4326,
            gpsLng = -99.1332,
            timestamp = 1711900800000L,
            investigatorId = "PERITO-001"
        )

        assertEquals(hashA, hashB)
    }

    @Test
    fun testAuditChainHashing() {
        val genesis = CryptoUtils.GENESIS_HASH
        val event1Content = "CREATE_CASE:123"
        val event1Hash = CryptoUtils.calculateAuditEventHash(event1Content, genesis)

        val event2Content = "CAPTURE_EVIDENCE:456"
        val event2Hash = CryptoUtils.calculateAuditEventHash(event2Content, event1Hash)

        assertNotEquals(event1Hash, event2Hash)
        assertEquals(64, event2Hash.length)
    }
}
