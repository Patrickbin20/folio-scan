package com.docuscanpro.app

import android.app.Application
import com.docuscanpro.app.data.AppContainer

/** Point d'entrée de l'application : crée les dépendances partagées (injection manuelle). */
class DocuScanApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
