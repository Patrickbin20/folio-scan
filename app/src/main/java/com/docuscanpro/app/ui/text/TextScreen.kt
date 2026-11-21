package com.docuscanpro.app.ui.text

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docuscanpro.app.ui.components.EmptyState
import com.docuscanpro.app.ui.state.LibraryViewModel

/** Texte reconnu (OCR) : sélection, copie, partage, export Word, PDF sélectionnable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextScreen(id: Long, vm: LibraryViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val docFlow = remember(id) { vm.observeDocument(id) }
    val doc by docFlow.collectAsState(initial = null)
    var pages by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(doc?.id) { doc?.let { d -> if (pages == null) pages = runCatching { vm.textOf(d) }.getOrElse { vm.toast("Reconnaissance impossible"); emptyList() } } }
    val docxSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri ->
        val d = doc; if (uri != null && d != null) vm.exportDocx(d, uri)
    }
    val full = pages?.mapIndexed { i, t -> if ((pages?.size ?: 0) > 1) "— Page ${i + 1} —\n$t" else t }?.joinToString("\n\n") ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Texte du document", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } },
                actions = {
                    IconButton(enabled = full.isNotBlank(), onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Texte", full))
                        vm.toast("Texte copié")
                    }) { Icon(Icons.Outlined.ContentCopy, "Copier") }
                    IconButton(enabled = full.isNotBlank(), onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, full), "Partager le texte"))
                    }) { Icon(Icons.Outlined.Share, "Partager") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            val d = doc
            if (d != null && !pages.isNullOrEmpty()) Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
                Row(Modifier.navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { docxSaver.launch("${d.name}.docx") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Outlined.Description, null); Spacer(Modifier.padding(3.dp)); Text("Word")
                    }
                    OutlinedButton(onClick = { vm.makeSearchable(d) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Outlined.ManageSearch, null); Spacer(Modifier.padding(3.dp)); Text("PDF sélectionnable")
                    }
                }
            }
        }
    ) { padding ->
        val p = pages
        when {
            p == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            p.all { it.isBlank() } -> EmptyState(Icons.Outlined.TextSnippet, "Aucun texte reconnu",
                "La reconnaissance fonctionne pour l'alphabet latin (français, anglais, espagnol…). L'arabe n'est pas encore pris en charge.", Modifier.padding(padding))
            else -> SelectionContainer(Modifier.padding(padding)) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(p) { i, t ->
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(16.dp)).padding(16.dp)) {
                            if (p.size > 1) { Text("Page ${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary); Spacer(Modifier.height(6.dp)) }
                            Text(t.ifBlank { "(aucun texte)" }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
