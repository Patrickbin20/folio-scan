package com.docuscanpro.app.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docuscanpro.app.data.db.DocumentEntity
import com.docuscanpro.app.ui.LocalAppActions
import com.docuscanpro.app.ui.components.TextInputDialog
import com.docuscanpro.app.ui.state.LibraryViewModel

private class ToolItem(val icon: ImageVector, val title: String, val subtitle: String, val onClick: () -> Unit)

/** Tous les outils — chacun est réellement fonctionnel. */
@Composable
fun ToolsScreen(
    vm: LibraryViewModel,
    openViewer: (Long) -> Unit, openEdit: (Long, String) -> Unit, openPages: (Long) -> Unit,
    openText: (Long) -> Unit, openSignatures: () -> Unit, openWeb: () -> Unit
) {
    val a = LocalAppActions.current
    val ctl = rememberToolController()
    var pickFor by remember { mutableStateOf<DocTool?>(null) }
    var mergePick by remember { mutableStateOf(false) }
    var mergeList by remember { mutableStateOf<List<DocumentEntity>?>(null) }

    var showMore by remember { mutableStateOf(false) }
    val essentials = listOf(
        ToolItem(Icons.Outlined.DocumentScanner, "Scanner", "Bords et perspective corrigés", a.scan),
        ToolItem(Icons.Outlined.Draw, "Signer", "Signature, initiales, date", { pickFor = DocTool.SIGN }),
        ToolItem(Icons.Outlined.CallMerge, "Fusionner", "Plusieurs PDF en un", { mergePick = true }),
        ToolItem(Icons.Outlined.CallSplit, "Diviser et organiser", "Pages, ordre, rotation", { pickFor = DocTool.PAGES }),
        ToolItem(Icons.Outlined.Compress, "Compresser", "Réduire la taille", { pickFor = DocTool.COMPRESS }),
        ToolItem(Icons.Outlined.TextSnippet, "Extraire le texte", "Copier, partager, Word", { pickFor = DocTool.TEXT })
    )
    val sections = listOf(
        "Numériser et importer" to listOf(
            ToolItem(Icons.Outlined.Badge, "Carte d'identité", "Recto + verso sur une page A4", a.scanIdCard),
            ToolItem(Icons.Outlined.Collections, "Images en PDF", "JPG / PNG → un seul PDF", a.importImages),
            ToolItem(Icons.Outlined.PictureAsPdf, "Importer un PDF", "Y compris protégé par mot de passe", a.importPdf)
        ),
        "Annoter" to listOf(
            ToolItem(Icons.Outlined.BorderColor, "Annoter et surligner", "Stylo, surligneur, texte", { pickFor = DocTool.ANNOTATE }),
            ToolItem(Icons.Outlined.Gesture, "Mes signatures", "Créer, importer, gérer", openSignatures)
        ),
        "Protéger" to listOf(
            ToolItem(Icons.Outlined.WaterDrop, "Filigrane", "Texte en diagonale", { pickFor = DocTool.WATERMARK }),
            ToolItem(Icons.Outlined.Lock, "Mot de passe", "Protéger avant envoi", { pickFor = DocTool.PROTECT })
        ),
        "Convertir" to listOf(
            ToolItem(Icons.Outlined.Description, "PDF en Word", "Texte reconnu → .docx", { pickFor = DocTool.DOCX }),
            ToolItem(Icons.Outlined.ManageSearch, "PDF sélectionnable", "Ajoute une couche de texte", { pickFor = DocTool.SEARCHABLE }),
            ToolItem(Icons.Outlined.AutoAwesomeMosaic, "Plusieurs pages par feuille", "2 ou 4 pages", { pickFor = DocTool.COLLAGE }),
            ToolItem(Icons.Outlined.Language, "Page web en PDF", "Via l'impression Android", openWeb),
            ToolItem(Icons.Outlined.Print, "Imprimer", "Imprimante ou PDF", { pickFor = DocTool.PRINT })
        )
    )

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Text("Outils", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.height(44.dp).padding(top = 4.dp)) }
        item { ToolGroup(null, essentials) }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showMore = !showMore }.padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (showMore) "Moins d'outils" else "Plus d'outils", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                Icon(if (showMore) androidx.compose.material.icons.Icons.Outlined.ExpandLess else androidx.compose.material.icons.Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.secondary)
            }
        }
        if (showMore) sections.forEach { (title, tools) -> item { ToolGroup(title, tools) } }
        item {
            Text("La reconnaissance de texte fonctionne hors-ligne pour l'alphabet latin (français, anglais, espagnol…). L'arabe n'est pas encore pris en charge.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    pickFor?.let { tool ->
        DocPickerDialog("Choisir un document", vm, multi = false, confirmLabel = "", onDismiss = { pickFor = null }) { list ->
            pickFor = null; list.firstOrNull()?.let { ctl.run(tool, it) }
        }
    }
    if (mergePick) {
        DocPickerDialog("Fusionner des PDF", vm, multi = true, confirmLabel = "Fusionner", onDismiss = { mergePick = false }) { list ->
            mergePick = false; mergeList = list
        }
    }
    mergeList?.let { list ->
        TextInputDialog("Nom du PDF fusionné", "${list.first().name} (fusion)", "Fusionner", { mergeList = null }) { name ->
            mergeList = null; vm.merge(list, name, openViewer)
        }
    }
    ToolDialogs(ctl, vm, openViewer, openEdit, openPages, openText)
}

@Composable
private fun ToolGroup(title: String?, tools: List<ToolItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title.uppercase(), style = com.docuscanpro.app.ui.theme.SectionTitle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                tools.forEachIndexed { i, t ->
                    if (i > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ToolRow(t)
                }
            }
        }
    }
}

@Composable
private fun ToolRow(t: ToolItem) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = t.onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(t.icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
            Text(t.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
