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

    val sections = listOf(
        "Numériser" to listOf(
            ToolItem(Icons.Outlined.DocumentScanner, "Scanner", "Bords, perspective, capture auto", a.scan),
            ToolItem(Icons.Outlined.Badge, "Carte d'identité", "Recto + verso sur une page A4", a.scanIdCard),
            ToolItem(Icons.Outlined.Collections, "Images en PDF", "JPG / PNG → un seul PDF", a.importImages),
            ToolItem(Icons.Outlined.PictureAsPdf, "Importer un PDF", "Y compris protégé par mot de passe", a.importPdf)
        ),
        "Signer et annoter" to listOf(
            ToolItem(Icons.Outlined.Draw, "Signer", "Signature, initiales, date", { pickFor = DocTool.SIGN }),
            ToolItem(Icons.Outlined.BorderColor, "Annoter", "Stylo, surligneur, texte", { pickFor = DocTool.ANNOTATE }),
            ToolItem(Icons.Outlined.Gesture, "Mes signatures", "Créer, importer, gérer", openSignatures)
        ),
        "Organiser" to listOf(
            ToolItem(Icons.Outlined.CallMerge, "Fusionner", "Plusieurs PDF en un", { mergePick = true }),
            ToolItem(Icons.Outlined.CallSplit, "Diviser / extraire", "Par plages ou sélection", { pickFor = DocTool.PAGES }),
            ToolItem(Icons.Outlined.Reorder, "Organiser les pages", "Ordre, rotation, suppression", { pickFor = DocTool.PAGES }),
            ToolItem(Icons.Outlined.AutoAwesomeMosaic, "Collage", "2 ou 4 pages par feuille", { pickFor = DocTool.COLLAGE })
        ),
        "Optimiser et protéger" to listOf(
            ToolItem(Icons.Outlined.Compress, "Compresser", "Réduire la taille du PDF", { pickFor = DocTool.COMPRESS }),
            ToolItem(Icons.Outlined.WaterDrop, "Filigrane", "Texte en diagonale", { pickFor = DocTool.WATERMARK }),
            ToolItem(Icons.Outlined.Lock, "Mot de passe", "Protéger avant envoi", { pickFor = DocTool.PROTECT }),
            ToolItem(Icons.Outlined.LockOpen, "Déverrouiller", "Importer un PDF protégé", a.importPdf)
        ),
        "Texte et conversion" to listOf(
            ToolItem(Icons.Outlined.TextSnippet, "Extraire le texte", "OCR : copier, partager", { pickFor = DocTool.TEXT }),
            ToolItem(Icons.Outlined.Description, "PDF en Word", "Texte reconnu → .docx", { pickFor = DocTool.DOCX }),
            ToolItem(Icons.Outlined.ManageSearch, "PDF sélectionnable", "Ajoute une couche de texte", { pickFor = DocTool.SEARCHABLE }),
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
        sections.forEach { (title, tools) ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title.uppercase(), style = com.docuscanpro.app.ui.theme.SectionTitle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
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
        }
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
