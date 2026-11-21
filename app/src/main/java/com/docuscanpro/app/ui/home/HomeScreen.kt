package com.docuscanpro.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.docuscanpro.app.ui.LocalAppActions
import com.docuscanpro.app.ui.components.Avatar
import com.docuscanpro.app.ui.components.DocumentRow
import com.docuscanpro.app.ui.components.EmptyState
import com.docuscanpro.app.ui.components.SectionHeader
import com.docuscanpro.app.ui.components.rememberDocActions
import com.docuscanpro.app.ui.state.LibraryViewModel
import com.docuscanpro.app.ui.theme.Electric
import com.docuscanpro.app.ui.theme.Navy
import com.docuscanpro.app.ui.theme.NavyDeep
import java.util.Calendar

@Composable
fun HomeScreen(
    vm: LibraryViewModel,
    onOpenDocument: (Long) -> Unit,
    onOpenLibrary: (filter: String, focusSearch: Boolean) -> Unit,
    onOpenProfile: () -> Unit
) {
    val actions = LocalAppActions.current
    val docActions = rememberDocActions(vm)
    val settings by vm.settings.collectAsState()
    val docs by vm.documents.collectAsState()
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hello = if (hour in 5..17) "Bonjour" else "Bonsoir"

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Navy), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DocumentScanner, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("DocuScan Pro", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (settings.userName.isBlank()) hello else "$hello, ${settings.userName}", style = MaterialTheme.typography.headlineSmall)
                }
                Avatar(settings.userName, Modifier.clickable(onClick = onOpenProfile))
            }
        }
        item {
            Surface(
                onClick = { onOpenLibrary("all", true) }, shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Rechercher un document", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(
                onClick = actions.scan, shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Navy, NavyDeep))).padding(22.dp)
                ) {
                    Column {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CameraAlt, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("Scanner un document", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Détection des bords, recadrage et correction de perspective automatiques", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f))
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Electric))
                            Spacer(Modifier.width(8.dp))
                            Text("Commencer", style = MaterialTheme.typography.labelLarge, color = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ImportCard(Icons.Outlined.Image, "Importer depuis la galerie", actions.importImages, Modifier.weight(1f))
                ImportCard(Icons.Outlined.PictureAsPdf, "Importer un PDF", actions.importPdf, Modifier.weight(1f))
            }
        }
        item {
            SectionHeader("Raccourcis", Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Shortcut(Icons.Outlined.Collections, "Convertir\nen PDF", actions.importImages, Modifier.weight(1f))
                Shortcut(Icons.Outlined.FolderOpen, "Mes\ndocuments", { onOpenLibrary("all", false) }, Modifier.weight(1f))
                Shortcut(Icons.Outlined.StarBorder, "Mes\nfavoris", { onOpenLibrary("favorites", false) }, Modifier.weight(1f))
            }
        }
        item {
            SectionHeader("Documents récents", Modifier.padding(top = 6.dp)) {
                if (docs.isNotEmpty()) TextButton(onClick = { onOpenLibrary("all", false) }) { Text("Tout voir") }
            }
        }
        if (docs.isEmpty()) {
            item {
                EmptyState(Icons.Outlined.DocumentScanner, "Aucun document", "Scannez votre premier document ou importez un PDF pour le retrouver ici.")
            }
        } else {
            items(docs.take(6), key = { it.id }) { d ->
                DocumentRow(d, vm, docActions, onOpen = { onOpenDocument(d.id) })
            }
        }
    }
}

@Composable
private fun ImportCard(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    OutlinedCard(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
