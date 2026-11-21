package com.docuscanpro.app.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** Mises en page sur feuille A4 (200 dpi) : carte d'identité recto/verso, plusieurs pages par feuille. */
object Collage {
    const val A4W = 1654
    const val A4H = 2339

    private fun fitInto(bw: Int, bh: Int, box: RectF): RectF {
        val s = min(box.width() / bw, box.height() / bh)
        val w = bw * s
        val h = bh * s
        return RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
    }

    /** Recto (et verso) de la carte à taille réelle (85,6 × 54 mm), centrés sur une page A4. */
    fun idCard(files: List<File>, out: File, quality: Int): File {
        val page = Bitmap.createBitmap(A4W, A4H, Bitmap.Config.ARGB_8888)
        val c = Canvas(page)
        c.drawColor(Color.WHITE)
        val cardW = (85.6 / 25.4 * 200).roundToInt()
        val cardH = (54.0 / 25.4 * 200).roundToInt()
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 30f; typeface = Typeface.DEFAULT_BOLD }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        files.take(2).forEachIndexed { i, f ->
            val raw = BitmapFactory.decodeFile(f.absolutePath) ?: return@forEachIndexed
            val bmp = if (raw.height > raw.width) ImageProcessor.rotate(raw, 90, recycleSource = true) else raw
            val top = 300f + i * (cardH + 260f)
            val left = (A4W - cardW) / 2f
            c.drawText(if (i == 0) "RECTO" else "VERSO", left, top - 24f, label)
            c.drawBitmap(bmp, null, fitInto(bmp.width, bmp.height, RectF(left, top, left + cardW, top + cardH)), paint)
            bmp.recycle()
        }
        ImageProcessor.writeJpeg(page, out, quality)
        page.recycle()
        return out
    }

    /** Regroupe des pages : 2 (l'une sous l'autre) ou 4 (grille 2×2) par feuille A4. */
    fun nUp(pages: List<Bitmap>, perSheet: Int): List<Bitmap> {
        val cols = if (perSheet >= 4) 2 else 1
        val rows = if (perSheet >= 2) 2 else 1
        val margin = 60f
        val gap = 40f
        val cellW = (A4W - 2 * margin - (cols - 1) * gap) / cols
        val cellH = (A4H - 2 * margin - (rows - 1) * gap) / rows
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(210, 214, 220); strokeWidth = 2f }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        return pages.chunked(cols * rows).map { group ->
            val sheet = Bitmap.createBitmap(A4W, A4H, Bitmap.Config.ARGB_8888)
            val c = Canvas(sheet)
            c.drawColor(Color.WHITE)
            group.forEachIndexed { i, bmp ->
                val col = i % cols
                val row = i / cols
                val box = RectF(margin + col * (cellW + gap), margin + row * (cellH + gap), margin + col * (cellW + gap) + cellW, margin + row * (cellH + gap) + cellH)
                val r = fitInto(bmp.width, bmp.height, box)
                c.drawBitmap(bmp, null, r, paint)
                c.drawRect(r, border)
            }
            sheet
        }
    }
}
