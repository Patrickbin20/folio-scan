package com.docuscanpro.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.docuscanpro.app.data.db.DocumentEntity
import com.docuscanpro.app.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Export vers le stockage du téléphone (SAF) et partage via la feuille de partage Android. */
class ExportManager(private val context: Context) {

    private fun shareDir(): File = File(context.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
    private fun uriFor(f: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)

    suspend fun exportPdf(doc: DocumentEntity, target: Uri) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(target) ?: throw IOException("Écriture impossible")
        out.use { o -> File(doc.filePath).inputStream().use { it.copyTo(o) } }
    }

    /** Écrit une image JPEG par page dans le dossier choisi. Renvoie le nombre d'images. */
    suspend fun exportJpegs(doc: DocumentEntity, tree: Uri): Int = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val tmp = File(context.cacheDir, "export").apply { deleteRecursively(); mkdirs() }
        val files = PdfTools.renderPagesToJpeg(File(doc.filePath), tmp, doc.name)
        files.forEach { f ->
            val uri = DocumentsContract.createDocument(cr, parent, "image/jpeg", f.name) ?: throw IOException("Création du fichier impossible")
            val out = cr.openOutputStream(uri) ?: throw IOException("Écriture impossible")
            out.use { o -> f.inputStream().use { it.copyTo(o) } }
        }
        tmp.deleteRecursively()
        files.size
    }

    suspend fun shareIntent(doc: DocumentEntity, format: ExportFormat): Intent = withContext(Dispatchers.IO) {
        val dir = shareDir()
        val intent = if (format == ExportFormat.PDF) {
            val f = File(dir, "${doc.name}.pdf")
            File(doc.filePath).copyTo(f, overwrite = true)
            Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uriFor(f))
        } else {
            val uris = ArrayList(PdfTools.renderPagesToJpeg(File(doc.filePath), dir, doc.name).map { uriFor(it) })
            if (uris.size == 1) Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uris[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, doc.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Intent.createChooser(intent, "Partager « ${doc.name} »").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
