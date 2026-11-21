package com.docuscanpro.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Métadonnées d'un document. Le fichier PDF est stocké dans le stockage privé de l'app. */
@Entity(tableName = "documents", indices = [Index("folderId")])
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String,
    val thumbPath: String?,
    val pageCount: Int,
    val sizeBytes: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val lastOpenedAt: Long? = null,
    val favorite: Boolean = false,
    val folderId: Long? = null,
    /** scan | images | import | idcard | tool */
    val source: String = "scan",
    /** Texte reconnu (OCR) ou extrait, pages séparées par \u000C — sert à la recherche */
    val ocrText: String? = null
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)
