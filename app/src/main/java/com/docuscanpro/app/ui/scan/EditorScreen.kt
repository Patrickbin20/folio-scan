package com.docuscanpro.app.ui.scan

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docuscanpro.app.imaging.ScanFilter
import com.docuscanpro.app.ui.LocalAppActions
import com.docuscanpro.app.ui.components.ConfirmDialog
import com.docuscanpro.app.ui.components.FolderPickerDialog
import com.docuscanpro.app.ui.components.TextInputDialog
import com.docuscanpro.app.ui.state.LibraryViewModel
import com.docuscanpro.app.ui.state.ScanPage
import com.docuscanpro.app.ui.state.ScanViewModel

/** Retouche épurée : aperçu, filtres, 4 outils. Réglages fins et nom du fichier à la demande. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(scanVm: ScanViewModel, vm: LibraryViewModel, onClose: () -> Unit, onSaved: (Long) -> Unit) {
    val state by scanVm.state.collectAsState()
    val folders by vm.folders.collectAsState()
    val actions = LocalAppActions.current
    var confirmExit by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var cropping by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    LaunchedEffect(state.openCrop) { if (state.openCrop) { cropping = true; scanVm.consumeOpenCrop() } }
    LaunchedEffect(state.pages.isEmpty(), state.saving) { if (state.pages.isEmpty() && !state.saving) onClose() }
    BackHandler(enabled = !state.saving) { confirmExit = true }
    val page = state.pages.getOrNull(state.selected)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Retouche", style = MaterialTheme.typography.titleMedium)
                        Text("Page ${state.selected + 1} sur ${state.pages.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = { confirmExit = true }, enabled = !state.saving) { Icon(Icons.Outlined.Close, "Fermer") } },
                actions = { IconButton(onClick = actions.importImages, enabled = !state.saving) { Icon(Icons.Outlined.AddPhotoAlternate, "Ajouter des pages") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (page == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Aperçu (pixels réellement traités)
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                val preview by produceState<Bitmap?>(null, page) { value = scanVm.preview(page) }
                val bmp = preview
                if (bmp != null) Image(bmp.asImageBitmap(), "Aperçu", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else CircularProgressIndicator()
            }
            // Pages
            if (state.pages.size > 1) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scanVm.move(-1) }, enabled = state.selected > 0) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Avancer la page") }
                    LazyRow(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(state.pages, key = { _, p -> p.id }) { i, p -> PageThumb(scanVm, p, i, i == state.selected) { scanVm.select(i) } }
                    }
                    IconButton(onClick = { scanVm.move(1) }, enabled = state.selected < state.pages.size - 1) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Reculer la page") }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.navigationBarsPadding().padding(top = 12.dp, bottom = 12.dp)) {
                    // Filtres en vignettes
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ScanFilter.entries.size) { idx ->
                            val f = ScanFilter.entries[idx]
                            FilterTile(scanVm, page, f, page.filter == f) { scanVm.setFilter(f) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Tool(Icons.Outlined.Crop, "Recadrer") { cropping = true }
                        Tool(Icons.Outlined.RotateRight, "Pivoter") { scanVm.rotate() }
                        Tool(Icons.Outlined.Tune, "Réglages") { adjusting = true }
                        Tool(Icons.Outlined.Delete, "Supprimer") { if (state.pages.size == 1) confirmExit = true else scanVm.deleteSelected() }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (state.saving) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                    Button(
                        onClick = { dialog = "save" }, enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp), shape = RoundedCornerShape(14.dp)
                    ) {
                        if (state.saving) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(10.dp)); Text("Enregistrement…") }
                        else Text("Enregistrer en PDF · ${state.pages.size} page${if (state.pages.size > 1) "s" else ""}")
                    }
                }
            }
        }
    }

    if (cropping && page != null) {
        CropDialog(scanVm, page, onDismiss = { cropping = false }) { q -> scanVm.setCrop(q); cropping = false }
    }
    if (adjusting && page != null) {
        ModalBottomSheet(onDismissRequest = { adjusting = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                Text("Réglages", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                AdjustSlider(Icons.Outlined.BrightnessMedium, "Luminosité", page.brightness, -0.5f..0.5f, page.id) { scanVm.setBrightness(it) }
                AdjustSlider(Icons.Outlined.Contrast, "Contraste", page.contrast, 0.5f..1.6f, page.id) { scanVm.setContrast(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { scanVm.resetAdjust() }) { Text("Réinitialiser") }
                    if (state.pages.size > 1) TextButton(onClick = { scanVm.applyToAll(); vm.toast("Filtre et réglages appliqués à toutes les pages") }) { Text("Appliquer à toutes les pages") }
                }
            }
        }
    }
    if (confirmExit) {
        ConfirmDialog("Abandonner ce scan ?", "Les pages non enregistrées seront perdues.", "Abandonner", { confirmExit = false }) {
            confirmExit = false; scanVm.clear(); onClose()
        }
    }
    when (dialog) {
        "save" -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Enregistrer") },
            text = {
                Column {
                    OutlinedTextField(state.name, scanVm::setName, label = { Text("Nom du document") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { dialog = "folder" }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(Icons.Outlined.Folder, null); Spacer(Modifier.width(8.dp))
                        Text(folders.firstOrNull { it.id == state.folderId }?.name ?: "Aucun dossier", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null; scanVm.save(onDone = onSaved, onError = { vm.toast(it) }) }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Annuler") } }
        )
        "folder" -> FolderPickerDialog(folders, state.folderId, { dialog = "save" }, { scanVm.setFolder(it); dialog = "save" }, { dialog = "new" })
        "new" -> TextInputDialog("Nouveau dossier", "", "Créer", { dialog = "save" }) { name -> vm.createFolder(name) { scanVm.setFolder(it) }; dialog = "save" }
    }
}

@Composable
private fun FilterTile(scanVm: ScanViewModel, page: ScanPage, filter: ScanFilter, selected: Boolean, onClick: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, page.uri, page.rotation, page.crop, filter) {
        value = scanVm.preview(page.copy(filter = filter, brightness = 0f, contrast = 1f), 220)
    }
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 56.dp, height = 70.dp).clip(RoundedCornerShape(10.dp))
                .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) { bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
        Spacer(Modifier.height(4.dp))
        Text(filter.label, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PageThumb(scanVm: ScanViewModel, page: ScanPage, index: Int, selected: Boolean, onClick: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, page.uri) { value = scanVm.base(page.uri, 240) }
    Box(
        Modifier.size(width = 44.dp, height = 58.dp).clip(RoundedCornerShape(8.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().rotate(page.rotation.toFloat()), contentScale = ContentScale.Crop) }
        Text("${index + 1}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(topStart = 6.dp)).padding(horizontal = 4.dp))
    }
}

@Composable
private fun Tool(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AdjustSlider(icon: ImageVector, label: String, value: Float, range: ClosedFloatingPointRange<Float>, key: Long, onCommit: (Float) -> Unit) {
    var local by remember(key, value) { mutableFloatStateOf(value) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(86.dp))
        Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onCommit(local) }, valueRange = range, modifier = Modifier.weight(1f))
    }
}
