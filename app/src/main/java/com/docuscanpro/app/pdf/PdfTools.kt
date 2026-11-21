package com.docuscanpro.app.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

/** Lecture et rendu de PDF avec le moteur natif d'Android (PdfRenderer). */
object PdfTools {

    fun pageCount(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        }

    private fun renderPage(renderer: PdfRenderer, index: Int, width: Int): Bitmap {
        renderer.openPage(index).use { page ->
            val height = (width.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        }
    }

    fun renderPage(pdf: File, index: Int, width: Int): Bitmap =
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r -> renderPage(r, index, width) }
        }

    fun renderThumbnail(pdf: File, out: File) {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                val bmp = renderPage(r, 0, 360)
                out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                bmp.recycle()
            }
        }
    }

    /** Rend chaque page en JPEG (≈ 200 dpi pour une page A4). */
    fun renderPagesToJpeg(pdf: File, dir: File, baseName: String, width: Int = 1654): List<File> {
        dir.mkdirs()
        val result = mutableListOf<File>()
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                val n = r.pageCount
                for (i in 0 until n) {
                    val bmp = renderPage(r, i, width)
                    val name = if (n == 1) "$baseName.jpg" else "${baseName}_p${i + 1}.jpg"
                    val f = File(dir, name)
                    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    bmp.recycle()
                    result += f
                }
            }
        }
        return result
    }
}

/** Session de lecture d'un PDF ouverte pendant l'affichage (rendu à la demande, thread-safe). */
class PdfSession(file: File) : Closeable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val mutex = Mutex()
    private var closed = false
    val pageCount: Int = renderer.pageCount

    suspend fun aspect(index: Int): Float = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (closed) 0.707f else renderer.openPage(index).use { it.width.toFloat() / it.height }
        }
    }

    suspend fun render(index: Int, width: Int): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (closed) return@withContext null
            renderer.openPage(index).use { page ->
                val h = (width.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    override fun close() {
        closed = true
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}
