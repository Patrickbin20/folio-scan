package com.docuscanpro.app.ui.signature

import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.docuscanpro.app.data.SignatureStore
import com.docuscanpro.app.ui.components.FileImage
import com.docuscanpro.app.ui.state.LibraryViewModel
import kotlinx.coroutines.launch
import java.io.File

/** Point de signature (avec pression pour le stylet). */
private data class SigPoint(val x: Float, val y: Float, val w: Float)

private val INKS = listOf(Color(0xFF14235F), Color(0xFF111111), Color(0xFF1E5BFF))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(vm: LibraryViewModel, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(vm.signatures()) }
    var creating by remember { mutableStateOf(saved.isEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (creating) "Nouvelle signature" else "Mes signatures") },
                navigationIcon = { IconButton(onClick = { if (creating && saved.isNotEmpty()) creating = false else onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!creating) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(saved, key = { it.absolutePath }) { f ->
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                FileImage(f.absolutePath, Modifier.weight(1f).height(80.dp).background(Color.White), ContentScale.Fit)
                                IconButton(onClick = { vm.deleteSignature(f); saved = vm.signatures() }) { Icon(Icons.Outlined.Delete, "Supprimer", tint = Color(0xFFD93025)) }
                            }
                        }
                    }
                }
                Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("Créer une signature") }
            } else {
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Dessiner") }, icon = { Icon(Icons.Outlined.Draw, null) })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Taper") }, icon = { Icon(Icons.Outlined.Keyboard, null) })
                    Tab(tab == 2, { tab = 2 }, text = { Text("Importer") }, icon = { Icon(Icons.Outlined.Image, null) })
                }
                val onSaved = { saved = vm.signatures(); creating = false }
                when (tab) {
                    0 -> DrawTab(vm, onSaved)
                    1 -> TypeTab(vm, onSaved)
                    else -> ImportTab(vm, onSaved)
                }
            }
        }
    }
}

@Composable
private fun DrawTab(vm: LibraryViewModel, onSaved: () -> Unit) {
    val strokes = remember { mutableStateListOf<List<SigPoint>>() }
    val current = remember { mutableStateListOf<SigPoint>() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var ink by remember { mutableStateOf(INKS[0]) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Signez avec le doigt ou un stylet (la pression du stylet est prise en compte)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(20.dp)).background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull() ?: continue
                            val stylus = ch.type == PointerType.Stylus
                            val w = if (stylus) 2f + ch.pressure.coerceIn(0f, 1f) * 9f else 5.5f
                            if (ch.pressed) {
                                current.add(SigPoint(ch.position.x, ch.position.y, w))
                                ch.consume()
                            } else if (current.isNotEmpty()) {
                                strokes.add(current.toList()); current.clear()
                            }
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                (strokes + listOf(current.toList())).forEach { s ->
                    if (s.size == 1) drawCircle(ink, s[0].w / 2, Offset(s[0].x, s[0].y))
                    for (i in 1 until s.size) drawLine(ink, Offset(s[i - 1].x, s[i - 1].y), Offset(s[i].x, s[i].y), (s[i - 1].w + s[i].w) / 2, StrokeCap.Round)
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp).fillMaxWidth(0.8f).height(1.dp).background(Color(0xFFD0D5DD)))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            INKS.forEach { c ->
                Box(Modifier.padding(end = 10.dp).size(34.dp).clip(CircleShape).background(c)
                    .border(if (c == ink) 3.dp else 0.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                    .clickable { ink = c })
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { strokes.clear(); current.clear() }, shape = RoundedCornerShape(12.dp)) { Text("Effacer") }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                if (size.width > 0) {
                    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                    val c = ACanvas(bmp)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.toArgb(); strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
                    strokes.forEach { s ->
                        if (s.size == 1) { p.style = Paint.Style.FILL; c.drawCircle(s[0].x, s[0].y, s[0].w / 2, p); p.style = Paint.Style.STROKE }
                        for (i in 1 until s.size) { p.strokeWidth = (s[i - 1].w + s[i].w) / 2; c.drawLine(s[i - 1].x, s[i - 1].y, s[i].x, s[i].y, p) }
                    }
                    vm.saveSignature(bmp, onSaved)
                }
            },
            enabled = strokes.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)
        ) { Text("Enregistrer la signature") }
    }
}

@Composable
private fun TypeTab(vm: LibraryViewModel, onSaved: () -> Unit) {
    var name by remember { mutableStateOf(vm.settings.value.userName) }
    var style by remember { mutableIntStateOf(0) }
    val preview = remember(name, style) { if (name.isBlank()) null else SignatureStore.renderText(name, typeface = SignatureStore.typeface(style), size = 150f) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Votre nom") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SignatureStore.STYLES.size) { i -> FilterChip(selected = style == i, onClick = { style = i }, label = { Text(SignatureStore.STYLES[i]) }) }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            preview?.let { Image(it.asImageBitmap(), "Aperçu", Modifier.fillMaxSize().padding(16.dp), contentScale = ContentScale.Fit) }
                ?: Text("Aperçu", color = Color.Gray)
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = { preview?.let { vm.saveSignature(it, onSaved) } }, enabled = preview != null, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("Enregistrer la signature") }
    }
}

@Composable
private fun ImportTab(vm: LibraryViewModel, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch { bmp = runCatching { vm.signatureFromImage(uri) }.getOrElse { vm.toast("Image illisible"); null } }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Photographiez votre signature sur une feuille blanche : le fond sera rendu transparent.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            bmp?.let { Image(it.asImageBitmap(), "Signature importée", Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit) }
                ?: Text("Aucune image", color = Color.Gray)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text("Choisir une image") }
        Spacer(Modifier.weight(1f))
        Button(onClick = { bmp?.let { vm.saveSignature(it, onSaved) } }, enabled = bmp != null, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("Enregistrer la signature") }
    }
}

/** Liste des signatures enregistrées pour le choix dans l'éditeur. */
fun loadSignatures(vm: LibraryViewModel): List<File> = vm.signatures()
