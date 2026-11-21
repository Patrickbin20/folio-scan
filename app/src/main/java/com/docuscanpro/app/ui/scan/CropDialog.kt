package com.docuscanpro.app.ui.scan

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docuscanpro.app.imaging.DocDetector
import com.docuscanpro.app.ui.state.ScanPage
import com.docuscanpro.app.ui.state.ScanViewModel
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private val Accent = Color(0xFF2F6BFF)

/** Recadrage précis : 4 coins déplaçables, loupe de précision, détection automatique, correction de perspective. */
@Composable
fun CropDialog(scanVm: ScanViewModel, page: ScanPage, onDismiss: () -> Unit, onApply: (FloatArray?) -> Unit) {
    val source by produceState<Pair<Bitmap, FloatArray?>?>(null, page.uri, page.rotation) { value = scanVm.cropSource(page) }
    val quad = remember { mutableStateListOf<Float>() }
    var detectedNone by remember { mutableStateOf(false) }
    LaunchedEffect(source) {
        val s = source ?: return@LaunchedEffect
        if (quad.isEmpty()) {
            quad.addAll((page.crop ?: s.second ?: DocDetector.FULL).toList())
            detectedNone = page.crop == null && s.second == null
        }
    }

    val bars = com.docuscanpro.app.ui.components.rememberSystemBarsPadding()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0D12)).padding(bars)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Annuler", tint = Color.White) }
                Text("Recadrer", color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val q = quad.toFloatArray()
                    val full = q.size == 8 && q.indices.all { abs(q[it] - DocDetector.FULL[it]) < 0.004f }
                    onApply(if (full || q.size != 8) null else q)
                }, enabled = quad.size == 8) { Text("Valider", color = Accent) }
            }
            Text(
                if (detectedNone) "Bords non détectés automatiquement : placez les 4 coins sur les angles de la feuille" else "Ajustez les coins sur les angles de la feuille — la loupe aide à viser précisément",
                color = Color(0xFFB8C0CC), modifier = Modifier.padding(horizontal = 16.dp)
            )
            Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                val s = source
                if (s == null || quad.size != 8) CircularProgressIndicator(color = Accent)
                else CropArea(s.first.asImageBitmap(), quad)
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    val d = source?.second
                    quad.clear(); quad.addAll((d ?: DocDetector.FULL).toList()); detectedNone = d == null
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.AutoFixHigh, null, tint = Color.White); Text("  Auto", color = Color.White)
                }
                OutlinedButton(onClick = { quad.clear(); quad.addAll(DocDetector.FULL.toList()) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.Fullscreen, null, tint = Color.White); Text("  Plein cadre", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CropArea(img: ImageBitmap, quad: MutableList<Float>) {
    val density = LocalDensity.current
    var active by remember { mutableIntStateOf(-1) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val s = min(maxW / img.width, maxH / img.height)
        val w = img.width * s
        val h = img.height * s
        val touch = with(density) { 44.dp.toPx() }
        Canvas(
            Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .pointerInput(w, h) {
                    detectDragGestures(
                        onDragStart = { o ->
                            var best = -1; var bestD = Float.MAX_VALUE
                            for (i in 0 until 4) {
                                val d = hypot(o.x - quad[2 * i] * w, o.y - quad[2 * i + 1] * h)
                                if (d < bestD) { bestD = d; best = i }
                            }
                            active = if (bestD <= touch * 1.6f) best else -1
                        },
                        onDrag = { change, drag ->
                            val i = active
                            if (i >= 0) {
                                quad[2 * i] = (quad[2 * i] + drag.x / w).coerceIn(0f, 1f)
                                quad[2 * i + 1] = (quad[2 * i + 1] + drag.y / h).coerceIn(0f, 1f)
                                change.consume()
                            }
                        },
                        onDragEnd = { active = -1 },
                        onDragCancel = { active = -1 }
                    )
                }
        ) {
            drawImage(img, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            val pts = (0 until 4).map { Offset(quad[2 * it] * size.width, quad[2 * it + 1] * size.height) }
            val poly = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            val outside = Path().apply { fillType = PathFillType.EvenOdd; addRect(Rect(Offset.Zero, size)); addPath(poly) }
            drawPath(outside, Color.Black.copy(alpha = 0.5f))
            drawPath(poly, Accent, style = Stroke(width = 2.5.dp.toPx()))
            pts.forEachIndexed { i, p ->
                drawCircle(Color.White, radius = if (i == active) 14.dp.toPx() else 11.dp.toPx(), center = p)
                drawCircle(Accent, radius = if (i == active) 10.dp.toPx() else 7.dp.toPx(), center = p)
            }
            // Loupe de précision (x3) pendant le déplacement d'un coin
            if (active >= 0) {
                val p = pts[active]
                val lr = 64.dp.toPx()
                val center = Offset(if (p.x < size.width / 2) size.width - lr - 8.dp.toPx() else lr + 8.dp.toPx(), lr + 8.dp.toPx())
                val zoom = 3f
                val srcSide = (lr * 2 / zoom / s).roundToInt().coerceIn(4, min(img.width, img.height))
                val ix = quad[2 * active] * img.width
                val iy = quad[2 * active + 1] * img.height
                val sx = (ix - srcSide / 2f).roundToInt().coerceIn(0, img.width - srcSide)
                val sy = (iy - srcSide / 2f).roundToInt().coerceIn(0, img.height - srcSide)
                val k = lr * 2 / srcSide
                val cross = Offset(center.x - lr + (ix - sx) * k, center.y - lr + (iy - sy) * k)
                val circle = Path().apply { addOval(Rect(center, lr)) }
                clipPath(circle) {
                    drawRect(Color.Black, topLeft = Offset(center.x - lr, center.y - lr), size = Size(lr * 2, lr * 2))
                    drawImage(
                        img,
                        srcOffset = IntOffset(sx, sy),
                        srcSize = IntSize(srcSide, srcSide),
                        dstOffset = IntOffset((center.x - lr).roundToInt(), (center.y - lr).roundToInt()),
                        dstSize = IntSize((lr * 2).roundToInt(), (lr * 2).roundToInt())
                    )
                    drawLine(Accent, Offset(cross.x - 14.dp.toPx(), cross.y), Offset(cross.x + 14.dp.toPx(), cross.y), strokeWidth = 2.dp.toPx())
                    drawLine(Accent, Offset(cross.x, cross.y - 14.dp.toPx()), Offset(cross.x, cross.y + 14.dp.toPx()), strokeWidth = 2.dp.toPx())
                }
                drawCircle(Color.White, radius = lr, center = center, style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}
