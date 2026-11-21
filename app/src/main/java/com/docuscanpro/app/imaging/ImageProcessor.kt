package com.docuscanpro.app.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    ENHANCED("Amélioré"),
    BW("Noir & blanc"),
    GRAY("Niveaux de gris"),
    PHOTO("Photo")
}

/** Traitement réel des pixels : rotation, filtres, luminosité, contraste. */
object ImageProcessor {

    /** Décode une image en limitant sa taille et en respectant l'orientation EXIF. */
    fun decodeSampled(context: Context, uri: Uri, maxDim: Int): Bitmap {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: throw IOException("Image introuvable")
        if (bounds.outWidth <= 0) throw IOException("Format d'image non pris en charge")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: throw IOException("Image illisible")
        val big = max(bmp.width, bmp.height)
        if (big > maxDim) {
            val s = maxDim.toFloat() / big
            val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt(), (bmp.height * s).roundToInt(), true)
            if (scaled != bmp) bmp.recycle()
            bmp = scaled
        }
        val degrees = runCatching {
            cr.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        return if (degrees != 0) rotate(bmp, degrees, recycleSource = true) else bmp
    }

    fun rotate(src: Bitmap, degrees: Int, recycleSource: Boolean = false): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (recycleSource && out != src) src.recycle()
        return out
    }

    private fun contrastMatrix(contrast: Float, brightness: Float): ColorMatrix {
        val t = (1f - contrast) * 128f + brightness
        return ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, t,
            0f, contrast, 0f, 0f, t,
            0f, 0f, contrast, 0f, t,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun saturation(s: Float) = ColorMatrix().apply { setSaturation(s) }

    /**
     * Applique rotation + filtre + réglages.
     * @param brightness -1..1   @param contrast 0.5..1.5
     */
    fun process(src: Bitmap, rotation: Int, filter: ScanFilter, brightness: Float, contrast: Float): Bitmap {
        val rotated = rotate(src, rotation)
        val cm = ColorMatrix()
        when (filter) {
            ScanFilter.ORIGINAL -> Unit
            ScanFilter.ENHANCED -> { cm.postConcat(saturation(1.15f)); cm.postConcat(contrastMatrix(1.22f, 14f)) }
            ScanFilter.GRAY -> { cm.postConcat(saturation(0f)); cm.postConcat(contrastMatrix(1.08f, 6f)) }
            ScanFilter.PHOTO -> { cm.postConcat(saturation(1.3f)); cm.postConcat(contrastMatrix(1.08f, 4f)) }
            ScanFilter.BW -> cm.postConcat(saturation(0f))
        }
        cm.postConcat(contrastMatrix(contrast, brightness * 100f))

        val out = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(rotated, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) })
        if (rotated != src) rotated.recycle()
        if (filter == ScanFilter.BW) adaptiveThreshold(out)
        return out
    }

    /**
     * Noir & blanc net : chaque pixel est comparé à la luminosité moyenne de son voisinage
     * (fond estimé par réduction/agrandissement), ce qui supprime les ombres.
     */
    private fun adaptiveThreshold(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val small = Bitmap.createScaledBitmap(bmp, max(1, w / 24), max(1, h / 24), true)
        val bg = Bitmap.createScaledBitmap(small, w, h, true)
        small.recycle()
        val row = IntArray(w)
        val bgRow = IntArray(w)
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            bg.getPixels(bgRow, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val g = row[x] and 0xFF
                val m = (bgRow[x] and 0xFF) + 1
                val r = g.toFloat() / m
                val v = when {
                    r >= 0.88f -> 255
                    r <= 0.62f -> 0
                    else -> ((r - 0.62f) / 0.26f * 255f).toInt()
                }
                row[x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            bmp.setPixels(row, 0, w, 0, y, w, 1)
        }
        bg.recycle()
    }

    fun writeJpeg(bmp: Bitmap, file: File, quality: Int) {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    fun writeThumbnail(source: File, out: File) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, opts)
        var sample = 1
        while (opts.outWidth / (sample * 2) >= 360) sample *= 2
        val bmp = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
        val s = 360f / bmp.width
        val t = if (s < 1f) Bitmap.createScaledBitmap(bmp, 360, (bmp.height * s).roundToInt().coerceAtLeast(1), true) else bmp
        writeJpeg(t, out, 82)
        if (t != bmp) t.recycle()
        bmp.recycle()
    }
}
