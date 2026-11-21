package com.docuscanpro.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.docuscanpro.app.data.db.DocumentDao
import com.docuscanpro.app.data.db.DocumentEntity
import com.docuscanpro.app.data.db.FolderDao
import com.docuscanpro.app.data.db.FolderEntity
import com.docuscanpro.app.imaging.ImageProcessor
import com.docuscanpro.app.pdf.JpegPdfWriter
import com.docuscanpro.app.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class ProtectedPdfException : Exception("Ce PDF est protégé par un mot de passe (pas encore pris en charge)")
class InvalidPdfException : Exception("Ce fichier PDF est illisible ou endommagé")

/**
 * Bibliothèque de documents : fichiers dans le stockage privé de l'app + métadonnées Room.
 * Aucun fichier n'est envoyé sur Internet.
 */
class DocumentRepository(
    private val context: Context,
    private val docs: DocumentDao,
    private val folders: FolderDao
) {
    private val docDir: File get() = File(context.filesDir, "documents").apply { mkdirs() }
    private val thumbDir: File get() = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun observeDocuments(): Flow<List<DocumentEntity>> = docs.observeAll()
    fun observeDocument(id: Long): Flow<DocumentEntity?> = docs.observe(id)
    fun observeFolders(): Flow<List<FolderEntity>> = folders.observeAll()

    /** Crée un PDF à partir de pages JPEG déjà traitées. */
    suspend fun createFromImages(name: String, pages: List<File>, folderId: Long?, source: String): Long =
        withContext(Dispatchers.IO) {
            require(pages.isNotEmpty()) { "Aucune page" }
            val key = UUID.randomUUID().toString()
            val pdf = File(docDir, "$key.pdf")
            JpegPdfWriter.write(pages, pdf)
            val thumb = File(thumbDir, "$key.jpg")
            ImageProcessor.writeThumbnail(pages.first(), thumb)
            val now = System.currentTimeMillis()
            docs.insert(
                DocumentEntity(
                    name = sanitize(name), filePath = pdf.absolutePath, thumbPath = thumb.absolutePath,
                    pageCount = pages.size, sizeBytes = pdf.length(), createdAt = now, modifiedAt = now,
                    folderId = folderId, source = source
                )
            )
        }

    /** Importe un PDF choisi par l'utilisateur (copie locale). */
    suspend fun importPdf(uri: Uri): Long = withContext(Dispatchers.IO) {
        val key = UUID.randomUUID().toString()
        val pdf = File(docDir, "$key.pdf")
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Lecture du fichier impossible")
        input.use { i -> pdf.outputStream().use { o -> i.copyTo(o) } }
        val count = try {
            PdfTools.pageCount(pdf)
        } catch (e: SecurityException) {
            pdf.delete(); throw ProtectedPdfException()
        } catch (e: Exception) {
            pdf.delete(); throw InvalidPdfException()
        }
        val thumb = File(thumbDir, "$key.jpg")
        val thumbOk = runCatching { PdfTools.renderThumbnail(pdf, thumb) }.isSuccess
        val name = displayName(uri)?.replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "") ?: "Document importé"
        val now = System.currentTimeMillis()
        docs.insert(
            DocumentEntity(
                name = sanitize(name), filePath = pdf.absolutePath, thumbPath = if (thumbOk) thumb.absolutePath else null,
                pageCount = count, sizeBytes = pdf.length(), createdAt = now, modifiedAt = now, source = "import"
            )
        )
    }

    suspend fun rename(doc: DocumentEntity, newName: String) =
        docs.update(doc.copy(name = sanitize(newName), modifiedAt = System.currentTimeMillis()))

    suspend fun setFavorite(doc: DocumentEntity, favorite: Boolean) = docs.update(doc.copy(favorite = favorite))

    suspend fun moveToFolder(doc: DocumentEntity, folderId: Long?) = docs.update(doc.copy(folderId = folderId))

    suspend fun markOpened(id: Long) {
        docs.get(id)?.let { docs.update(it.copy(lastOpenedAt = System.currentTimeMillis())) }
    }

    suspend fun duplicate(doc: DocumentEntity): Long = withContext(Dispatchers.IO) {
        val key = UUID.randomUUID().toString()
        val pdf = File(docDir, "$key.pdf")
        File(doc.filePath).copyTo(pdf, overwrite = true)
        val thumb = doc.thumbPath?.let { p ->
            val t = File(thumbDir, "$key.jpg")
            runCatching { File(p).copyTo(t, overwrite = true) }.getOrNull()?.absolutePath
        }
        val now = System.currentTimeMillis()
        docs.insert(
            doc.copy(
                id = 0, name = "${doc.name} (copie)", filePath = pdf.absolutePath, thumbPath = thumb,
                createdAt = now, modifiedAt = now, lastOpenedAt = null, favorite = false
            )
        )
    }

    suspend fun delete(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        File(doc.filePath).delete()
        doc.thumbPath?.let { File(it).delete() }
        docs.delete(doc)
    }

    suspend fun createFolder(name: String): Long =
        folders.insert(FolderEntity(name = name.trim().ifBlank { "Nouveau dossier" }, createdAt = System.currentTimeMillis()))

    suspend fun renameFolder(folder: FolderEntity, name: String) =
        folders.update(folder.copy(name = name.trim().ifBlank { folder.name }))

    suspend fun deleteFolder(folder: FolderEntity) {
        docs.clearFolder(folder.id)
        folders.delete(folder)
    }

    suspend fun storageUsedBytes(): Long = withContext(Dispatchers.IO) {
        listOf(docDir, thumbDir).sumOf { dir -> dir.listFiles()?.sumOf { it.length() } ?: 0L }
    }

    suspend fun cacheBytes(): Long = withContext(Dispatchers.IO) {
        context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    companion object {
        fun sanitize(name: String): String =
            name.trim().replace(Regex("[\\\\/:*?\"<>|\\n]"), "-").take(120).ifBlank { "Document" }
    }
}
