package com.docuscanpro.app.ui.web

import android.annotation.SuppressLint
import android.content.Context
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.docuscanpro.app.ui.components.EmptyState

/** Convertit une page web en PDF via le service d'impression Android (« Enregistrer au format PDF »). */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebToPdfScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("https://") }
    var loaded by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    val web = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
            }
        }
    }
    fun go() {
        val u = url.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        url = u; loaded = u; web.loadUrl(u)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Page web en PDF", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val title = web.title?.takeIf { it.isNotBlank() } ?: "Page web"
                    pm.print(title, web.createPrintDocumentAdapter(title), null)
                },
                enabled = loaded != null && progress >= 100,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(54.dp), shape = RoundedCornerShape(16.dp)
            ) { Text("Créer le PDF") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                url, { url = it }, singleLine = true, label = { Text("Adresse du site") }, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                trailingIcon = { IconButton(onClick = { go() }) { Icon(Icons.Outlined.Language, "Charger") } },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            if (loaded != null && progress < 100) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            Text("Dans la fenêtre d'impression, choisissez « Enregistrer au format PDF ». Importez ensuite le fichier dans DocuScan.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                if (loaded == null) EmptyState(Icons.Outlined.Language, "Saisissez une adresse", "La page s'affichera ici avant la conversion.")
                else AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
