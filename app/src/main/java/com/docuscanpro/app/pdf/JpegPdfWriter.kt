package com.docuscanpro.app.pdf

import android.graphics.BitmapFactory
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale

/**
 * Écrit un PDF dont chaque page contient une image JPEG intégrée telle quelle (filtre DCTDecode).
 * Avantage : fichiers légers et qualité intacte (pas de recompression, contrairement à PdfDocument).
 */
object JpegPdfWriter {

    private class CountingStream(private val os: OutputStream) : OutputStream() {
        var count = 0L
            private set
        override fun write(b: Int) { os.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { os.write(b, off, len); count += len }
        override fun flush() = os.flush()
        override fun close() = os.close()
    }

    private fun f(v: Double) = String.format(Locale.US, "%.2f", v)

    fun write(pages: List<File>, out: File) {
        val n = pages.size
        val total = 3 + 3 * n
        val offsets = LongArray(total)
        CountingStream(BufferedOutputStream(FileOutputStream(out))).use { o ->
            fun w(s: String) = o.write(s.toByteArray(Charsets.ISO_8859_1))
            w("%PDF-1.4\n%âãÏÓ\n")
            offsets[1] = o.count
            w("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
            offsets[2] = o.count
            val kids = (0 until n).joinToString(" ") { "${3 + 3 * it} 0 R" }
            w("2 0 obj\n<< /Type /Pages /Count $n /Kids [$kids] >>\nendobj\n")

            pages.forEachIndexed { i, file ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                val iw = opts.outWidth
                val ih = opts.outHeight
                if (iw <= 0 || ih <= 0) throw IOException("Image invalide")
                // Largeur A4 (595 pt), hauteur proportionnelle à l'image
                val pw = 595.28
                val ph = pw * ih / iw
                val pid = 3 + 3 * i
                offsets[pid] = o.count
                w("$pid 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f(pw)} ${f(ph)}] " +
                    "/Resources << /XObject << /Im$i ${pid + 1} 0 R >> >> /Contents ${pid + 2} 0 R >>\nendobj\n")
                val bytes = file.readBytes()
                offsets[pid + 1] = o.count
                w("${pid + 1} 0 obj\n<< /Type /XObject /Subtype /Image /Width $iw /Height $ih " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${bytes.size} >>\nstream\n")
                o.write(bytes)
                w("\nendstream\nendobj\n")
                val cs = "q ${f(pw)} 0 0 ${f(ph)} 0 0 cm /Im$i Do Q"
                offsets[pid + 2] = o.count
                w("${pid + 2} 0 obj\n<< /Length ${cs.length} >>\nstream\n$cs\nendstream\nendobj\n")
            }

            val xref = o.count
            val sb = StringBuilder("xref\n0 $total\n0000000000 65535 f \n")
            for (k in 1 until total) sb.append(String.format(Locale.US, "%010d 00000 n \n", offsets[k]))
            w(sb.toString())
            w("trailer\n<< /Size $total /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }
    }
}
