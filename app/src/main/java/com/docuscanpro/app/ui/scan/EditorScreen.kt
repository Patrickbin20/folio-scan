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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(scanVm: ScanViewModel, vm: LibraryViewModel, onClose: () -> Unit, onSaved: (Long) -> Unit) {
    val state by scanVm.state.collectAsState()
    val folders by vm.folders.collectAsState()
    val actions = LocalAppActions.current
    var confirmExit by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var cropping by remember { mutableStateOf(false) }

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
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    if (state.saving) {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
                    }
                    Button(
                        onClick = { scanVm.save(onDone = onSaved, onError = { vm.toast(it) }) },
                        enabled = !state.saving && state.pages.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)
                    ) {
                        if (state.saving) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(10.dp)); Text("Enregistrement…") }
                        else { Icon(Icons.Outlined.Save, null); Spacer(Modifier.width(8.dp)); Text("Enregistrer en PDF (${state.pages.size} p.)") }
                    }
                }
            }
        }
    ) { padding ->
        if (page == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // --- Aperçu réel (pixels traités) ---
            Box(
                Modifier.fillMaxWidth().height(380.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                val preview by produceState<Bitmap?>(null, page) { value = scanVm.preview(page) }
                val bmp = preview
                if (bmp != null) Image(bmp.asImageBitmap(), "Aperçu", Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit)
                else CircularProgressIndicator()
            }

            // --- Pages ---
            LazyRow(contentPadding = PaddingValues(16.dp, 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(state.pages, key = { _, p -> p.id }) { i, p -> PageThumb(scanVm, p, i, i == state.selected) { scanVm.select(i) } }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Tool(Icons.Outlined.Crop, "Recadrer") { cropping = true }
                Tool(Icons.Outlined.RotateRight, "Pivoter") { scanVm.rotate() }
                Tool(Icons.AutoMirrored.Outlined.ArrowBack, "Avancer", state.selected > 0) { scanVm.move(-1) }
                Tool(Icons.AutoMirrored.Outlined.ArrowForward, "Reculer", state.selected < state.pages.size - 1) { scanVm.move(1) }
                Tool(Icons.Outlined.Delete, "Supprimer") { if (state.pages.size == 1) confirmExit = true else scanVm.deleteSelected() }
            }

            // --- Filtres ---
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filtre", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (state.pages.size > 1) TextButton(onClick = { scanVm.applyToAll(); vm.toast("Réglages appliqués à toutes les pages") }) {
                    Icon(Icons.Outlined.DoneAll, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Appliquer à toutes")
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ScanFilter.entries.size) { idx ->
                    val f = ScanFilter.entries[idx]
                    FilterChip(selected = page.filter == f, onClick = { scanVm.setFilter(f) }, label = { Text(f.label) }, shape = RoundedCornerShape(12.dp))
                }
            }

            // --- Luminosité / contraste ---
            AdjustSlider(Icons.Outlined.BrightnessMedium, "Luminosité", page.brightness, -0.5f..0.5f, page.id) { scanVm.setBrightness(it) }
            AdjustSlider(Icons.Outlined.Contrast, "Contraste", page.contrast, 0.5f..1.6f, page.id) { scanVm.setContrast(it) }
            if (page.brightness != 0f || page.contrast != 1f) {
                TextButton(onClick = { scanVm.resetAdjust() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Réinitialiser les réglages")
                }
            }

            // --- Nom & dossier ---
            OutlinedTextField(
                value = state.name, onValueChange = scanVm::setName, label = { Text("Nom du document") }, singleLine = true,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            )
            OutlinedButton(
                onClick = { dialog = "folder" }, shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Icon(Icons.Outlined.Folder, null); Spacer(Modifier.width(8.dp))
                Text(folders.firstOrNull { it.id == state.folderId }?.name ?: "Aucun dossier", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    if (cropping && page != null) {
        CropDialog(scanVm, page, onDismiss = { cropping = false }) { q -> scanVm.setCrop(q); cropping = false }
    }
    if (confirmExit) {
        ConfirmDialog("Abandonner ce scan ?", "Les pages non enregistrées seront perdues.", "Abandonner", { confirmExit = false }) {
            confirmExit = false; scanVm.clear(); onClose()
        }
    }
    when (dialog) {
        "folder" -> FolderPickerDialog(folders, state.folderId, { dialog = null }, { scanVm.setFolder(it); dialog = null }, { dialog = "new" })
        "new" -> TextInputDialog("Nouveau dossier", "", "Créer", { dialog = null }) { name -> vm.createFolder(name) { scanVm.setFolder(it) }; dialog = null }
    }
}

@Composable
private fun PageThumb(scanVm: ScanViewModel, page: ScanPage, index: Int, selected: Boolean, onClick: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, page.uri) { value = scanVm.base(page.uri, 240) }
    Box(
        Modifier.size(width = 58.dp, height = 76.dp).clip(RoundedCornerShape(10.dp))
            .border(if (selected) 2.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().rotate(page.rotation.toFloat()), contentScale = ContentScale.Crop) }
        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopStart).padding(3.dp)) {
            Text("${index + 1}", Modifier.padding(horizontal = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.surface)
        }
    }
}

@Composable
private fun Tool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val c = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        Icon(icon, null, tint = c)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c)
    }
}

@Composable
private fun AdjustSlider(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: Float,
    range: ClosedFloatingPointRange<Float>, key: Long, onCommit: (Float) -> Unit
) {
    var local by remember(key, value) { mutableFloatStateOf(value) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(86.dp))
        Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onCommit(local) }, valueRange = range, modifier = Modifier.weight(1f))
    }
}
