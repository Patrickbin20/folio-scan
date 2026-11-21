package com.docuscanpro.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import com.docuscanpro.app.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Signatures enregistrées (PNG transparents dans le stockage privé). */
class SignatureStore(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "signatures").apply { mkdirs() }

    fun list(): List<File> = dir.listFiles { f -> f.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    suspend fun save(bmp: Bitmap): File = withContext(Dispatchers.IO) {
        val trimmed = trim(bmp)
        val f = File(dir, "sig_${System.currentTimeMillis()}.png")
        f.outputStream().use { trimmed.compress(Bitmap.CompressFormat.PNG, 100, it) }
        f
    }

    fun delete(f: File) { f.delete() }

    /** Importe une signature photographiée : le fond clair devient transparent. */
    suspend fun fromImage(uri: Uri): Bitmap = withContext(Dispatchers.Default) {
        val src = ImageProcessor.decodeSampled(context, uri, 1400)
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        src.recycle()
        for (i in px.indices) {
            val c = px[i]
            val l = ((c shr 16 and 255) * 77 + (c shr 8 and 255) * 150 + (c and 255) * 29) shr 8
            val a = ((200 - l) * 255 / 120).coerceIn(0, 255)
            px[i] = if (a < 25) 0 else (a shl 24) or (c and 0xFFFFFF)
        }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    companion object {
        val INK = Color.rgb(20, 35, 95)

        /** Styles pour la signature générée à partir du nom. */
        val STYLES = listOf("Manuscrite", "Élégante", "Décontractée", "Classique")

        fun typeface(style: Int): Typeface = when (style) {
            0 -> Typeface.create("cursive", Typeface.NORMAL)
            1 -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            2 -> Typeface.create("casual", Typeface.NORMAL)
            else -> Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }

        /** Rend un texte en image transparente (signature stylisée, initiales, date, texte libre). */
        fun renderText(text: String, color: Int = INK, typeface: Typeface = Typeface.DEFAULT, size: Float = 140f): Bitmap {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; this.typeface = typeface; textSize = size }
            val lines = text.ifBlank { " " }.split("\n")
            val fm = p.fontMetrics
            val lineH = fm.descent - fm.ascent
            val w = max(8, (lines.maxOf { p.measureText(it) } + size * 0.4f).roundToInt())
            val h = max(8, (lineH * lines.size + size * 0.2f).roundToInt())
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            lines.forEachIndexed { i, line -> c.drawText(line, size * 0.2f, size * 0.1f - fm.ascent + i * lineH, p) }
            return bmp
        }

        /** Supprime les marges transparentes. */
        fun trim(src: Bitmap): Bitmap {
            val w = src.width
            val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            for (y in 0 until h) for (x in 0 until w) {
                if ((px[y * w + x] ushr 24) > 12) { minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y) }
            }
            if (maxX < 0) return src
            val pad = 10
            val l = max(0, minX - pad); val t = max(0, minY - pad)
            val r = min(w, maxX + pad + 1); val b = min(h, maxY + pad + 1)
            return Bitmap.createBitmap(src, l, t, r - l, b - t)
        }
    }
}
