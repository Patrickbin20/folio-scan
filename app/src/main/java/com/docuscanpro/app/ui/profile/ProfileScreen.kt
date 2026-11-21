package com.docuscanpro.app.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.Switch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docuscanpro.app.data.ExportFormat
import com.docuscanpro.app.data.ThemeMode
import com.docuscanpro.app.ui.components.Avatar
import com.docuscanpro.app.ui.components.TextInputDialog
import com.docuscanpro.app.ui.components.formatSize
import com.docuscanpro.app.ui.state.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: LibraryViewModel) {
    val s by vm.settings.collectAsState()
    val docs by vm.documents.collectAsState()
    val context = LocalContext.current
    var editName by remember { mutableStateOf(false) }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.backupAll(uri) }
    var used by remember { mutableLongStateOf(0L) }
    var cache by remember { mutableLongStateOf(0L) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(docs.size, refresh) { used = vm.storageUsed(); cache = vm.cacheSize() }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Profil", style = MaterialTheme.typography.headlineMedium) }
        item {
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.userName, Modifier.size(56.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.userName.ifBlank { "Votre nom" }, style = MaterialTheme.typography.titleMedium)
                        Text("${docs.size} document${if (docs.size > 1) "s" else ""} · stockés sur cet appareil", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editName = true }) { Icon(Icons.Outlined.Edit, "Modifier le nom") }
                }
            }
        }
        item {
            Section(Icons.Outlined.Palette, "Apparence") {
                Choice(listOf("Système", "Clair", "Sombre"), ThemeMode.entries.indexOf(s.theme)) { vm.setTheme(ThemeMode.entries[it]) }
            }
        }
        item {
            Section(Icons.Outlined.PhotoCamera, "Qualité de numérisation") {
                Choice(listOf("Standard", "Haute"), if (s.highQuality) 1 else 0) { vm.setHighQuality(it == 1) }
                Spacer(Modifier.height(8.dp))
                Text(if (s.highQuality) "2400 px, fichiers plus nets mais plus lourds" else "1600 px, fichiers plus légers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Section(Icons.Outlined.Upload, "Format d'export par défaut") {
                Choice(listOf("PDF", "JPEG"), ExportFormat.entries.indexOf(s.exportFormat)) { vm.setExportFormat(ExportFormat.entries[it]) }
            }
        }
        item {
            Section(Icons.Outlined.TextSnippet, "Reconnaissance de texte") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Analyser automatiquement les nouveaux documents (recherche dans le contenu)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = s.autoOcr, onCheckedChange = { vm.setAutoOcr(it) })
                }
                Text("Hors-ligne, alphabet latin (français, anglais…). L'arabe n'est pas encore pris en charge.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Section(Icons.Outlined.Backup, "Sauvegarde") {
                Text("Copie tous vos documents (PDF) dans un dossier de votre choix : téléphone, carte SD ou Google Drive.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { backupPicker.launch(null) }, shape = RoundedCornerShape(12.dp), enabled = docs.isNotEmpty()) { Text("Sauvegarder tous les documents") }
            }
        }
        item {
            Section(Icons.Outlined.SdStorage, "Stockage") {
                Text("Documents : ${formatSize(used)}", style = MaterialTheme.typography.bodyMedium)
                Text("Fichiers temporaires : ${formatSize(cache)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { vm.clearCache { refresh++ } }, shape = RoundedCornerShape(12.dp), enabled = cache > 0) { Text("Vider les fichiers temporaires") }
            }
        }
        item {
            Section(Icons.Outlined.Lock, "Confidentialité") {
                Text(
                    "Vos documents restent uniquement sur ce téléphone. DocuScan Pro n'envoie aucun fichier sur Internet, " +
                        "n'utilise ni compte ni publicité. Un document ne quitte l'appareil que si vous le partagez ou l'exportez vous-même.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Section(Icons.Outlined.Info, "À propos") {
                Text("DocuScan Pro — version $version", style = MaterialTheme.typography.bodyMedium)
                Text("Numérisation : Google ML Kit Document Scanner (traitement sur l'appareil)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (editName) TextInputDialog("Votre nom", s.userName, "Enregistrer", { editName = false }) { vm.setUserName(it); editName = false }
}

@Composable
private fun Section(icon: ImageVector? = null, title: String? = null, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(10.dp)) }
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(selected = i == selected, onClick = { onSelect(i) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label) }
        }
    }
}
