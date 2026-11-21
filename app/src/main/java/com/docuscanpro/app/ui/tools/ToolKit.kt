package com.docuscanpro.app.ui.tools

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docuscanpro.app.data.db.DocumentEntity
import com.docuscanpro.app.ui.components.FileImage
import com.docuscanpro.app.ui.components.TextInputDialog
import com.docuscanpro.app.ui.components.pagesLabel
import com.docuscanpro.app.ui.state.LibraryViewModel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Impression (ou « Enregistrer en PDF ») via le service d'impression Android. */
fun printPdf(context: Context, file: File, name: String) {
    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    pm.print(name, object : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
            if (cancel?.isCanceled == true) { callback.onLayoutCancelled(); return }
            callback.onLayoutFinished(PrintDocumentInfo.Builder("$name.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
        }
        override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, callback: WriteResultCallback) {
            try {
                FileInputStream(file).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) { callback.onWriteFailed(e.message) }
        }
    }, null)
}

/** Outils qui s'appliquent à un document. */
enum class DocTool { SIGN, ANNOTATE, PAGES, TEXT, DOCX, SEARCHABLE, COMPRESS, WATERMARK, PROTECT, COLLAGE, PRINT }

/** Lance un outil sur un document : navigation ou boîte de dialogue selon l'outil. */
class ToolController {
    var pending by mutableStateOf<Pair<DocTool, DocumentEntity>?>(null)
    fun run(tool: DocTool, doc: DocumentEntity) { pending = tool to doc }
}

@Composable
fun rememberToolController() = remember { ToolController() }

@Composable
fun ToolDialogs(
    ctl: ToolController, vm: LibraryViewModel,
    openViewer: (Long) -> Unit, openEdit: (Long, String) -> Unit, openPages: (Long) -> Unit, openText: (Long) -> Unit
) {
    val context = LocalContext.current
    var protectPw by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<DocumentEntity?>(null) }
    val docxSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri ->
        val d = target; if (uri != null && d != null) vm.exportDocx(d, uri)
    }
    val protectedSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val d = target; if (uri != null && d != null) vm.exportProtected(d, protectPw, uri)
    }
    val pending = ctl.pending ?: return
    val (tool, doc) = pending
    val close = { ctl.pending = null }
    val immediate = tool in setOf(DocTool.SIGN, DocTool.ANNOTATE, DocTool.PAGES, DocTool.TEXT, DocTool.SEARCHABLE, DocTool.PRINT, DocTool.DOCX)
    if (immediate) {
        LaunchedEffect(pending) {
            close()
            when (tool) {
                DocTool.SIGN -> openEdit(doc.id, "sign")
                DocTool.ANNOTATE -> openEdit(doc.id, "annotate")
                DocTool.PAGES -> openPages(doc.id)
                DocTool.TEXT -> openText(doc.id)
                DocTool.SEARCHABLE -> vm.makeSearchable(doc)
                DocTool.PRINT -> printPdf(context, File(doc.filePath), doc.name)
                DocTool.DOCX -> { target = doc; docxSaver.launch("${doc.name}.docx") }
                else -> Unit
            }
        }
        return
    }
    when (tool) {
        DocTool.COMPRESS -> ChoiceDialog("Compresser « ${doc.name} »", listOf("Normale — bonne qualité", "Forte — fichier le plus léger"), close) { i ->
            close(); vm.compress(doc, i == 1, openViewer)
        }
        DocTool.COLLAGE -> ChoiceDialog("Pages par feuille", listOf("2 pages par feuille", "4 pages par feuille"), close) { i ->
            close(); vm.collage(doc, if (i == 0) 2 else 4, openViewer)
        }
        DocTool.WATERMARK -> {
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
            TextInputDialog("Texte du filigrane", "COPIE – $today", "Ajouter", close) { t -> close(); vm.watermark(doc, t, openViewer) }
        }
        DocTool.PROTECT -> PasswordDialog("Protéger par mot de passe", "Le PDF ne pourra être ouvert qu'avec ce mot de passe. L'original dans l'app reste accessible.", true, close,
            actions = listOf(
                "Partager" to { pw: String -> close(); vm.shareProtected(doc, pw) { context.startActivity(it) } },
                "Exporter" to { pw: String -> close(); protectPw = pw; target = doc; protectedSaver.launch("${doc.name} (protégé).pdf") }
            ))
        else -> Unit
    }
}

@Composable
fun ChoiceDialog(title: String, options: List<String>, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var sel by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { sel = i }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sel == i, onClick = { sel = i }); Text(o)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(sel) }) { Text("Valider") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun PasswordDialog(title: String, message: String, confirm: Boolean, onDismiss: () -> Unit, actions: List<Pair<String, (String) -> Unit>>) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val ok = if (confirm) pw.length >= 4 && pw == pw2 else pw.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(pw, { pw = it }, label = { Text("Mot de passe") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                if (confirm) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(pw2, { pw2 = it }, label = { Text("Confirmer") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                        isError = pw2.isNotEmpty() && pw != pw2)
                }
                if (confirm && pw.isNotEmpty() && pw.length < 4) Text("4 caractères minimum", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Row { actions.forEach { (label, act) -> TextButton(onClick = { act(pw) }, enabled = ok) { Text(label) } } }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/** Sélection d'un ou plusieurs documents de la bibliothèque (ordre de sélection conservé). */
@Composable
fun DocPickerDialog(title: String, vm: LibraryViewModel, multi: Boolean, confirmLabel: String, onDismiss: () -> Unit, onConfirm: (List<DocumentEntity>) -> Unit) {
    val docs by vm.documents.collectAsState()
    val selected = remember { mutableStateListOf<Long>() }
    val bars = com.docuscanpro.app.ui.components.rememberSystemBarsPadding()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Marges lues dans l'activité : le bouton de validation reste au-dessus de la barre de navigation
            Column(Modifier.fillMaxSize().padding(bars).padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Fermer") }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (multi) Text("Touchez les documents dans l'ordre voulu", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (docs.isEmpty()) Text("Aucun document dans la bibliothèque.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(docs, key = { it.id }) { d ->
                        val idx = selected.indexOf(d.id)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (idx >= 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                                .clickable {
                                    if (multi) { if (idx >= 0) selected.remove(d.id) else selected.add(d.id) }
                                    else onConfirm(listOf(d))
                                }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FileImage(d.thumbPath, Modifier.size(width = 42.dp, height = 54.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(pagesLabel(d.pageCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (multi && idx >= 0) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary), contentAlignment = Alignment.Center) {
                                    Text("${idx + 1}", color = MaterialTheme.colorScheme.onSecondary, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                if (multi) {
                    Button(
                        onClick = { onConfirm(selected.mapNotNull { id -> docs.firstOrNull { it.id == id } }) }, enabled = selected.size >= 2,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp), shape = RoundedCornerShape(16.dp)
                    ) { Text(if (selected.size >= 2) "$confirmLabel (${selected.size})" else "Sélectionnez au moins 2 documents") }
                }
            }
        }
    }
}
