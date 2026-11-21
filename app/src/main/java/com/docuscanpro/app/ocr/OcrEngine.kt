package com.docuscanpro.app.ocr

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Reconnaissance de texte sur l'appareil (Google ML Kit, alphabet latin). */
class OcrEngine {
    data class Line(val text: String, val box: RectF)          // boîte normalisée 0..1
    data class PageText(val text: String, val lines: List<Line>)

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bmp: Bitmap): PageText {
        val result = recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val b = l.boundingBox ?: return@mapNotNull null
            Line(l.text, RectF(b.left / w, b.top / h, b.right / w, b.bottom / h))
        }
        return PageText(result.text, lines)
    }
}
