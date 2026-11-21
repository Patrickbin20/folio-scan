package com.docuscanpro.app.data

import android.content.Context
import com.docuscanpro.app.data.db.AppDatabase
import com.docuscanpro.app.ocr.OcrEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Conteneur de dépendances (base Room, réglages, documents, export). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = AppDatabase.build(appContext)
    val settings = SettingsRepository(appContext)
    val documents = DocumentRepository(appContext, database.documentDao(), database.folderDao())
    val exporter = ExportManager(appContext)
    val signatures = SignatureStore(appContext)
    val ocr = OcrEngine()

    /** Portée de l'application pour les tâches de fond (indexation OCR). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Reconnaissance du texte en arrière-plan pour la recherche dans le contenu (si activée). */
    fun indexInBackground(id: Long) {
        appScope.launch {
            if (!settings.settings.first().autoOcr) return@launch
            runCatching { documents.indexText(id, ocr) }
        }
    }
}
