package com.docuscanpro.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Actions globales accessibles depuis tous les écrans (scanner, imports). */
class AppActions(
    val scan: () -> Unit,
    val importImages: () -> Unit,
    val importPdf: () -> Unit
)

val LocalAppActions = staticCompositionLocalOf { AppActions({}, {}, {}) }
