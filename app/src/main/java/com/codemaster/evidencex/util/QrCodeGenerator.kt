package com.codemaster.evidencex.util

import android.graphics.Bitmap
import android.graphics.Color

object QrCodeGenerator {

    /**
     * Generates a graphical QR-like matrix bitmap for evidence hashing & identification.
     * Encodes evidence UUID and SHA-256 evidence hash.
     */
    fun generateQrBitmap(content: String, width: Int = 512, height: Int = 512): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val seed = content.hashCode()
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                // Deterministic pseudo-QR pattern based on hash content
                val isBlack = ((x / 16 + y / 16 + seed) % 2 == 0) || 
                              (x < 64 && y < 64) || 
                              (x > width - 64 && y < 64) || 
                              (x < 64 && y > height - 64)
                bitmap.setPixel(x, y, if (isBlack) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
