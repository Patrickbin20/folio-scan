package com.docuscanpro.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.docuscanpro.app.ui.documents.DocumentsScreen
import com.docuscanpro.app.ui.edit.PdfEditScreen
import com.docuscanpro.app.ui.pages.PagesScreen
import com.docuscanpro.app.ui.signature.SignatureScreen
import com.docuscanpro.app.ui.text.TextScreen
import com.docuscanpro.app.ui.tools.PasswordDialog
import com.docuscanpro.app.ui.web.WebToPdfScreen
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docuscanpro.app.ui.home.HomeScreen
import com.docuscanpro.app.ui.profile.ProfileScreen
import com.docuscanpro.app.ui.scan.EditorScreen
import com.docuscanpro.app.ui.state.LibraryViewModel
import com.docuscanpro.app.ui.state.ScanViewModel
import com.docuscanpro.app.ui.theme.Electric
import com.docuscanpro.app.ui.theme.Navy
import com.docuscanpro.app.ui.tools.ToolsScreen
import com.docuscanpro.app.ui.viewer.ViewerScreen
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

object Routes {
    const val HOME = "home"
    const val DOCS = "documents?filter={filter}&focus={focus}"
    const val TOOLS = "tools"
    const val PROFILE = "profile"
    const val EDITOR = "editor"
    const val VIEWER = "viewer/{id}"
    const val EDIT = "edit/{id}?mode={mode}"
    const val PAGES = "pages/{id}"
    const val TEXT = "text/{id}"
    const val SIGNATURES = "signatures"
    const val WEB = "web"
    fun edit(id: Long, mode: String) = "edit/$id?mode=$mode"
    fun pages(id: Long) = "pages/$id"
    fun text(id: Long) = "text/$id"
    fun docs(filter: String = "all", focus: Boolean = false) = "documents?filter=$filter&focus=$focus"
    fun viewer(id: Long) = "viewer/$id"
}

@Composable
fun AppRoot(vm: LibraryViewModel, scanVm: ScanViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val activity = LocalContext.current as Activity
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentRoute by rememberUpdatedState(route)

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var scanMode by remember { mutableStateOf("scan") }
    var protectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val busy by vm.busy.collectAsState()

    // --- Scanner ML Kit (bords, recadrage, perspective, capture auto/manuelle, multi-pages) ---
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            val uris = result?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) {
                if (currentRoute == Routes.EDITOR) scanVm.addPages(uris)
                else { scanVm.start(uris, scanMode); nav.navigate(Routes.EDITOR) }
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) {
            if (currentRoute == Routes.EDITOR) scanVm.addPages(uris)
            else { scanVm.start(uris, "images"); nav.navigate(Routes.EDITOR) }
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPdf(uri, onProtected = { protectedUri = uri }) { id -> nav.navigate(Routes.viewer(id)) }
    }

    val actions = remember {
        fun launchScanner(mode: String, limit: Int) {
            scanMode = mode
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(limit)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { vm.toast("Scanner indisponible : vérifiez les services Google Play (${it.localizedMessage ?: "erreur"})") }
        }
        AppActions(
            scan = { launchScanner("scan", 50) },
            scanIdCard = { vm.toast("Scannez le recto puis le verso de la carte"); launchScanner("idcard", 2) },
            importImages = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            importPdf = { pdfLauncher.launch(arrayOf("application/pdf")) }
        )
    }

    val tabRoutes = setOf(Routes.HOME, Routes.DOCS, Routes.TOOLS, Routes.PROFILE)

    CompositionLocalProvider(LocalAppActions provides actions) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { if (route != null && route in tabRoutes) BottomBar(nav, route, actions.scan) }
        ) { padding ->
            NavHost(
                nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize().padding(padding),
                enterTransition = { fadeIn() }, exitTransition = { fadeOut() }
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        vm,
                        onOpenDocument = { nav.navigate(Routes.viewer(it)) },
                        onOpenLibrary = { f, focus -> nav.navigateTab(Routes.docs(f, focus)) },
                        onOpenProfile = { nav.navigateTab(Routes.PROFILE) },
                        openEdit = { id, m -> nav.navigate(Routes.edit(id, m)) },
                        openPages = { nav.navigate(Routes.pages(it)) },
                        openText = { nav.navigate(Routes.text(it)) }
                    )
                }
                composable(
                    Routes.DOCS,
                    arguments = listOf(
                        navArgument("filter") { type = NavType.StringType; defaultValue = "all" },
                        navArgument("focus") { type = NavType.BoolType; defaultValue = false }
                    )
                ) { entry ->
                    DocumentsScreen(
                        vm,
                        initialFilter = entry.arguments?.getString("filter") ?: "all",
                        focusSearch = entry.arguments?.getBoolean("focus") ?: false,
                        onOpenDocument = { nav.navigate(Routes.viewer(it)) }
                    )
                }
                composable(Routes.TOOLS) {
                    ToolsScreen(
                        vm, openViewer = { nav.navigate(Routes.viewer(it)) }, openEdit = { id, m -> nav.navigate(Routes.edit(id, m)) },
                        openPages = { nav.navigate(Routes.pages(it)) }, openText = { nav.navigate(Routes.text(it)) },
                        openSignatures = { nav.navigate(Routes.SIGNATURES) }, openWeb = { nav.navigate(Routes.WEB) }
                    )
                }
                composable(Routes.EDIT, arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("mode") { type = NavType.StringType; defaultValue = "sign" }
                )) { entry ->
                    PdfEditScreen(
                        entry.arguments?.getLong("id") ?: 0L, entry.arguments?.getString("mode") ?: "sign", vm,
                        onBack = { nav.popBackStack() },
                        onOpenSignatures = { nav.navigate(Routes.SIGNATURES) },
                        onSaved = { newId -> nav.navigate(Routes.viewer(newId)) { popUpTo(Routes.EDIT) { inclusive = true } } }
                    )
                }
                composable(Routes.PAGES, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    PagesScreen(entry.arguments?.getLong("id") ?: 0L, vm, onBack = { nav.popBackStack() },
                        onOpen = { newId -> nav.navigate(Routes.viewer(newId)) { popUpTo(Routes.PAGES) { inclusive = true } } })
                }
                composable(Routes.TEXT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    TextScreen(entry.arguments?.getLong("id") ?: 0L, vm, onBack = { nav.popBackStack() })
                }
                composable(Routes.SIGNATURES) { SignatureScreen(vm, onBack = { nav.popBackStack() }) }
                composable(Routes.WEB) { WebToPdfScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.PROFILE) { ProfileScreen(vm) }
                composable(Routes.EDITOR) {
                    EditorScreen(
                        scanVm, vm,
                        onClose = { if (nav.currentDestination?.route == Routes.EDITOR) nav.popBackStack() },
                        onSaved = { id ->
                            vm.toast("Document enregistré")
                            nav.navigate(Routes.viewer(id)) { popUpTo(Routes.EDITOR) { inclusive = true } }
                        }
                    )
                }
                composable(Routes.VIEWER, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    ViewerScreen(
                        entry.arguments?.getLong("id") ?: 0L, vm, onBack = { nav.popBackStack() },
                        openViewer = { nav.navigate(Routes.viewer(it)) }, openEdit = { id, m -> nav.navigate(Routes.edit(id, m)) },
                        openPages = { nav.navigate(Routes.pages(it)) }, openText = { nav.navigate(Routes.text(it)) }
                    )
                }
            }
        }
        protectedUri?.let { uri ->
            PasswordDialog("PDF protégé", "Ce PDF est protégé. Saisissez son mot de passe pour l'importer déverrouillé.", false, { protectedUri = null },
                actions = listOf("Déverrouiller" to { pw: String -> protectedUri = null; vm.importProtected(uri, pw) { id -> nav.navigate(Routes.viewer(id)) } }))
        }
        busy?.let { msg ->
            Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(msg, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = false }
        launchSingleTop = true
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?, onScan: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround
            ) {
                TabItem(Icons.Outlined.Home, Icons.Filled.Home, "Accueil", route == Routes.HOME, Modifier.weight(1f)) { nav.navigateTab(Routes.HOME) }
                TabItem(Icons.Outlined.Folder, Icons.Filled.Folder, "Documents", route == Routes.DOCS, Modifier.weight(1f)) { nav.navigateTab(Routes.docs()) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(58.dp).shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = Electric, spotColor = Electric)
                            .clip(RoundedCornerShape(20.dp)).background(Navy).clickable(onClick = onScan),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.DocumentScanner, "Scanner", tint = Color.White, modifier = Modifier.size(28.dp)) }
                }
                TabItem(Icons.Outlined.Handyman, Icons.Filled.Handyman, "Outils", route == Routes.TOOLS, Modifier.weight(1f)) { nav.navigateTab(Routes.TOOLS) }
                TabItem(Icons.Outlined.Person, Icons.Filled.Person, "Profil", route == Routes.PROFILE, Modifier.weight(1f)) { nav.navigateTab(Routes.PROFILE) }
            }
        }
    }
}

@Composable
private fun TabItem(icon: ImageVector, selectedIcon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) { Icon(if (selected) selectedIcon else icon, label, tint = color) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}
