package com.docuscanpro.app.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docuscanpro.app.data.db.DocumentEntity
import com.docuscanpro.app.ui.LocalAppActions
import com.docuscanpro.app.ui.components.DocumentMenu
import com.docuscanpro.app.ui.components.EmptyState
import com.docuscanpro.app.ui.components.FileImage
import com.docuscanpro.app.ui.components.TextInputDialog
import com.docuscanpro.app.ui.components.formatSize
import com.docuscanpro.app.ui.components.pagesLabel
import com.docuscanpro.app.ui.components.rememberDocActions
import com.docuscanpro.app.ui.state.LibraryViewModel
import com.docuscanpro.app.ui.theme.AppGradient
import com.docuscanpro.app.ui.tools.DocPickerDialog
import com.docuscanpro.app.ui.tools.DocTool
import com.docuscanpro.app.ui.tools.ToolDialogs
import com.docuscanpro.app.ui.tools.rememberToolController
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    vm: LibraryViewModel,
    onOpenDocument: (Long) -> Unit,
    onOpenLibrary: (filter: String, focusSearch: Boolean) -> Unit,
    onOpenProfile: () -> Unit,
    openEdit: (Long, String) -> Unit = { _, _ -> },
    openPages: (Long) -> Unit = {},
    openText: (Long) -> Unit = {}
) {
    val actions = LocalAppActions.current
    val docActions = rememberDocActions(vm)
    val settings by vm.settings.collectAsState()
    val docs by vm.documents.collectAsState()
    val ctl = rememberToolController()
    var pickFor by remember { mutableStateOf<DocTool?>(null) }
    var mergePick by remember { mutableStateOf(false) }
    var mergeList by remember { mutableStateOf<List<DocumentEntity>?>(null) }
    var importChoice by remember { mutableStateOf(false) }
    var used by remember { mutableLongStateOf(0L) }
    LaunchedEffect(docs.size) { used = vm.storageUsed() }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hello = if (hour in 5..17) "Bonjour" else "Bonsoir"
    val first = settings.userName.trim().split(" ").firstOrNull().orEmpty()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // En-tête
        item {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(AppGradient), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DocumentScanner, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("DocuScan", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                val initials = settings.userName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "D" }
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable(onClick = onOpenProfile),
                    contentAlignment = Alignment.Center
                ) { Text(initials, style = MaterialTheme.typography.labelMedium) }
            }
        }
        // Salutation
        item {
            Column {
                Text(if (first.isBlank()) hello else "$hello, $first", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text("${docs.size} document${if (docs.size > 1) "s" else ""} · ${formatSize(used)} sur l'appareil", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Recherche
        item {
            Surface(
                onClick = { onOpenLibrary("all", true) }, shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Rechercher un nom ou un mot", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Action principale — dégradé sauge → émeraude → marine
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppGradient).clickable(onClick = actions.scan).padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.DocumentScanner, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Scanner un document", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(Modifier.height(2.dp))
                        Text("Bords et perspective corrigés automatiquement", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color.White)
                }
            }
        }
        // Raccourcis
        item {
            Row(Modifier.fillMaxWidth()) {
                Quick(Icons.Outlined.FileUpload, "Importer", Modifier.weight(1f)) { importChoice = true }
                Quick(Icons.Outlined.Draw, "Signer", Modifier.weight(1f)) { pickFor = DocTool.SIGN }
                Quick(Icons.Outlined.CallMerge, "Fusionner", Modifier.weight(1f)) { mergePick = true }
                Quick(Icons.Outlined.TextFields, "Texte", Modifier.weight(1f)) { pickFor = DocTool.TEXT }
            }
        }
        // Récents
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Récents", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (docs.isNotEmpty()) TextButton(onClick = { onOpenLibrary("all", false) }) { Text("Tout voir", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium) }
            }
        }
        item {
            if (docs.isEmpty()) {
                EmptyState(Icons.Outlined.Description, "Aucun document", "Scannez votre premier document ou importez un PDF.")
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        docs.take(5).forEachIndexed { i, d ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenDocument(d.id) }.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FileImage(d.thumbPath, Modifier.size(width = 38.dp, height = 50.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(d.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(2.dp))
                                    Text("${pagesLabel(d.pageCount)} · PDF · ${relDate(d.modifiedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DocumentMenu(d, vm, docActions)
                            }
                        }
                    }
                }
            }
        }
    }

    if (importChoice) {
        AlertDialog(
            onDismissRequest = { importChoice = false },
            title = { Text("Importer") },
            text = {
                Column {
                    ListItem(headlineContent = { Text("Images depuis la galerie") }, supportingContent = { Text("Converties en un seul PDF") },
                        leadingContent = { Icon(Icons.Outlined.Image, null) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { importChoice = false; actions.importImages() })
                    ListItem(headlineContent = { Text("Un fichier PDF") }, supportingContent = { Text("Même protégé par mot de passe") },
                        leadingContent = { Icon(Icons.Outlined.PictureAsPdf, null) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { importChoice = false; actions.importPdf() })
                }
            },
            confirmButton = { TextButton(onClick = { importChoice = false }) { Text("Annuler") } }
        )
    }
    pickFor?.let { tool ->
        DocPickerDialog("Choisir un document", vm, multi = false, confirmLabel = "", onDismiss = { pickFor = null }) { list ->
            pickFor = null; list.firstOrNull()?.let { ctl.run(tool, it) }
        }
    }
    if (mergePick) {
        DocPickerDialog("Fusionner des PDF", vm, multi = true, confirmLabel = "Fusionner", onDismiss = { mergePick = false }) { list -> mergePick = false; mergeList = list }
    }
    mergeList?.let { list ->
        TextInputDialog("Nom du PDF fusionné", "${list.first().name} (fusion)", "Fusionner", { mergeList = null }) { name -> mergeList = null; vm.merge(list, name, onOpenDocument) }
    }
    ToolDialogs(ctl, vm, onOpenDocument, openEdit, openPages, openText)
}

private fun relDate(ms: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "aujourd'hui"
        sameYear && dayDiff == 1 -> "hier"
        else -> SimpleDateFormat(if (sameYear) "d MMM" else "d MMM yyyy", Locale.FRANCE).format(Date(ms))
    }
}

@Composable
private fun Quick(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(21.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
