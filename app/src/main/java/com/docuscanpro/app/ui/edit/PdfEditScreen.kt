package com.docuscanpro.app.ui.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.docuscanpro.app.data.SignatureStore
import com.docuscanpro.app.pdf.InkStroke
import com.docuscanpro.app.pdf.Overlay
import com.docuscanpro.app.pdf.PageEdits
import com.docuscanpro.app.pdf.PdfSession
import com.docuscanpro.app.ui.components.ConfirmDialog
import com.docuscanpro.app.ui.components.FileImage
import com.docuscanpro.app.ui.components.TextInputDialog
import com.docuscanpro.app.ui.state.LibraryViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

enum class EditTool { MOVE, PEN, HIGHLIGHT }

data class EditItem(val id: Long, val page: Int, val bitmap: Bitmap, val cx: Float, val cy: Float, val w: Float, val rot: Float)
data class PageStroke(val id: Long, val page: Int, val stroke: InkStroke)

/** État de l'éditeur conservé pendant la navigation (ex. création d'une signature). */
class EditState : ViewModel() {
    val items = mutableStateListOf<EditItem>()
    val strokes = mutableStateListOf<PageStroke>()
    val history = mutableListOf<Long>()
    var page by mutableIntStateOf(0)
    var selected by mutableStateOf<Long?>(null)
    var tool by mutableStateOf(EditTool.MOVE)
    var preview by mutableStateOf(false)
    var penColor by mutableStateOf(Color(0xFF14235F))
    var hlColor by mutableStateOf(Color(0xFFFFD60A))
    private var next = 1L
    fun newId() = next++

    fun add(bmp: Bitmap, w: Float, cy: Float = 0.5f) {
        val it = EditItem(newId(), page, bmp, 0.5f, cy, w, 0f)
        items.add(it); history.add(it.id); selected = it.id; tool = EditTool.MOVE
    }
    fun update(id: Long, f: (EditItem) -> EditItem) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = f(items[i])
    }
    fun remove(id: Long) { items.removeAll { it.id == id }; strokes.removeAll { it.id == id }; history.remove(id); if (selected == id) selected = null }
    fun undo() { history.removeLastOrNull()?.let { remove(it) } }
    val hasChanges get() = items.isNotEmpty() || strokes.isNotEmpty()

    fun edits(): Map<Int, PageEdits> {
        val pages = (items.map { it.page } + strokes.map { it.page }).toSet()
        return pages.associateWith { p ->
            PageEdits(
                overlays = items.filter { it.page == p }.map { Overlay(it.bitmap, it.cx, it.cy, it.w, it.rot) },
                strokes = strokes.filter { it.page == p }.map { it.stroke }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditScreen(id: Long, mode: String, vm: LibraryViewModel, onBack: () -> Unit, onOpenSignatures: () -> Unit, onSaved: (Long) -> Unit) {
    val st: EditState = viewModel()
    val docFlow = remember(id) { vm.observeDocument(id) }
    val doc by docFlow.collectAsState(initial = null)
    val settings by vm.settings.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }
    val path = doc?.filePath
    val session by produceState<PdfSession?>(null, path) {
        value = path?.let { runCatching { PdfSession(File(it)) }.getOrNull() }
        awaitDispose { value?.close() }
    }
    val sign = mode == "sign"
    BackHandler { if (st.hasChanges) dialog = "exit" else onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (sign) "Signer le document" else "Annoter le document", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = { if (st.hasChanges) dialog = "exit" else onBack() }) { Icon(Icons.Outlined.Close, "Fermer") } },
                actions = {
                    IconButton(onClick = { st.preview = !st.preview; st.selected = null }) {
                        Icon(if (st.preview) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Aperçu")
                    }
                    TextButton(enabled = st.hasChanges && doc != null, onClick = {
                        doc?.let { d -> vm.stamp(d, st.edits(), if (sign) "signé" else "annoté") { newId -> onSaved(newId) } }
                    }) { Text("Enregistrer") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
                Column(Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
                    val sel = st.items.firstOrNull { it.id == st.selected }
                    if (sel != null && !st.preview) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            SmallAction(Icons.Outlined.Remove, "Réduire") { st.update(sel.id) { it.copy(w = (it.w / 1.12f).coerceAtLeast(0.03f)) } }
                            SmallAction(Icons.Outlined.Add, "Agrandir") { st.update(sel.id) { it.copy(w = (it.w * 1.12f).coerceAtMost(1f)) } }
                            SmallAction(Icons.Outlined.RotateLeft, "−15°") { st.update(sel.id) { it.copy(rot = it.rot - 15f) } }
                            SmallAction(Icons.Outlined.RotateRight, "+15°") { st.update(sel.id) { it.copy(rot = it.rot + 15f) } }
                            SmallAction(Icons.Outlined.Delete, "Supprimer") { st.remove(sel.id) }
                        }
                    } else if (st.tool != EditTool.MOVE) {
                        val colors = if (st.tool == EditTool.PEN) listOf(Color(0xFF14235F), Color(0xFF111111), Color(0xFFD93025), Color(0xFF1E5BFF))
                        else listOf(Color(0xFFFFD60A), Color(0xFF7CFC8A), Color(0xFFFF8AD8), Color(0xFF7FD3FF))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (st.tool == EditTool.PEN) "Stylo" else "Surligneur", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            colors.forEach { c ->
                                val cur = if (st.tool == EditTool.PEN) st.penColor else st.hlColor
                                Box(Modifier.padding(start = 10.dp).size(30.dp).clip(CircleShape).background(c)
                                    .border(if (c == cur) 3.dp else 0.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                                    .clickable { if (st.tool == EditTool.PEN) st.penColor = c else st.hlColor = c })
                            }
                        }
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        item { ToolButton(Icons.Outlined.OpenWith, "Déplacer", st.tool == EditTool.MOVE) { st.tool = EditTool.MOVE } }
                        item { ToolButton(Icons.Outlined.Draw, "Signature", false) { dialog = "signature" } }
                        item { ToolButton(Icons.Outlined.Title, "Initiales", false) { dialog = "initials" } }
                        item { ToolButton(Icons.Outlined.Event, "Date", false) {
                            val date = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
                            st.add(SignatureStore.renderText(date, Color(0xFF111111).toArgb(), Typeface.DEFAULT, 90f), 0.2f, 0.8f)
                        } }
                        item { ToolButton(Icons.Outlined.TextFields, "Texte", false) { dialog = "text" } }
                        item { ToolButton(Icons.Outlined.Edit, "Stylo", st.tool == EditTool.PEN) { st.tool = EditTool.PEN; st.selected = null } }
                        item { ToolButton(Icons.Outlined.BorderColor, "Surligner", st.tool == EditTool.HIGHLIGHT) { st.tool = EditTool.HIGHLIGHT; st.selected = null } }
                        item { ToolButton(Icons.AutoMirrored.Outlined.Undo, "Annuler", false, enabled = st.history.isNotEmpty() || st.hasChanges) { st.undo() } }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceContainer)) {
            val s = session
            val count = s?.pageCount ?: 0
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { st.page--; st.selected = null }, enabled = st.page > 0) { Icon(Icons.Outlined.ChevronLeft, "Page précédente") }
                Text(if (count > 0) "Page ${st.page + 1} / $count" else "", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { st.page++; st.selected = null }, enabled = st.page < count - 1) { Icon(Icons.Outlined.ChevronRight, "Page suivante") }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                if (s == null) CircularProgressIndicator() else EditablePage(s, st)
            }
            if (st.tool == EditTool.MOVE && !st.preview) {
                Text("Glissez pour déplacer · pincez pour redimensionner et pivoter", Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }

    when (dialog) {
        "exit" -> ConfirmDialog("Quitter sans enregistrer ?", "Les modifications seront perdues.", "Quitter", { dialog = null }) { dialog = null; onBack() }
        "initials" -> {
            val initials = settings.userName.split(" ").filter { it.isNotBlank() }.joinToString("") { "${it.first().uppercaseChar()}." }
            TextInputDialog("Initiales", initials.ifBlank { "" }, "Ajouter", { dialog = null }) { t ->
                dialog = null; st.add(SignatureStore.renderText(t, typeface = SignatureStore.typeface(0), size = 130f), 0.12f, 0.85f)
            }
        }
        "text" -> TextInputDialog("Texte à ajouter", "", "Ajouter", { dialog = null }) { t ->
            dialog = null; st.add(SignatureStore.renderText(t, Color(0xFF111111).toArgb(), Typeface.DEFAULT, 80f), 0.4f)
        }
        "signature" -> {
            val sigs = remember { vm.signatures() }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Choisir une signature") },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (sigs.isEmpty()) item { Text("Aucune signature enregistrée.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(sigs, key = { it.absolutePath }) { f ->
                            Card(onClick = {
                                dialog = null
                                BitmapFactory.decodeFile(f.absolutePath)?.let { st.add(it, 0.32f, 0.8f) }
                            }, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                FileImage(f.absolutePath, Modifier.fillMaxWidth().height(70.dp).background(Color.White).padding(8.dp), ContentScale.Fit)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = null; onOpenSignatures() }) { Text("Nouvelle signature") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Annuler") } }
            )
        }
    }
}

@Composable
private fun EditablePage(session: PdfSession, st: EditState) {
    val page = st.page.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0))
    val aspect by produceState(0.707f, session, page) { value = session.aspect(page).takeIf { it > 0f } ?: 0.707f }
    val bmp by produceState<Bitmap?>(null, session, page) { value = session.render(page, 1400) }
    val current = remember { mutableStateListOf<Float>() }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val maxWpx = with(density) { maxWidth.toPx() }
        val maxHpx = with(density) { maxHeight.toPx() }
        val wPx = min(maxWpx, maxHpx * aspect)
        val hPx = wPx / aspect
        Box(Modifier.size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() }).background(Color.White)) {
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            // Toucher le fond désélectionne (sous les éléments)
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { st.selected = null } })
            // Traits (stylo / surligneur)
            Canvas(Modifier.fillMaxSize()) {
                fun drawInk(points: List<Float>, color: Color, widthFrac: Float, hl: Boolean) {
                    if (points.size < 2) return
                    val path = Path()
                    path.moveTo(points[0] * size.width, points[1] * size.height)
                    var i = 2
                    while (i + 1 < points.size) { path.lineTo(points[i] * size.width, points[i + 1] * size.height); i += 2 }
                    drawPath(path, if (hl) color.copy(alpha = 0.38f) else color,
                        style = Stroke(width = widthFrac * size.width, cap = if (hl) StrokeCap.Square else StrokeCap.Round, join = StrokeJoin.Round))
                }
                st.strokes.filter { it.page == page }.forEach { ps -> drawInk(ps.stroke.points, Color(ps.stroke.color), ps.stroke.widthFrac, ps.stroke.highlighter) }
                if (current.isNotEmpty()) {
                    val hl = st.tool == EditTool.HIGHLIGHT
                    drawInk(current.toList(), if (hl) st.hlColor else st.penColor, if (hl) 0.025f else 0.004f, hl)
                }
            }
            // Éléments placés (signature, initiales, date, texte)
            st.items.filter { it.page == page }.forEach { item ->
                val iw = item.w * wPx
                val ih = iw * item.bitmap.height / item.bitmap.width
                val selected = st.selected == item.id && !st.preview
                Box(
                    Modifier
                        .offset { IntOffset((item.cx * wPx - iw / 2).roundToInt(), (item.cy * hPx - ih / 2).roundToInt()) }
                        .size(with(density) { iw.toDp() }, with(density) { ih.toDp() })
                        .pointerInput(item.id, wPx, hPx, st.tool) {
                            if (st.tool != EditTool.MOVE) return@pointerInput
                            detectTransformGestures { _, pan, zoom, rotation ->
                                st.selected = item.id
                                st.update(item.id) {
                                    it.copy(
                                        cx = (it.cx + pan.x / wPx).coerceIn(0f, 1f),
                                        cy = (it.cy + pan.y / hPx).coerceIn(0f, 1f),
                                        w = (it.w * zoom).coerceIn(0.03f, 1f),
                                        rot = it.rot + rotation
                                    )
                                }
                            }
                        }
                        .pointerInput(item.id, st.tool) {
                            if (st.tool != EditTool.MOVE) return@pointerInput
                            detectTapGestures { st.selected = item.id }
                        }
                        .graphicsLayer { rotationZ = item.rot }
                        .then(if (selected) Modifier.border(1.5.dp, Color(0xFF2F6BFF), RoundedCornerShape(4.dp)) else Modifier)
                ) { Image(item.bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            }
            // Couche de dessin
            if (st.tool != EditTool.MOVE && !st.preview) {
                Box(Modifier.fillMaxSize().pointerInput(st.tool, wPx, hPx) {
                    detectDragGestures(
                        onDragStart = { o -> current.clear(); current.add(o.x / wPx); current.add(o.y / hPx) },
                        onDrag = { change, _ -> current.add((change.position.x / wPx).coerceIn(0f, 1f)); current.add((change.position.y / hPx).coerceIn(0f, 1f)); change.consume() },
                        onDragEnd = {
                            val hl = st.tool == EditTool.HIGHLIGHT
                            if (current.size >= 4) {
                                val s = PageStroke(st.newId(), page, InkStroke(current.toList(), (if (hl) st.hlColor else st.penColor).toArgb(), if (hl) 0.025f else 0.004f, hl))
                                st.strokes.add(s); st.history.add(s.id)
                            }
                            current.clear()
                        },
                        onDragCancel = { current.clear() }
                    )
                })
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        active -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = c)
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c)
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
