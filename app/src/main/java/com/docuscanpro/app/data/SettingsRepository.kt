package com.docuscanpro.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ExportFormat { PDF, JPEG }
enum class SortOrder { DATE, NAME, SIZE }

data class AppSettings(
    val userName: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val highQuality: Boolean = true,
    val exportFormat: ExportFormat = ExportFormat.PDF,
    val gridView: Boolean = false,
    val sort: SortOrder = SortOrder.DATE
)

/** Réglages persistants (DataStore). */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val name = stringPreferencesKey("user_name")
        val theme = stringPreferencesKey("theme")
        val quality = booleanPreferencesKey("high_quality")
        val export = stringPreferencesKey("export_format")
        val grid = booleanPreferencesKey("grid_view")
        val sort = stringPreferencesKey("sort")
    }

    private inline fun <reified T : Enum<T>> Preferences.enumOf(key: Preferences.Key<String>, default: T): T =
        this[key]?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            AppSettings(
                userName = p[Keys.name] ?: "",
                theme = p.enumOf(Keys.theme, ThemeMode.SYSTEM),
                highQuality = p[Keys.quality] ?: true,
                exportFormat = p.enumOf(Keys.export, ExportFormat.PDF),
                gridView = p[Keys.grid] ?: false,
                sort = p.enumOf(Keys.sort, SortOrder.DATE)
            )
        }

    suspend fun setUserName(v: String) { context.dataStore.edit { it[Keys.name] = v.trim() } }
    suspend fun setTheme(v: ThemeMode) { context.dataStore.edit { it[Keys.theme] = v.name } }
    suspend fun setHighQuality(v: Boolean) { context.dataStore.edit { it[Keys.quality] = v } }
    suspend fun setExportFormat(v: ExportFormat) { context.dataStore.edit { it[Keys.export] = v.name } }
    suspend fun setGridView(v: Boolean) { context.dataStore.edit { it[Keys.grid] = v } }
    suspend fun setSort(v: SortOrder) { context.dataStore.edit { it[Keys.sort] = v.name } }
}
