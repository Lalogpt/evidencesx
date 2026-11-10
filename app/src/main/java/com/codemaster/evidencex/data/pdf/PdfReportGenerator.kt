package com.codemaster.evidencex.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.codemaster.evidencex.data.local.entity.AuditEventEntity
import com.codemaster.evidencex.data.local.entity.CaseEntity
import com.codemaster.evidencex.data.local.entity.EvidenceEntity
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object PdfReportGenerator {

    fun generateCaseReport(
        context: Context,
        case: CaseEntity,
        evidences: List<EvidenceEntity>,
        auditEvents: List<AuditEventEntity>
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size at 72 DPI
        val paint = Paint()

        // --- PAGE 1: Cover & Summary ---
        var page = pdfDocument.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        // Title
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("EVIDENCE X - INFORME PERICIAL FORENSE", 40f, 60f, paint)

        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Sistema de Investigaciones Digitales con Cadena de Custodia", 40f, 85f, paint)

        // Divider
        paint.strokeWidth = 2f
        canvas.drawLine(40f, 100f, 555f, 100f, paint)

        // Case Details
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Datos del Expediente:", 40f, 130f, paint)

        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Número de Expediente: ${case.caseNumber}", 40f, 155f, paint)
        canvas.drawText("Título: ${case.title}", 40f, 175f, paint)
        canvas.drawText("Descripción: ${case.description}", 40f, 195f, paint)
        canvas.drawText("Investigador ID: ${case.investigatorId}", 40f, 215f, paint)
        canvas.drawText("Estado: ${case.status}", 40f, 235f, paint)
        val dateStr = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(case.createdAt).atZone(ZoneOffset.UTC))
        canvas.drawText("Fecha de Apertura (UTC): $dateStr", 40f, 255f, paint)

        // Summary of Evidences with GPS Locations & Hashes
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Resumen de Evidencias Levantadas (${evidences.size}):", 40f, 295f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        var yPos = 325f
        evidences.forEachIndexed { index, evidence ->
            if (yPos > 760f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPos = 60f
            }
            val sensitiveTag = if (evidence.sensitive) "[⚠️ SENSIBLE] " else ""
            val gpsStr = String.format(Locale.US, "Lat: %.4f, Lng: %.4f", evidence.gpsLat, evidence.gpsLng)
            canvas.drawText("${index + 1}. $sensitiveTag${evidence.fileName}", 40f, yPos, paint)
            canvas.drawText("    GPS: $gpsStr | Hash: ${evidence.evidenceHash.take(24)}...", 40f, yPos + 14f, paint)
            yPos += 34f
        }

        pdfDocument.finishPage(page)

        // --- PAGE 2: Audit Trail & Global Hash ---
        val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        canvas = page2.canvas

        paint.textSize = 16f
        paint.isFakeBoldText = true
        canvas.drawText("Bitácora de Auditoría y Cadena de Custodia", 40f, 60f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        yPos = 90f
        val globalHash = auditEvents.lastOrNull()?.eventHash ?: "N/A"
        canvas.drawText("Hash Global de Integridad (SHA-256):", 40f, yPos, paint)
        paint.isFakeBoldText = true
        canvas.drawText(globalHash, 40f, yPos + 15f, paint)
        paint.isFakeBoldText = false
        yPos += 45f

        auditEvents.take(15).forEach { event ->
            if (yPos > 780f) return@forEach
            val eventDate = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(event.timestamp).atZone(ZoneOffset.UTC))
            canvas.drawText("[$eventDate] ${event.eventType}: ${event.eventDetails}", 40f, yPos, paint)
            canvas.drawText("Hash: ${event.eventHash.take(24)}...", 40f, yPos + 12f, paint)
            yPos += 32f
        }

        pdfDocument.finishPage(page2)

        // Save to file
        val reportsDir = File(context.filesDir, "reports").apply { mkdirs() }
        val pdfFile = File(reportsDir, "Informe_Pericial_${case.caseNumber}.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()

        return pdfFile
    }
}
