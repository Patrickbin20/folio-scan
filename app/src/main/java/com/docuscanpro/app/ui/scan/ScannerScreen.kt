package com.docuscanpro.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.docuscanpro.app.imaging.DocDetector
import com.docuscanpro.app.imaging.ImageProcessor
import com.docuscanpro.app.ui.LocalAppActions
import com.docuscanpro.app.ui.state.LibraryViewModel
import com.docuscanpro.app.ui.state.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

private val Accent = Color(0xFF2F5BFF)
private val Ok = Color(0xFF3DDC97)

enum class ScanMode(val label: String) { SINGLE("Document"), MULTI("Multi-pages"), IDCARD("Carte d'identité") }

/**
 * Scanner maison : aperçu caméra, détection des bords en direct (cadre bleu),
 * capture automatique quand la feuille est stable, capture manuelle, multi-pages.
 */
@Composable
fun ScannerScreen(scanVm: ScanViewModel, vm: LibraryViewModel, initialMode: String, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val actions = LocalAppActions.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askPermission.launch(Manifest.permission.CAMERA) }

    var mode by remember { mutableStateOf(if (initialMode == "idcard") ScanMode.IDCARD else ScanMode.SINGLE) }
    var auto by remember { mutableStateOf(true) }
    var torch by remember { mutableStateOf(false) }
    var quad by remember { mutableStateOf<FloatArray?>(null) }
    var stable by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    val pages = remember { mutableStateListOf<Pair<Uri, FloatArray?>>() }
    var lastThumb by remember { mutableStateOf<Bitmap?>(null) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()).build() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    fun finish() {
        if (pages.isEmpty()) return
        val source = if (mode == ScanMode.IDCARD) "idcard" else "scan"
        scanVm.startScanned(pages.toList(), source, openCrop = mode == ScanMode.SINGLE)
        onDone()
    }

    fun shoot() {
        if (busy) return
        busy = true
        flash = true
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "cap_${System.currentTimeMillis()}.jpg")
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val uri = Uri.fromFile(file)
                        val (q, thumb) = withContext(Dispatchers.Default) {
                            runCatching {
                                val b = ImageProcessor.decodeSampled(context, uri, 1600)
                                val det = DocDetector.detect(b)
                                val t = ImageProcessor.process(b, 0, com.docuscanpro.app.imaging.ScanFilter.ORIGINAL, 0f, 1f, det)
                                val small = Bitmap.createScaledBitmap(t, 120, (120f * t.height / t.width).toInt().coerceAtLeast(1), true)
                                b.recycle(); if (t != small) t.recycle()
                                det to small
                            }.getOrDefault(null to null)
                        }
                        pages.add(uri to q)
                        lastThumb = thumb
                        busy = false
                        stable = 0
                        when {
                            mode == ScanMode.SINGLE -> finish()
                            mode == ScanMode.IDCARD && pages.size >= 2 -> finish()
                            mode == ScanMode.IDCARD -> vm.toast("Retournez la carte : scannez le verso")
                        }
                    }
                }
                override fun onError(exception: ImageCaptureException) { busy = false; vm.toast("Capture impossible : ${exception.message}") }
            })
    }

    // Capture automatique : feuille détectée et immobile ~1 s
    LaunchedEffect(stable, auto, busy) {
        if (auto && !busy && stable >= 4 && quad != null) { delay(150); if (stable >= 4) shoot() }
    }
    LaunchedEffect(flash) { if (flash) { delay(160); flash = false } }
    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!granted) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Autorisez la caméra pour scanner vos documents.", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { askPermission.launch(Manifest.permission.CAMERA) }) { Text("Autoriser la caméra") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { actions.importImages() }) { Text("Importer depuis la galerie") }
            }
        } else {
            // Aperçu caméra + analyse en direct
            BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = 190.dp).statusBarsPadding().padding(top = 64.dp)) {
                val vw = constraints.maxWidth.toFloat()
                val vh = constraints.maxHeight.toFloat()
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val selector43 = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
                            val preview = Preview.Builder().setResolutionSelector(selector43).build().also { it.setSurfaceProvider(view.surfaceProvider) }
                            val analysis = ImageAnalysis.Builder().setResolutionSelector(selector43)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                            var lastRun = 0L
                            analysis.setAnalyzer(analysisExecutor) { image ->
                                val now = System.currentTimeMillis()
                                if (now - lastRun < 220) { image.close(); return@setAnalyzer }
                                lastRun = now
                                try {
                                    val raw = image.toBitmap()
                                    val rot = ImageProcessor.rotate(raw, image.imageInfo.rotationDegrees)
                                    val q = DocDetector.detectLines(rot)
                                    if (rot != raw) rot.recycle()
                                    raw.recycle()
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        val prev = quad
                                        stable = if (q != null && prev != null && q.indices.all { abs(q[it] - prev[it]) < 0.02f }) stable + 1 else 0
                                        quad = q
                                    }
                                } catch (_: Throwable) {
                                } finally { image.close() }
                            }
                            runCatching {
                                provider.unbindAll()
                                camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
                            }.onFailure { vm.toast("Caméra indisponible") }
                        }, ContextCompat.getMainExecutor(ctx))
                        view
                    }
                )
                // Cadre détecté (l'image est centrée au format 3:4)
                Canvas(Modifier.fillMaxSize()) {
                    val cw = min(vw, vh * 3f / 4f)
                    val ch = cw * 4f / 3f
                    val ox = (vw - cw) / 2f
                    val oy = (vh - ch) / 2f
                    val q = quad ?: return@Canvas
                    val pts = (0 until 4).map { Offset(ox + q[2 * it] * cw, oy + q[2 * it + 1] * ch) }
                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                    val color = if (stable >= 2) Ok else Accent
                    drawPath(path, color.copy(alpha = 0.18f))
                    drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
                    pts.forEach { drawCircle(Color.White, 6.dp.toPx(), it); drawCircle(color, 4.dp.toPx(), it) }
                }
            }
            if (flash) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.6f)))

            // Barre du haut
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Outlined.Close, "Fermer", onClose)
                Spacer(Modifier.weight(1f))
                RoundIcon(if (torch) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, "Lampe") { torch = !torch }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(if (auto) Color.White else Color.Black.copy(alpha = 0.5f))
                        .clickable { auto = !auto }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) { Text(if (auto) "Auto" else "Manuel", color = if (auto) Color.Black else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            }
            // Indication
            val hint = when {
                quad == null -> "Placez la feuille dans le champ"
                stable >= 2 -> if (auto) "Ne bougez plus…" else "Document détecté"
                else -> "Document détecté"
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 206.dp).navigationBarsPadding()) {
                Row(Modifier.clip(RoundedCornerShape(17.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (quad != null) Ok else Color.Gray))
                    Spacer(Modifier.width(8.dp))
                    Text(hint, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            // Barre du bas
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xFF0B0C0E)).navigationBarsPadding().padding(top = 14.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ScanMode.entries.forEach { m ->
                        Text(m.label, color = if (m == mode) Color.White else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = pages.isEmpty()) { mode = m }.padding(horizontal = 6.dp, vertical = 6.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1E22)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                                .clickable { actions.importImages() },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Outlined.Image, "Importer depuis la galerie", tint = Color.White) }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val scale by animateFloatAsState(if (busy) 0.85f else 1f, label = "shutter")
                        Box(
                            Modifier.size(78.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).clickable(enabled = !busy) { shoot() }.padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) { Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(Color.White)) }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (pages.isNotEmpty()) {
                            Box(Modifier.size(56.dp).clickable { finish() }) {
                                lastThumb?.let { Image(it.asImageBitmap(), "Terminer", Modifier.size(52.dp).align(Alignment.BottomStart).clip(RoundedCornerShape(10.dp)).border(2.dp, Color.White, RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop) }
                                    ?: Box(Modifier.size(52.dp).align(Alignment.BottomStart).clip(RoundedCornerShape(10.dp)).background(Color.White))
                                Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                                    Text("${pages.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White)
    }
}
