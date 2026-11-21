package com.docuscanpro.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docuscanpro.app.data.ExportFormat
import com.docuscanpro.app.data.db.DocumentEntity
import com.docuscanpro.app.ui.state.LibraryViewModel
import kotlinx.coroutines.launch

/** Partage et export (Storage Access Framework) d'un document. */
class DocActions(
    val share: (DocumentEntity, ExportFormat?) -> Unit,
    val export: (DocumentEntity, ExportFormat?) -> Unit
)

@Composable
fun rememberDocActions(vm: LibraryViewModel): DocActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { mutableStateOf<DocumentEntity?>(null) }
    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        val d = pending.value
        if (uri != null && d != null) vm.exportPdf(d, uri)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        val d = pending.value
        if (uri != null && d != null) vm.exportJpegs(d, uri)
    }
    return remember(vm) {
        DocActions(
            share = { d, f ->
                scope.launch {
                    try { context.startActivity(vm.shareIntent(d, f ?: vm.settings.value.exportFormat)) }
                    catch (e: Exception) { vm.toast("Partage impossible") }
                }
            },
            export = { d, f ->
                pending.value = d
                if ((f ?: vm.settings.value.exportFormat) == ExportFormat.PDF) pdfSaver.launch("${d.name}.pdf")
                else folderPicker.launch(null)
            }
        )
    }
}

/** Menu contextuel complet d'un document + boîtes de dialogue associées. */
@Composable
fun DocumentMenu(doc: DocumentEntity, vm: LibraryViewModel, actions: DocActions) {
    var open by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val folders by vm.folders.collectAsState()
    val fmt = vm.settings.collectAsState().value.exportFormat

    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "Actions") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            DropdownMenuItem(text = { Text("Renommer") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { open = false; dialog = "rename" })
            DropdownMenuItem(text = { Text("Partager") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { open = false; actions.share(doc, null) })
            DropdownMenuItem(text = { Text("Exporter (${fmt.name})") }, leadingIcon = { Icon(Icons.Outlined.FileDownload, null) }, onClick = { open = false; actions.export(doc, null) })
            DropdownMenuItem(
                text = { Text(if (doc.favorite) "Retirer des favoris" else "Ajouter aux favoris") },
                leadingIcon = { Icon(if (doc.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, null) },
                onClick = { open = false; vm.setFavorite(doc, !doc.favorite) })
            DropdownMenuItem(text = { Text("Déplacer vers un dossier") }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) }, onClick = { open = false; dialog = "move" })
            DropdownMenuItem(text = { Text("Dupliquer") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { open = false; vm.duplicate(doc) })
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; dialog = "delete" })
        }
    }
    when (dialog) {
        "rename" -> TextInputDialog("Renommer", doc.name, "Renommer", { dialog = null }) { vm.rename(doc, it); dialog = null }
        "delete" -> ConfirmDialog("Supprimer ce document ?", "« ${doc.name} » sera définitivement supprimé de cet appareil.", "Supprimer", { dialog = null }) { vm.delete(doc); dialog = null }
        "move" -> FolderPickerDialog(folders, doc.folderId, { dialog = null }, { vm.move(doc, it); dialog = null }, { dialog = "newfolder" })
        "newfolder" -> TextInputDialog("Nouveau dossier", "", "Créer", { dialog = null }) { name -> vm.createFolder(name) { id -> vm.move(doc, id) }; dialog = null }
    }
}

@Composable
fun DocumentRow(doc: DocumentEntity, vm: LibraryViewModel, actions: DocActions, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onOpen, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FileImage(doc.thumbPath, Modifier.size(width = 52.dp, height = 68.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (doc.favorite) { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.Star, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.secondary) }
                }
                Spacer(Modifier.height(4.dp))
                Text(formatDate(doc.modifiedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FormatBadge()
                    Spacer(Modifier.width(8.dp))
                    Text("${pagesLabel(doc.pageCount)} · ${formatSize(doc.sizeBytes)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DocumentMenu(doc, vm, actions)
        }
    }
}

@Composable
fun DocumentGridCard(doc: DocumentEntity, vm: LibraryViewModel, actions: DocActions, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onOpen, modifier = modifier, shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box {
            FileImage(doc.thumbPath, Modifier.fillMaxWidth().aspectRatio(0.78f))
            if (doc.favorite) Icon(Icons.Filled.Star, null, Modifier.padding(8.dp).size(18.dp).align(Alignment.TopStart), tint = MaterialTheme.colorScheme.secondary)
        }
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${pagesLabel(doc.pageCount)} · ${formatSize(doc.sizeBytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DocumentMenu(doc, vm, actions)
        }
    }
}

@Composable
fun FormatBadge(label: String = "PDF") {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(label, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
