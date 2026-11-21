package com.docuscanpro.app.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.docuscanpro.app.ui.LocalAppActions

/** Outils disponibles. Seuls les outils réellement implémentés sont affichés. */
@Composable
fun ToolsScreen(onOpenLibrary: () -> Unit) {
    val a = LocalAppActions.current
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Outils", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 4.dp)) }
        item { ToolCard(Icons.Outlined.DocumentScanner, "Scanner un document", "Bords détectés, recadrage, perspective et capture automatique", a.scan) }
        item { ToolCard(Icons.Outlined.Collections, "Convertir des images en PDF", "Une ou plusieurs images JPG/PNG en un seul PDF", a.importImages) }
        item { ToolCard(Icons.Outlined.PictureAsPdf, "Importer un PDF", "Ajouter un PDF existant à votre bibliothèque", a.importPdf) }
        item { ToolCard(Icons.Outlined.FolderOpen, "Mes documents", "Rechercher, trier, organiser en dossiers", onOpenLibrary) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Prochaines versions : signature électronique, fusion et division de PDF, compression, mot de passe, annotations et OCR.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToolCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
