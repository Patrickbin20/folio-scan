package com.docuscanpro.app.ui.pages

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.docuscanpro.app.pdf.PdfSession
import com.docuscanpro.app.ui.components.TextInputDialog
import com.docuscanpro.app.ui.state.LibraryViewModel
import java.io.File

/** Page dans l'ordre de travail : index d'origine + rotation ajoutée. */
private data class PageRef(val orig: Int, val rot: Int = 0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesScreen(id: Long, vm: LibraryViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val docFlow = remember(id) { vm.observeDocument(id) }
    val doc by docFlow.collectAsState(initial = null)
    val path = doc?.filePath
    val session by produceState<PdfSession?>(null, path) {
        value = path?.let { runCatching { PdfSession(File(it)) }.getOrNull() }
        awaitDispose { value?.close() }
    }
    val pages = remember { mutableStateListOf<PageRef>() }
    val selected = remember { mutableStateListOf<Int>() }   // positions dans la liste de travail
    var dialog by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session) { session?.let { s -> if (pages.isEmpty()) pages.addAll((0 until s.pageCount).map { PageRef(it) }) } }
    val modified = pages.mapIndexed { i, p -> p.orig != i || p.rot != 0 }.any { it } || (session != null && pages.size != session!!.pageCount)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pages", style = MaterialTheme.typography.titleMedium)
                        Text(if (selected.isEmpty()) "Touchez des pages pour les sélectionner" else "${selected.size} sélectionnée(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.Close, "Fermer") } },
                actions = {
                    IconButton(onClick = { if (selected.size == pages.size) selected.clear() else { selected.clear(); selected.addAll(pages.indices) } }) { Icon(Icons.Outlined.SelectAll, "Tout sélectionner") }
                    TextButton(onClick = { dialog = "save" }, enabled = modified && pages.isNotEmpty()) { Text("Enregistrer") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
                LazyRow(Modifier.navigationBarsPadding().padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    val one = selected.size == 1
                    item { Act(Icons.Outlined.RotateRight, "Pivoter", selected.isNotEmpty()) { selected.forEach { i -> pages[i] = pages[i].copy(rot = (pages[i].rot + 90) % 360) } } }
                    item { Act(Icons.AutoMirrored.Outlined.ArrowBack, "Avancer", one && selected[0] > 0) {
                        val i = selected[0]; val p = pages.removeAt(i); pages.add(i - 1, p); selected[0] = i - 1 } }
                    item { Act(Icons.AutoMirrored.Outlined.ArrowForward, "Reculer", one && selected[0] < pages.size - 1) {
                        val i = selected[0]; val p = pages.removeAt(i); pages.add(i + 1, p); selected[0] = i + 1 } }
                    item { Act(Icons.Outlined.Delete, "Supprimer", selected.isNotEmpty() && selected.size < pages.size) {
                        selected.sortedDescending().forEach { pages.removeAt(it) }; selected.clear() } }
                    item { Act(Icons.Outlined.ContentCut, "Extraire", selected.isNotEmpty()) { dialog = "extract" } }
                    item { Act(Icons.Outlined.CallSplit, "Diviser", pages.size > 1) { dialog = "split" } }
                }
            }
        }
    ) { padding ->
        val s = session
        if (s == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp), modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(pages, key = { _, p -> p.orig }) { i, p ->
                val sel = i in selected
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(10.dp))
                            .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .clickable { if (sel) selected.remove(i) else selected.add(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp by produceState<Bitmap?>(null, s, p.orig) { value = s.render(p.orig, 300) }
                        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().padding(4.dp).rotate(p.rot.toFloat()), contentScale = ContentScale.Fit) }
                        if (sel) Icon(Icons.Outlined.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(4.dp), tint = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    val d = doc
    if (d != null) when (dialog) {
        "save" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Enregistrer les modifications") },
            text = { Text("Remplacer le document actuel ou créer une copie modifiée ?") },
            confirmButton = { TextButton(onClick = { dialog = null; vm.organize(d, pages.map { it.orig }, pages.associate { it.orig to it.rot }, asCopy = true, onDone = onOpen) }) { Text("Créer une copie") } },
            dismissButton = { TextButton(onClick = { dialog = null; vm.organize(d, pages.map { it.orig }, pages.associate { it.orig to it.rot }, asCopy = false, onDone = { onBack() }) }) { Text("Remplacer") } }
        )
        "extract" -> TextInputDialog("Nom du nouveau document", "${d.name} (extrait)", "Extraire", { dialog = null }) { name ->
            dialog = null
            val order = selected.sorted().map { pages[it].orig }
            vm.extractPages(d, order, name, onOpen)
        }
        "split" -> {
            var text by remember { mutableStateOf("") }
            val ranges = parseRanges(text, pages.size)
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Diviser par plages") },
                text = {
                    Column {
                        Text("Exemple : 1-3, 4-6, 7 → un fichier par plage (pages 1 à ${pages.size}).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Plages") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                            isError = text.isNotBlank() && ranges == null)
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { text = (1..pages.size).joinToString(", ") }) { Text("Une page par fichier") }
                    }
                },
                confirmButton = {
                    TextButton(enabled = ranges != null, onClick = {
                        dialog = null
                        val mapped = ranges!!.map { r -> pages[r.first].orig..pages[r.last].orig }
                        val contiguous = ranges.all { r -> r.toList().map { pages[it].orig } == (pages[r.first].orig..pages[r.last].orig).toList() }
                        if (contiguous) vm.splitRanges(d, mapped) { onBack() }
                        else vm.toast("Enregistrez d'abord le nouvel ordre des pages")
                    }) { Text("Diviser") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Annuler") } }
            )
        }
    }
}

/** « 1-3, 5 » → [0..2, 4..4] ; null si invalide. */
private fun parseRanges(text: String, max: Int): List<IntRange>? {
    if (text.isBlank()) return null
    return runCatching {
        text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.map { part ->
            val bits = part.split('-').map { it.trim().toInt() }
            val a = bits[0]; val b = bits.getOrElse(1) { a }
            require(a in 1..max && b in a..max)
            (a - 1)..(b - 1)
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}

@Composable
private fun Act(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = c)
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c)
    }
}
