package com.docuscanpro.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.docuscanpro.app.data.ThemeMode
import com.docuscanpro.app.ui.AppRoot
import com.docuscanpro.app.ui.state.LibraryViewModel
import com.docuscanpro.app.ui.state.ScanViewModel
import com.docuscanpro.app.ui.theme.DocuScanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DocuScanApp).container
        setContent {
            val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
            val scanVm: ScanViewModel = viewModel(factory = ScanViewModel.factory(applicationContext, container))
            val settings by vm.settings.collectAsState()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            DocuScanTheme(dark) { AppRoot(vm, scanVm) }
        }
    }
}
