package com.docuscanpro.app.ui.state

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.docuscanpro.app.data.AppContainer
import com.docuscanpro.app.imaging.ImageProcessor
import com.docuscanpro.app.imaging.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class ScanPage(
    val id: Long,
    val uri: Uri,
    val rotation: Int = 0,
    val filter: ScanFilter = ScanFilter.ORIGINAL,
    val brightness: Float = 0f,
    val contrast: Float = 1f
)

data class ScanUiState(
    val pages: List<ScanPage> = emptyList(),
    val selected: Int = 0,
    val name: String = "",
    val folderId: Long? = null,
    val source: String = "scan",
    val saving: Boolean = false,
    val progress: Float = 0f
)

/** Session d'édition des pages scannées / importées avant enregistrement en PDF. */
class ScanViewModel(private val context: Context, private val c: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()
    private val ids = AtomicLong(1)

    // Cache des images d'aperçu décodées (clé = uri + taille)
    private val previewCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 12
    }

    private fun defaultName(source: String): String {
        val d = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.FRANCE).format(Date())
        return if (source == "images") "Images $d" else "Scan $d"
    }

    fun start(uris: List<Uri>, source: String) {
        synchronized(previewCache) { previewCache.clear() }
        _state.value = ScanUiState(
            pages = uris.map { ScanPage(ids.getAndIncrement(), it) },
            name = defaultName(source),
            source = source
        )
    }

    fun addPages(uris: List<Uri>) = _state.update { s ->
        s.copy(pages = s.pages + uris.map { ScanPage(ids.getAndIncrement(), it) }, selected = s.pages.size)
    }

    fun clear() { _state.value = ScanUiState(); synchronized(previewCache) { previewCache.clear() } }

    fun select(i: Int) = _state.update { it.copy(selected = i.coerceIn(0, (it.pages.size - 1).coerceAtLeast(0))) }

    private fun editSelected(f: (ScanPage) -> ScanPage) = _state.update { s ->
        if (s.pages.isEmpty()) s else s.copy(pages = s.pages.mapIndexed { i, p -> if (i == s.selected) f(p) else p })
    }

    fun rotate() = editSelected { it.copy(rotation = (it.rotation + 90) % 360) }
    fun setFilter(f: ScanFilter) = editSelected { it.copy(filter = f) }
    fun setBrightness(v: Float) = editSelected { it.copy(brightness = v) }
    fun setContrast(v: Float) = editSelected { it.copy(contrast = v) }
    fun resetAdjust() = editSelected { it.copy(brightness = 0f, contrast = 1f) }

    fun applyToAll() = _state.update { s ->
        val cur = s.pages.getOrNull(s.selected) ?: return@update s
        s.copy(pages = s.pages.map { it.copy(filter = cur.filter, brightness = cur.brightness, contrast = cur.contrast) })
    }

    fun move(delta: Int) = _state.update { s ->
        val from = s.selected
        val to = from + delta
        if (to !in s.pages.indices) return@update s
        val list = s.pages.toMutableList()
        val p = list.removeAt(from)
        list.add(to, p)
        s.copy(pages = list, selected = to)
    }

    fun deleteSelected() = _state.update { s ->
        if (s.pages.isEmpty()) return@update s
        val list = s.pages.toMutableList().apply { removeAt(s.selected) }
        s.copy(pages = list, selected = s.selected.coerceAtMost((list.size - 1).coerceAtLeast(0)))
    }

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setFolder(id: Long?) = _state.update { it.copy(folderId = id) }

    /** Image de base (sans réglages) réduite pour l'aperçu. */
    suspend fun base(uri: Uri, maxDim: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$uri@$maxDim"
        synchronized(previewCache) { previewCache[key] }?.let { return@withContext it }
        runCatching { ImageProcessor.decodeSampled(context, uri, maxDim) }.getOrNull()?.also {
            synchronized(previewCache) { previewCache[key] = it }
        }
    }

    suspend fun preview(page: ScanPage, maxDim: Int = 1100): Bitmap? {
        val b = base(page.uri, maxDim) ?: return null
        return withContext(Dispatchers.Default) {
            ImageProcessor.process(b, page.rotation, page.filter, page.brightness, page.contrast)
        }
    }

    /** Traite chaque page en pleine résolution et crée le PDF. */
    fun save(onDone: (Long) -> Unit, onError: (String) -> Unit) {
        val s = _state.value
        if (s.pages.isEmpty() || s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, progress = 0f) }
            val tmp = File(context.cacheDir, "scan-${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                val hq = c.settings.settings.first().highQuality
                val maxDim = if (hq) 2400 else 1600
                val quality = if (hq) 90 else 80
                val files = withContext(Dispatchers.Default) {
                    s.pages.mapIndexed { i, p ->
                        val src = ImageProcessor.decodeSampled(context, p.uri, maxDim)
                        val out = ImageProcessor.process(src, p.rotation, p.filter, p.brightness, p.contrast)
                        src.recycle()
                        val f = File(tmp, "page_${i + 1}.jpg")
                        ImageProcessor.writeJpeg(out, f, quality)
                        out.recycle()
                        _state.update { it.copy(progress = (i + 1f) / (s.pages.size + 1)) }
                        f
                    }
                }
                val id = c.documents.createFromImages(s.name, files, s.folderId, s.source)
                clear()
                onDone(id)
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(saving = false) }
                onError("Mémoire insuffisante : choisissez la qualité Standard dans le Profil")
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                onError(e.message ?: "Échec de l'enregistrement")
            } finally {
                tmp.deleteRecursively()
            }
        }
    }

    companion object {
        fun factory(context: Context, c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScanViewModel(context.applicationContext, c) }
        }
    }
}
