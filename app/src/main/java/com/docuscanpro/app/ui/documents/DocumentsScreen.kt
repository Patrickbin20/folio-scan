package com.docuscanpro.app.ui.documents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.docuscanpro.app.data.SortOrder
import com.docuscanpro.app.data.db.FolderEntity
import com.docuscanpro.app.ui.components.ConfirmDialog
import com.docuscanpro.app.ui.components.DocumentGridCard
import com.docuscanpro.app.ui.components.DocumentRow
import com.docuscanpro.app.ui.components.EmptyState
import com.docuscanpro.app.ui.components.TextInputDialog
import com.docuscanpro.app.ui.components.rememberDocActions
import com.docuscanpro.app.ui.state.LibraryViewModel
import java.text.Normalizer

private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentsScreen(vm: LibraryViewModel, initialFilter: String, focusSearch: Boolean, onOpenDocument: (Long) -> Unit) {
    val docs by vm.documents.collectAsState()
    val folders by vm.folders.collectAsState()
    val settings by vm.settings.collectAsState()
    val actions = rememberDocActions(vm)
    var query by rememberSaveable { mutableStateOf("") }
    // "all" | "favorites" | "recent" | "folder:<id>"
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var sortMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var folderTarget by remember { mutableStateOf<FolderEntity?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { focus.requestFocus() } }

    val q = norm(query.trim())
    val visible = docs
        .filter { d ->
            when {
                filter == "favorites" -> d.favorite
                filter == "recent" -> d.lastOpenedAt != null
                filter.startsWith("folder:") -> d.folderId == filter.removePrefix("folder:").toLongOrNull()
                else -> true
            }
        }
        .filter { q.isEmpty() || norm(it.name).contains(q) }
        .let { list ->
            when {
                filter == "recent" -> list.sortedByDescending { it.lastOpenedAt }
                settings.sort == SortOrder.NAME -> list.sortedBy { norm(it.name) }
                settings.sort == SortOrder.SIZE -> list.sortedByDescending { it.sizeBytes }
                else -> list.sortedByDescending { it.modifiedAt }
            }
        }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Documents", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { dialog = "newfolder" }) { Icon(Icons.Outlined.CreateNewFolder, "Nouveau dossier") }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "Trier") }
                DropdownMenu(sortMenu, { sortMenu = false }, shape = RoundedCornerShape(16.dp)) {
                    listOf(SortOrder.DATE to "Date de modification", SortOrder.NAME to "Nom", SortOrder.SIZE to "Taille").forEach { (s, l) ->
                        DropdownMenuItem(text = { Text(l) }, leadingIcon = { RadioButton(selected = settings.sort == s, onClick = null) },
                            onClick = { vm.setSort(s); sortMenu = false })
                    }
                }
            }
            IconButton(onClick = { vm.setGridView(!settings.gridView) }) {
                Icon(if (settings.gridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView, "Affichage")
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Rechercher par nom") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Effacer") } },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).focusRequester(focus)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip("Tous", filter == "all") { filter = "all" }
            Chip("Favoris", filter == "favorites") { filter = "favorites" }
            Chip("Historique", filter == "recent") { filter = "recent" }
            folders.forEach { f ->
                Chip(f.name, filter == "folder:${f.id}", Icons.Outlined.Folder,
                    onLongClick = { folderTarget = f; dialog = "folder" }) { filter = "folder:${f.id}" }
            }
        }
        if (filter.startsWith("folder:")) {
            Text("Appui long sur un dossier pour le renommer ou le supprimer", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        }
        Spacer(Modifier.height(6.dp))

        if (visible.isEmpty()) {
            if (docs.isEmpty()) EmptyState(Icons.Outlined.Inventory2, "Bibliothèque vide", "Vos documents scannés et importés apparaîtront ici.")
            else EmptyState(Icons.Outlined.SearchOff, "Aucun résultat", "Aucun document ne correspond à cette recherche ou à ce filtre.")
        } else if (settings.gridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp), contentPadding = PaddingValues(20.dp, 6.dp, 20.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visible, key = { it.id }) { d -> DocumentGridCard(d, vm, actions, { onOpenDocument(d.id) }) }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(20.dp, 6.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible, key = { it.id }) { d -> DocumentRow(d, vm, actions, { onOpenDocument(d.id) }) }
            }
        }
    }

    when (dialog) {
        "newfolder" -> TextInputDialog("Nouveau dossier", "", "Créer", { dialog = null }) { vm.createFolder(it) { id -> filter = "folder:$id" }; dialog = null }
        "folder" -> folderTarget?.let { f ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(f.name) },
                text = { Text("Que voulez-vous faire avec ce dossier ?") },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { dialog = "renamefolder" }) { Text("Renommer") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { dialog = "deletefolder" }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } }
            )
        }
        "renamefolder" -> folderTarget?.let { f -> TextInputDialog("Renommer le dossier", f.name, "Renommer", { dialog = null }) { vm.renameFolder(f, it); dialog = null } }
        "deletefolder" -> folderTarget?.let { f ->
            ConfirmDialog("Supprimer « ${f.name} » ?", "Les documents qu'il contient seront conservés.", "Supprimer", { dialog = null }) {
                vm.deleteFolder(f); if (filter == "folder:${f.id}") filter = "all"; dialog = null
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(label: String, selected: Boolean, icon: ImageVector? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = fg); Spacer(Modifier.width(6.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
