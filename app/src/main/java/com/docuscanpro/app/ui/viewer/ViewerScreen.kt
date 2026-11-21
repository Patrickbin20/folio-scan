package com.docuscanpro.app.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docuscanpro.app.data.ExportFormat
import com.docuscanpro.app.pdf.PdfSession
import com.docuscanpro.app.ui.components.ConfirmDialog
import com.docuscanpro.app.ui.components.EmptyState
import com.docuscanpro.app.ui.components.FolderPickerDialog
import com.docuscanpro.app.ui.components.TextInputDialog
import com.docuscanpro.app.ui.components.pagesLabel
import com.docuscanpro.app.ui.components.formatSize
import com.docuscanpro.app.ui.components.rememberDocActions
import com.docuscanpro.app.ui.state.LibraryViewModel
import com.docuscanpro.app.data.db.DocumentEntity
import com.docuscanpro.app.ui.tools.DocTool
import com.docuscanpro.app.ui.tools.ToolDialogs
import com.docuscanpro.app.ui.tools.rememberToolController
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.WaterDrop
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    id: Long, vm: LibraryViewModel, onBack: () -> Unit,
    openViewer: (Long) -> Unit, openEdit: (Long, String) -> Unit, openPages: (Long) -> Unit, openText: (Long) -> Unit
) {
    val ctl = rememberToolController()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val docFlow = remember(id) { vm.observeDocument(id) }
    val doc by docFlow.collectAsState(initial = null)
    val folders by vm.folders.collectAsState()
    val actions = rememberDocActions(vm)
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var deleted by remember { mutableStateOf(false) }

    LaunchedEffect(id) { vm.markOpened(id) }

    val path = doc?.filePath
    val session by produceState<Result<PdfSession>?>(null, path) {
        value = path?.let { p -> runCatching { PdfSession(File(p)) } }
        awaitDispose { value?.getOrNull()?.close() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(doc?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        doc?.let { Text("${pagesLabel(it.pageCount)} · ${formatSize(it.sizeBytes)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } },
                actions = {
                    doc?.let { d ->
                        IconButton(onClick = { searching = !searching }) { Icon(Icons.Outlined.Search, "Rechercher") }
                        IconButton(onClick = { vm.setFavorite(d, !d.favorite) }) {
                            Icon(if (d.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, "Favori", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
        ,
        bottomBar = {
            doc?.let { d ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                            BarAction(Icons.Outlined.Share, "Partager", Modifier.weight(1f)) { actions.share(d, null) }
                            BarAction(Icons.Outlined.Draw, "Signer", Modifier.weight(1f)) { ctl.run(DocTool.SIGN, d) }
                            BarAction(Icons.Outlined.BorderColor, "Annoter", Modifier.weight(1f)) { ctl.run(DocTool.ANNOTATE, d) }
                            BarAction(Icons.Outlined.MoreHoriz, "Plus", Modifier.weight(1f)) { menu = true }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceContainer)) {
            val d0 = doc
            if (searching && d0 != null) {
                SearchBarInDoc(d0, query, { query = it }, onAnalyze = { vm.indexNow(d0) }) { page -> scope.launch { listState.animateScrollToItem(page) } }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val res = session
                when {
                    res == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    res.isFailure -> EmptyState(Icons.Outlined.ErrorOutline, "Impossible d'ouvrir le PDF", "Le fichier est peut-être protégé ou endommagé.", Modifier.align(Alignment.Center))
                    else -> PdfPages(res.getOrThrow(), listState)
                }
            }
        }
    }

    val d = doc
    if (d != null) when (dialog) {
        "rename" -> TextInputDialog("Renommer", d.name, "Renommer", { dialog = null }) { vm.rename(d, it); dialog = null }
        "move" -> FolderPickerDialog(folders, d.folderId, { dialog = null }, { vm.move(d, it); dialog = null }, { dialog = "new" })
        "new" -> TextInputDialog("Nouveau dossier", "", "Créer", { dialog = null }) { n -> vm.createFolder(n) { fid -> vm.move(d, fid) }; dialog = null }
        "delete" -> ConfirmDialog("Supprimer ce document ?", "« ${d.name} » sera définitivement supprimé.", "Supprimer", { dialog = null }) {
            dialog = null; deleted = true; vm.delete(d); onBack()
        }
    }
    LaunchedEffect(doc, deleted) { if (doc == null && deleted) onBack() }
    val dd = doc
    if (menu && dd != null) {
        ModalBottomSheet(onDismissRequest = { menu = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            val go: (() -> Unit) -> () -> Unit = { f -> { menu = false; f() } }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                SheetGroup("Pages et texte")
                SheetRow(Icons.Outlined.Reorder, "Organiser, extraire, diviser", go { ctl.run(DocTool.PAGES, dd) })
                SheetRow(Icons.Outlined.TextSnippet, "Texte du document (OCR)", go { ctl.run(DocTool.TEXT, dd) })
                SheetRow(Icons.Outlined.Description, "Convertir en Word", go { ctl.run(DocTool.DOCX, dd) })
                SheetGroup("Optimiser et protéger")
                SheetRow(Icons.Outlined.Compress, "Compresser", go { ctl.run(DocTool.COMPRESS, dd) })
                SheetRow(Icons.Outlined.WaterDrop, "Filigrane", go { ctl.run(DocTool.WATERMARK, dd) })
                SheetRow(Icons.Outlined.Lock, "Protéger par mot de passe", go { ctl.run(DocTool.PROTECT, dd) })
                SheetRow(Icons.Outlined.AutoAwesomeMosaic, "Plusieurs pages par feuille", go { ctl.run(DocTool.COLLAGE, dd) })
                SheetGroup("Fichier")
                SheetRow(Icons.Outlined.PictureAsPdf, "Exporter en PDF", go { actions.export(dd, ExportFormat.PDF) })
                SheetRow(Icons.Outlined.Image, "Exporter en images JPEG", go { actions.export(dd, ExportFormat.JPEG) })
                SheetRow(Icons.Outlined.Print, "Imprimer", go { ctl.run(DocTool.PRINT, dd) })
                SheetRow(Icons.Outlined.Edit, "Renommer", go { dialog = "rename" })
                SheetRow(Icons.Outlined.DriveFileMove, "Déplacer vers un dossier", go { dialog = "move" })
                SheetRow(Icons.Outlined.ContentCopy, "Dupliquer", go { vm.duplicate(dd) })
                SheetRow(Icons.Outlined.Delete, "Supprimer", go { dialog = "delete" }, danger = true)
            }
        }
    }
    ToolDialogs(ctl, vm, openViewer, openEdit, openPages, openText)
}

@Composable
private fun BarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SheetGroup(title: String) {
    Text(title.uppercase(), style = com.docuscanpro.app.ui.theme.SectionTitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val c = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c, modifier = Modifier.size(21.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c, modifier = Modifier.padding(start = 16.dp))
    }
}

private fun normText(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/** Recherche de mots dans le texte du document (OCR ou texte numérique). */
@Composable
private fun SearchBarInDoc(doc: DocumentEntity, query: String, onQuery: (String) -> Unit, onAnalyze: () -> Unit, onJump: (Int) -> Unit) {
    val pages = doc.ocrText?.split('\u000C')
    val q = normText(query.trim())
    val hits = if (pages == null || q.length < 2) emptyList() else pages.mapIndexedNotNull { i, t ->
        val n = Regex(Regex.escape(q)).findAll(normText(t)).count(); if (n > 0) i to n else null
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (pages == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Le texte de ce document n'a pas encore été analysé.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onAnalyze) { Text("Analyser") }
            }
        } else {
            OutlinedTextField(query, onQuery, singleLine = true, placeholder = { Text("Rechercher dans le document") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            if (q.length >= 2) {
                if (hits.isEmpty()) Text("Aucun résultat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                else LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hits.size) { k ->
                        val (page, n) = hits[k]
                        AssistChip(onClick = { onJump(page) }, label = { Text("Page ${page + 1} · $n") })
                    }
                }
            }
        }
    }
}

/** Pages du PDF : défilement vertical, zoom à deux doigts, double-tap, miniatures. */
@Composable
private fun PdfPages(session: PdfSession, listState: androidx.compose.foundation.lazy.LazyListState) {
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val current by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val renderWidth = (widthPx * 1.6f).toInt().coerceIn(600, 2000)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { if (scale > 1.05f) { scale = 1f; offsetX = 0f } else scale = 2.2f })
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                                    val maxX = (size.width * (scale - 1f)) / 2f
                                    offsetX = (offsetX + event.calculatePan().x).coerceIn(-maxX, maxX)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX }
            ) {
                items(session.pageCount) { i -> PageImage(session, i, renderWidth) }
            }
            Surface(
                shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
            ) {
                Text("${current + 1} / ${session.pageCount}", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (session.pageCount > 1) {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
                LazyRow(
                    contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    items(session.pageCount) { i ->
                        val sel = i == current
                        Box(
                            Modifier.size(width = 44.dp, height = 58.dp).clip(RoundedCornerShape(8.dp))
                                .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clickable { scope.launch { listState.animateScrollToItem(i) } }
                        ) { PageImage(session, i, 140, thumb = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageImage(session: PdfSession, index: Int, width: Int, thumb: Boolean = false) {
    val aspect by produceState(0.707f, session, index) { value = session.aspect(index).takeIf { it > 0f } ?: 0.707f }
    val bmp by produceState<Bitmap?>(null, session, index, width) { value = session.render(index, width) }
    val m = if (thumb) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(4.dp))
    Box(m.background(Color.White), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxSize(), contentScale = if (thumb) ContentScale.Crop else ContentScale.Fit)
        else if (!thumb) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}
