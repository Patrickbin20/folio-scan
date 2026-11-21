package com.docuscanpro.app

import android.app.Application
import com.docuscanpro.app.data.AppContainer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/** Point d'entrée de l'application : crée les dépendances partagées (injection manuelle). */
class DocuScanApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}
