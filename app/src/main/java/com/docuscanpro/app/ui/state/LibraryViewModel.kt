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
import com.docuscanpro.app.data.ExportFormat
import com.docuscanpro.app.data.SortOrder
import com.docuscanpro.app.data.ThemeMode
import com.docuscanpro.app.data.db.DocumentEntity
import com.docuscanpro.app.data.db.FolderEntity
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

    fun importPdf(uri: Uri, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val id = c.documents.importPdf(uri)
                toast("PDF importé")
                onDone(id)
            } catch (e: Exception) { toast(e.message ?: "Import impossible") }
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

    // --- Réglages ---
    fun setUserName(v: String) = run { c.settings.setUserName(v) }
    fun setTheme(v: ThemeMode) = run { c.settings.setTheme(v) }
    fun setHighQuality(v: Boolean) = run { c.settings.setHighQuality(v) }
    fun setExportFormat(v: ExportFormat) = run { c.settings.setExportFormat(v) }
    fun setGridView(v: Boolean) = run { c.settings.setGridView(v) }
    fun setSort(v: SortOrder) = run { c.settings.setSort(v) }

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
