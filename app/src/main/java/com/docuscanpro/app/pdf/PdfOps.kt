package com.docuscanpro.app.pdf

import android.graphics.Bitmap
import android.graphics.Color
import com.docuscanpro.app.ocr.OcrEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.text.Normalizer
import kotlin.math.hypot
import kotlin.math.max

/** Élément placé sur une page (signature, initiales, date, texte) — coordonnées normalisées de la page affichée. */
data class Overlay(val bitmap: Bitmap, val cx: Float, val cy: Float, val widthFrac: Float, val rotation: Float)

/** Trait à main levée (stylo ou surligneur) — points normalisés x,y alternés. */
data class InkStroke(val points: List<Float>, val color: Int, val widthFrac: Float, val highlighter: Boolean)

data class PageEdits(val overlays: List<Overlay> = emptyList(), val strokes: List<InkStroke> = emptyList())

class WrongPasswordException : Exception("Mot de passe incorrect")

/** Opérations PDF réelles (PdfBox) : le contenu d'origine est conservé (texte, vecteurs). */
object PdfOps {

    /** Repère « page affichée » (origine en bas à gauche, rotation de la page appliquée) → espace PDF. */
    private fun display(page: PDPage): Triple<Matrix, Float, Float> {
        val box = page.cropBox
        val llx = box.lowerLeftX
        val lly = box.lowerLeftY
        val w = box.width
        val h = box.height
        return when (((page.rotation % 360) + 360) % 360) {
            90 -> Triple(Matrix(0f, 1f, -1f, 0f, llx + w, lly), h, w)
            180 -> Triple(Matrix(-1f, 0f, 0f, -1f, llx + w, lly + h), w, h)
            270 -> Triple(Matrix(0f, -1f, 1f, 0f, llx, lly + h), h, w)
            else -> Triple(Matrix(1f, 0f, 0f, 1f, llx, lly), w, h)
        }
    }

    /** Fige les attributs hérités sur la page pour pouvoir la copier dans un autre document. */
    private fun materialize(p: PDPage) {
        p.mediaBox = p.mediaBox
        p.cropBox = p.cropBox
        p.resources = p.resources
        p.rotation = p.rotation
    }

    private fun appendStream(doc: PDDocument, page: PDPage) =
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)

    private val charCache = HashMap<Char, Boolean>()
    /** Garde les caractères affichables par la police standard (accents français inclus). */
    private fun winAnsi(s: String, font: PDType1Font): String {
        val sb = StringBuilder()
        for (ch in s) {
            if (ch == '\n' || ch == '\r' || ch == '\t') { sb.append(' '); continue }
            val ok = charCache.getOrPut(ch) { runCatching { font.encode(ch.toString()) }.isSuccess }
            if (ok) sb.append(ch) else {
                val base = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                if (base.isNotEmpty() && runCatching { font.encode(base) }.isSuccess) sb.append(base)
            }
        }
        return sb.toString()
    }

    fun pageCount(file: File, password: String? = null): Int =
        (if (password == null) PDDocument.load(file) else PDDocument.load(file, password)).use { it.numberOfPages }

    fun merge(inputs: List<File>, out: File) {
        val sources = mutableListOf<PDDocument>()
        PDDocument().use { dest ->
            try {
                val m = PDFMergerUtility()
                inputs.forEach { f -> val d = PDDocument.load(f); sources += d; m.appendDocument(dest, d) }
                dest.save(out)
            } finally { sources.forEach { it.close() } }
        }
    }

    /** Copie les pages [order] (index 0) dans un nouveau PDF, avec rotations supplémentaires éventuelles. */
    fun extract(input: File, order: List<Int>, out: File, extraRotation: Map<Int, Int> = emptyMap()) {
        PDDocument.load(input).use { src ->
            PDDocument().use { dst ->
                order.forEach { i ->
                    val sp = src.getPage(i)
                    materialize(sp)
                    val p = dst.importPage(sp)
                    val extra = extraRotation[i] ?: 0
                    if (extra != 0) p.rotation = (p.rotation + extra) % 360
                }
                dst.save(out)
            }
        }
    }

    /** Recompresse les images trop lourdes (JPEG) — texte et vecteurs intacts. */
    fun compress(input: File, out: File, maxDim: Int, quality: Float) {
        PDDocument.load(input).use { doc ->
            for (page in doc.pages) {
                val res = page.resources ?: continue
                for (name in res.xObjectNames.toList()) {
                    val xo = runCatching { res.getXObject(name) }.getOrNull() as? PDImageXObject ?: continue
                    if (xo.isStencil || xo.cosObject.getDictionaryObject(COSName.SMASK) != null) continue
                    val img = runCatching { xo.image }.getOrNull() ?: continue
                    val big = max(img.width, img.height)
                    val scaled = if (big > maxDim) {
                        val s = maxDim.toFloat() / big
                        Bitmap.createScaledBitmap(img, (img.width * s).toInt().coerceAtLeast(1), (img.height * s).toInt().coerceAtLeast(1), true)
                    } else img
                    val jpg = JPEGFactory.createFromImage(doc, scaled, quality)
                    if (jpg.cosObject.length < xo.cosObject.length) res.put(name, jpg)
                }
            }
            doc.save(out)
        }
    }

    /** Filigrane diagonal répété, semi-transparent, sur toutes les pages. */
    fun watermark(input: File, out: File, text: String) {
        val font = PDType1Font.HELVETICA_BOLD
        val safe = winAnsi(text, font).ifBlank { "COPIE" }
        PDDocument.load(input).use { doc ->
            for (page in doc.pages) {
                val (m, dw, dh) = display(page)
                appendStream(doc, page).use { cs ->
                    cs.saveGraphicsState()
                    cs.transform(m)
                    val gs = PDExtendedGraphicsState()
                    gs.setNonStrokingAlphaConstant(0.18f)
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(0.78f, 0.12f, 0.18f)
                    val fs = dw / 26f
                    val tw = font.getStringWidth(safe) / 1000f * fs + fs * 3
                    val diag = hypot(dw.toDouble(), dh.toDouble()).toFloat()
                    cs.transform(Matrix.getRotateInstance(Math.toRadians(30.0), dw / 2, dh / 2))
                    var row = 0
                    var y = -diag / 2
                    while (y < diag / 2) {
                        var x = -diag / 2 - tw + (row % 2) * tw / 2
                        while (x < diag / 2) {
                            cs.beginText(); cs.setFont(font, fs); cs.newLineAtOffset(x, y); cs.showText(safe); cs.endText()
                            x += tw
                        }
                        y += fs * 3.2f; row++
                    }
                    cs.restoreGraphicsState()
                }
            }
            doc.save(out)
        }
    }

    fun protect(input: File, out: File, password: String) {
        PDDocument.load(input).use { doc ->
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            doc.save(out)
        }
    }

    fun unlock(input: File, out: File, password: String) {
        val doc = try { PDDocument.load(input, password) } catch (e: InvalidPasswordException) { throw WrongPasswordException() }
        doc.use {
            it.isAllSecurityToBeRemoved = true
            it.save(out)
        }
    }

    /** Applique signatures, textes, dates, traits et surlignages sur les pages (nouveau fichier). */
    fun stamp(input: File, out: File, edits: Map<Int, PageEdits>) {
        PDDocument.load(input).use { doc ->
            edits.forEach { (index, e) ->
                if (index !in 0 until doc.numberOfPages) return@forEach
                val page = doc.getPage(index)
                val (m, dw, dh) = display(page)
                appendStream(doc, page).use { cs ->
                    cs.saveGraphicsState()
                    cs.transform(m)
                    e.strokes.forEach { s ->
                        if (s.points.size < 4) return@forEach
                        cs.saveGraphicsState()
                        if (s.highlighter) {
                            val gs = PDExtendedGraphicsState()
                            gs.setStrokingAlphaConstant(0.38f)
                            cs.setGraphicsStateParameters(gs)
                        }
                        cs.setStrokingColor(Color.red(s.color) / 255f, Color.green(s.color) / 255f, Color.blue(s.color) / 255f)
                        cs.setLineWidth(s.widthFrac * dw)
                        cs.setLineCapStyle(if (s.highlighter) 2 else 1)
                        cs.setLineJoinStyle(1)
                        cs.moveTo(s.points[0] * dw, (1f - s.points[1]) * dh)
                        var i = 2
                        while (i + 1 < s.points.size) { cs.lineTo(s.points[i] * dw, (1f - s.points[i + 1]) * dh); i += 2 }
                        cs.stroke()
                        cs.restoreGraphicsState()
                    }
                    e.overlays.forEach { o ->
                        val img = LosslessFactory.createFromImage(doc, o.bitmap)
                        val w = o.widthFrac * dw
                        val h = w * o.bitmap.height / o.bitmap.width
                        val cx = o.cx * dw
                        val cy = (1f - o.cy) * dh
                        cs.saveGraphicsState()
                        cs.transform(Matrix.getRotateInstance(Math.toRadians(-o.rotation.toDouble()), cx, cy))
                        cs.drawImage(img, -w / 2, -h / 2, w, h)
                        cs.restoreGraphicsState()
                    }
                    cs.restoreGraphicsState()
                }
            }
            doc.save(out)
        }
    }

    /** Ajoute une couche de texte invisible (OCR) : le PDF devient sélectionnable et consultable. */
    fun addTextLayer(input: File, out: File, pages: List<OcrEngine.PageText?>) {
        val font = PDType1Font.HELVETICA
        PDDocument.load(input).use { doc ->
            pages.forEachIndexed { index, pt ->
                if (pt == null || pt.lines.isEmpty() || index >= doc.numberOfPages) return@forEachIndexed
                val page = doc.getPage(index)
                val (m, dw, dh) = display(page)
                appendStream(doc, page).use { cs ->
                    cs.saveGraphicsState()
                    cs.transform(m)
                    cs.beginText()
                    cs.setRenderingMode(RenderingMode.NEITHER)
                    pt.lines.forEach { line ->
                        val str = winAnsi(line.text, font)
                        if (str.isBlank()) return@forEach
                        val bh = line.box.height() * dh
                        val fs = (bh * 0.85f).coerceAtLeast(1f)
                        val tw = font.getStringWidth(str) / 1000f * fs
                        if (tw <= 0f) return@forEach
                        val scaling = (line.box.width() * dw / tw * 100f).coerceIn(10f, 1000f)
                        cs.setFont(font, fs)
                        cs.setHorizontalScaling(scaling)
                        cs.setTextMatrix(Matrix.getTranslateInstance(line.box.left * dw, (1f - line.box.bottom) * dh + bh * 0.18f))
                        cs.showText(str)
                    }
                    cs.endText()
                    cs.restoreGraphicsState()
                }
            }
            doc.save(out)
        }
    }

    /** Texte déjà présent dans le PDF (PDF numériques), page par page. */
    fun extractText(input: File): List<String> = PDDocument.load(input).use { doc ->
        val stripper = PDFTextStripper()
        (1..doc.numberOfPages).map { p ->
            stripper.startPage = p
            stripper.endPage = p
            runCatching { stripper.getText(doc) }.getOrDefault("").trim()
        }
    }
}
