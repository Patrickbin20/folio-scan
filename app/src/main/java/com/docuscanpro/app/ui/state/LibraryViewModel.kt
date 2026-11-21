package com.docuscanpro.app.ui.state

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.docuscanpro.app.data.AppContainer
import com.docuscanpro.app.data.AppSettings
import com.docuscanpro.app.data.ProtectedPdfException
import com.docuscanpro.app.data.ExportFormat
import com.docuscanpro.app.data.SortOrder
import com.docuscanpro.app.data.ThemeMode
import com.docuscanpro.app.data.db.DocumentEntity
import com.docuscanpro.app.data.db.FolderEntity
import android.graphics.Bitmap
import com.docuscanpro.app.export.DocxWriter
import com.docuscanpro.app.imaging.Collage
import com.docuscanpro.app.imaging.ImageProcessor
import com.docuscanpro.app.pdf.JpegPdfWriter
import com.docuscanpro.app.pdf.PageEdits
import com.docuscanpro.app.pdf.PdfOps
import com.docuscanpro.app.pdf.PdfTools
import com.docuscanpro.app.ui.components.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ViewModel principal : bibliothèque, dossiers, réglages, import/export (architecture MVVM). */
class LibraryViewModel(private val c: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val documents: StateFlow<List<DocumentEntity>> =
        c.documents.observeDocuments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val folders: StateFlow<List<FolderEntity>> =
        c.documents.observeFolders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun toast(msg: String) { _messages.trySend(msg) }

    fun observeDocument(id: Long): Flow<DocumentEntity?> = c.documents.observeDocument(id)

    private fun run(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); success?.let { toast(it) } } catch (e: Exception) { toast(e.message ?: "Une erreur est survenue") }
        }
    }

    // --- Documents ---
    fun rename(d: DocumentEntity, name: String) = run("Document renommé") { c.documents.rename(d, name) }
    fun setFavorite(d: DocumentEntity, fav: Boolean) =
        run(if (fav) "Ajouté aux favoris" else "Retiré des favoris") { c.documents.setFavorite(d, fav) }
    fun duplicate(d: DocumentEntity) = run("Document dupliqué") { c.documents.duplicate(d) }
    fun delete(d: DocumentEntity) = run("Document supprimé") { c.documents.delete(d) }
    fun move(d: DocumentEntity, folderId: Long?) = run("Document déplacé") { c.documents.moveToFolder(d, folderId) }
    fun markOpened(id: Long) = run { c.documents.markOpened(id) }

    fun importPdf(uri: Uri, onProtected: () -> Unit = {}, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val id = c.documents.importPdf(uri)
                c.indexInBackground(id)
                toast("PDF importé")
                onDone(id)
            } catch (e: ProtectedPdfException) { onProtected() }
            catch (e: Exception) { toast(e.message ?: "Import impossible") }
        }
    }

    // --- Dossiers ---
    fun createFolder(name: String, then: (Long) -> Unit = {}) {
        viewModelScope.launch {
            try { then(c.documents.createFolder(name)); toast("Dossier créé") } catch (e: Exception) { toast(e.message ?: "Erreur") }
        }
    }
    fun renameFolder(f: FolderEntity, name: String) = run("Dossier renommé") { c.documents.renameFolder(f, name) }
    fun deleteFolder(f: FolderEntity) = run("Dossier supprimé (documents conservés)") { c.documents.deleteFolder(f) }

    // --- Export / partage ---
    suspend fun shareIntent(d: DocumentEntity, format: ExportFormat = settings.value.exportFormat): Intent =
        c.exporter.shareIntent(d, format)
    fun exportPdf(d: DocumentEntity, target: Uri) = run("PDF exporté") { c.exporter.exportPdf(d, target) }
    fun exportJpegs(d: DocumentEntity, tree: Uri) {
        viewModelScope.launch {
            try { val n = c.exporter.exportJpegs(d, tree); toast(if (n > 1) "$n images exportées" else "Image exportée") }
            catch (e: Exception) { toast(e.message ?: "Export impossible") }
        }
    }

    // --- Outils PDF ---
    private val _busy = MutableStateFlow<String?>(null)
    /** Message de progression affiché pendant un traitement long (null = rien en cours). */
    val busy: StateFlow<String?> = _busy

    private fun tool(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = label
            try { block() }
            catch (e: OutOfMemoryError) { toast("Mémoire insuffisante pour ce document") }
            catch (e: Exception) { toast(e.message ?: "Une erreur est survenue") }
            finally { _busy.value = null }
        }
    }

    private suspend fun io(block: () -> Unit) = withContext(Dispatchers.IO) { block() }
    private fun f(d: DocumentEntity) = File(d.filePath)

    private suspend fun publish(tmp: File, name: String, msg: String, folderId: Long? = null): Long {
        val id = c.documents.addPdf(tmp, name, "tool", folderId)
        c.indexInBackground(id)
        toast(msg)
        return id
    }

    fun merge(list: List<DocumentEntity>, name: String, onDone: (Long) -> Unit) = tool("Fusion des PDF…") {
        val out = c.documents.workFile()
        io { PdfOps.merge(list.map { f(it) }, out) }
        onDone(publish(out, name, "${list.size} PDF fusionnés"))
    }

    fun extractPages(d: DocumentEntity, pages: List<Int>, name: String, onDone: (Long) -> Unit) = tool("Extraction des pages…") {
        val out = c.documents.workFile()
        io { PdfOps.extract(f(d), pages, out) }
        onDone(publish(out, name, "${pages.size} page(s) extraite(s)", d.folderId))
    }

    fun splitRanges(d: DocumentEntity, ranges: List<IntRange>, onDone: (Int) -> Unit) = tool("Division du PDF…") {
        ranges.forEach { r ->
            val out = c.documents.workFile()
            io { PdfOps.extract(f(d), r.toList(), out) }
            val id = c.documents.addPdf(out, "${d.name} (p. ${r.first + 1}-${r.last + 1})", "tool", d.folderId)
            c.indexInBackground(id)
        }
        toast("${ranges.size} fichiers créés")
        onDone(ranges.size)
    }

    fun organize(d: DocumentEntity, order: List<Int>, rotations: Map<Int, Int>, asCopy: Boolean, onDone: (Long) -> Unit) = tool("Enregistrement des pages…") {
        val out = c.documents.workFile()
        io { PdfOps.extract(f(d), order, out, rotations) }
        if (asCopy) onDone(publish(out, "${d.name} (modifié)", "Copie enregistrée", d.folderId))
        else { c.documents.replaceFile(d, out); c.indexInBackground(d.id); toast("Pages enregistrées"); onDone(d.id) }
    }

    fun compress(d: DocumentEntity, strong: Boolean, onDone: (Long) -> Unit) = tool("Compression…") {
        val out = c.documents.workFile()
        io { if (strong) PdfOps.compress(f(d), out, 1200, 0.5f) else PdfOps.compress(f(d), out, 1700, 0.68f) }
        val before = d.sizeBytes
        val after = out.length()
        if (after >= before * 0.97) { out.delete(); toast("Ce PDF est déjà optimisé"); return@tool }
        onDone(publish(out, "${d.name} (compressé)", "Taille : ${formatSize(before)} → ${formatSize(after)}", d.folderId))
    }

    fun watermark(d: DocumentEntity, text: String, onDone: (Long) -> Unit) = tool("Ajout du filigrane…") {
        val out = c.documents.workFile()
        io { PdfOps.watermark(f(d), out, text) }
        onDone(publish(out, "${d.name} (filigrane)", "Filigrane ajouté", d.folderId))
    }

    fun shareProtected(d: DocumentEntity, password: String, launch: (Intent) -> Unit) = tool("Protection du PDF…") {
        val out = c.documents.workFile()
        io { PdfOps.protect(f(d), out, password) }
        launch(c.exporter.shareFileIntent(out, "${d.name}.pdf", "application/pdf"))
        out.delete()
    }

    fun exportProtected(d: DocumentEntity, password: String, target: Uri) = tool("Protection du PDF…") {
        val out = c.documents.workFile()
        io { PdfOps.protect(f(d), out, password) }
        c.exporter.writeTo(out, target)
        out.delete()
        toast("PDF protégé exporté")
    }

    fun importProtected(uri: Uri, password: String, onDone: (Long) -> Unit) = tool("Déverrouillage…") {
        val id = c.documents.importProtectedPdf(uri, password)
        c.indexInBackground(id)
        toast("PDF déverrouillé et importé")
        onDone(id)
    }

    fun stamp(d: DocumentEntity, edits: Map<Int, PageEdits>, suffix: String, onDone: (Long) -> Unit) = tool("Enregistrement…") {
        val out = c.documents.workFile()
        io { PdfOps.stamp(f(d), out, edits) }
        onDone(publish(out, "${d.name} ($suffix)", "Nouveau document enregistré — l'original est conservé", d.folderId))
    }

    fun collage(d: DocumentEntity, perSheet: Int, onDone: (Long) -> Unit) = tool("Création du collage…") {
        val tmpDir = File(c.documents.workFile("dir").path).apply { mkdirs() }
        try {
            val sheets = withContext(Dispatchers.Default) {
                val pages = (0 until d.pageCount).map { PdfTools.renderPage(f(d), it, 1100) }
                Collage.nUp(pages, perSheet).also { pages.forEach { it.recycle() } }
            }
            val files = sheets.mapIndexed { i, b -> File(tmpDir, "s$i.jpg").also { ImageProcessor.writeJpeg(b, it, 88); b.recycle() } }
            val out = c.documents.workFile()
            io { JpegPdfWriter.write(files, out) }
            onDone(publish(out, "${d.name} (collage)", "Collage créé", d.folderId))
        } finally { tmpDir.deleteRecursively() }
    }

    /** Texte du document (OCR ou texte numérique), page par page. */
    suspend fun textOf(d: DocumentEntity): List<String> {
        val t = d.ocrText ?: run {
            _busy.value = "Reconnaissance du texte…"
            try { c.documents.indexText(d.id, c.ocr) } finally { _busy.value = null }
        }
        return t?.split('\u000C') ?: emptyList()
    }

    fun indexNow(d: DocumentEntity) = tool("Reconnaissance du texte…") {
        c.documents.indexText(d.id, c.ocr)
        toast("Texte analysé")
    }

    fun makeSearchable(d: DocumentEntity) = tool("Création du PDF avec texte sélectionnable…") {
        val n = c.documents.makeSearchable(d, c.ocr)
        toast(if (n > 0) "Texte ajouté sur $n page(s) : il est maintenant sélectionnable" else "Aucun texte reconnu")
    }

    fun exportDocx(d: DocumentEntity, target: Uri) = tool("Conversion en Word…") {
        val pages = textOf(d)
        val out = c.documents.workFile("docx")
        io { DocxWriter.write(pages, out) }
        c.exporter.writeTo(out, target)
        out.delete()
        toast("Document Word exporté")
    }

    fun backupAll(tree: Uri) = tool("Sauvegarde…") {
        val n = c.exporter.backup(c.documents.all(), tree)
        toast("$n document(s) sauvegardé(s)")
    }

    // --- Signatures ---
    fun signatures(): List<File> = c.signatures.list()
    fun saveSignature(bmp: Bitmap, onDone: () -> Unit) = tool("Enregistrement de la signature…") {
        c.signatures.save(bmp); toast("Signature enregistrée"); onDone()
    }
    fun deleteSignature(file: File) { c.signatures.delete(file); toast("Signature supprimée") }
    suspend fun signatureFromImage(uri: Uri): Bitmap = c.signatures.fromImage(uri)

    // --- Réglages ---
    fun setUserName(v: String) = run { c.settings.setUserName(v) }
    fun setTheme(v: ThemeMode) = run { c.settings.setTheme(v) }
    fun setHighQuality(v: Boolean) = run { c.settings.setHighQuality(v) }
    fun setExportFormat(v: ExportFormat) = run { c.settings.setExportFormat(v) }
    fun setGridView(v: Boolean) = run { c.settings.setGridView(v) }
    fun setSort(v: SortOrder) = run { c.settings.setSort(v) }
    fun setAutoOcr(v: Boolean) = run { c.settings.setAutoOcr(v) }

    // --- Stockage ---
    suspend fun storageUsed(): Long = c.documents.storageUsedBytes()
    suspend fun cacheSize(): Long = c.documents.cacheBytes()
    fun clearCache(then: () -> Unit) = run("Cache vidé") { c.documents.clearCache(); then() }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(c) }
        }
    }
}
