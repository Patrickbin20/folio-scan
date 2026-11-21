package com.docuscanpro.app.data

import android.content.Context
import com.docuscanpro.app.data.db.AppDatabase

/** Conteneur de dépendances (base Room, réglages, documents, export). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = AppDatabase.build(appContext)
    val settings = SettingsRepository(appContext)
    val documents = DocumentRepository(appContext, database.documentDao(), database.folderDao())
    val exporter = ExportManager(appContext)
}
