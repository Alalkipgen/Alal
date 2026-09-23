package com.alal.notes.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alal.notes.domain.model.AccentMode
import com.alal.notes.domain.model.AppThemeKey
import com.alal.notes.domain.model.CardStyle
import com.alal.notes.domain.model.MarginMode
import com.alal.notes.domain.model.PaperTexture
import com.alal.notes.domain.model.SortMode
import com.alal.notes.domain.model.TitleSize
import com.alal.notes.domain.model.UiDensity
import com.alal.notes.domain.model.ViewMode
import com.alal.notes.domain.model.WordCountMethod
import com.alal.notes.domain.model.enumOrDefault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.alalDataStore: DataStore<Preferences> by preferencesDataStore(name = "alal_settings")

data class Settings(
    val theme: AppThemeKey = AppThemeKey.SYSTEM,
    val accentMode: AccentMode = AccentMode.DYNAMIC,
    /** ARGB. Used for PRESET and CUSTOM. */
    val accentColor: Int = DEFAULT_ACCENT,
    val dynamicColor: Boolean = true,
    val bodyFont: String = "inter",
    val titleFont: String = "literata",
    val myanmarFont: String = "pyidaungsu",
    val bodySize: Int = 18,
    val titleSize: TitleSize = TitleSize.M,
    val lineHeight: Float = 1.6f,
    val margin: MarginMode = MarginMode.NORMAL,
    val cardPreviewSize: Int = 15,
    val uiDensity: UiDensity = UiDensity.DEFAULT,
    val paperTexture: PaperTexture = PaperTexture.DOTTED,
    val cardStyle: CardStyle = CardStyle.SOFT,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortMode: SortMode = SortMode.MODIFIED,
    val themedIcon: Boolean = true,
    val typewriterMode: Boolean = false,
    val paragraphFocus: Boolean = true,
    val autoSaveDelayMs: Int = 1000,
    val wordCountMethod: WordCountMethod = WordCountMethod.MYANMAR_SYLLABLE,
    val dailyGoal: Int = 0,
    val language: String = "",
    val recentSearches: List<String> = emptyList(),
    val lastCategoryFilter: Long = -1L,
    // ---- Phase 2 ----
    /** SAF tree URI of the auto-backup folder, or empty = off. */
    val autoBackupUri: String = "",
    val lastBackupAt: Long = 0L,
    val lockEnabled: Boolean = false,
    /** Hex SHA-256 of (salt + PIN). Empty = no PIN set. */
    val lockPinHash: String = "",
    val lockSalt: String = "",
    val lockBiometric: Boolean = true,
    /** Re-lock after this many seconds in background (0 = immediately). */
    val lockTimeoutSec: Int = 0,
) {
    companion object {
        /** Material You primary; used when the device has no wallpaper colours. */
        const val DEFAULT_ACCENT: Int = 0xFF6750A4.toInt()
    }
}

class UserPreferences(private val store: DataStore<Preferences>) {

    private object K {
        val theme = stringPreferencesKey("theme")
        val accentMode = stringPreferencesKey("accentMode")
        val accentColor = intPreferencesKey("accentColor")
        val dynamicColor = booleanPreferencesKey("dynamicColor")
        val bodyFont = stringPreferencesKey("bodyFont")
        val titleFont = stringPreferencesKey("titleFont")
        val myanmarFont = stringPreferencesKey("myanmarFont")
        val bodySize = intPreferencesKey("bodySize")
        val titleSize = stringPreferencesKey("titleSize")
        val lineHeight = floatPreferencesKey("lineHeight")
        val margin = stringPreferencesKey("margin")
        val cardPreviewSize = intPreferencesKey("cardPreviewSize")
        val uiDensity = stringPreferencesKey("uiDensity")
        val paperTexture = stringPreferencesKey("paperTexture")
        val cardStyle = stringPreferencesKey("cardStyle")
        val viewMode = stringPreferencesKey("viewMode")
        val sortMode = stringPreferencesKey("sortMode")
        val themedIcon = booleanPreferencesKey("themedIcon")
        val typewriterMode = booleanPreferencesKey("typewriterMode")
        val paragraphFocus = booleanPreferencesKey("paragraphFocus")
        val autoSaveDelayMs = intPreferencesKey("autoSaveDelayMs")
        val wordCountMethod = stringPreferencesKey("wordCountMethod")
        val dailyGoal = intPreferencesKey("dailyGoal")
        val language = stringPreferencesKey("language")
        val recentSearches = stringPreferencesKey("recentSearches")
        val lastCategoryFilter = longPreferencesKey("lastCategoryFilter")
        val autoBackupUri = stringPreferencesKey("autoBackupUri")
        val lastBackupAt = longPreferencesKey("lastBackupAt")
        val lockEnabled = booleanPreferencesKey("lockEnabled")
        val lockPinHash = stringPreferencesKey("lockPinHash")
        val lockSalt = stringPreferencesKey("lockSalt")
        val lockBiometric = booleanPreferencesKey("lockBiometric")
        val lockTimeoutSec = intPreferencesKey("lockTimeoutSec")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        val d = Settings()
        Settings(
            theme = enumOrDefault(p[K.theme], d.theme),
            accentMode = enumOrDefault(p[K.accentMode], d.accentMode),
            accentColor = p[K.accentColor] ?: d.accentColor,
            dynamicColor = p[K.dynamicColor] ?: d.dynamicColor,
            bodyFont = p[K.bodyFont] ?: d.bodyFont,
            titleFont = p[K.titleFont] ?: d.titleFont,
            myanmarFont = p[K.myanmarFont] ?: d.myanmarFont,
            bodySize = (p[K.bodySize] ?: d.bodySize).coerceIn(14, 30),
            titleSize = enumOrDefault(p[K.titleSize], d.titleSize),
            lineHeight = p[K.lineHeight] ?: d.lineHeight,
            margin = enumOrDefault(p[K.margin], d.margin),
            cardPreviewSize = (p[K.cardPreviewSize] ?: d.cardPreviewSize).coerceIn(13, 17),
            uiDensity = enumOrDefault(p[K.uiDensity], d.uiDensity),
            paperTexture = enumOrDefault(p[K.paperTexture], d.paperTexture),
            cardStyle = enumOrDefault(p[K.cardStyle], d.cardStyle),
            viewMode = enumOrDefault(p[K.viewMode], d.viewMode),
            sortMode = enumOrDefault(p[K.sortMode], d.sortMode),
            themedIcon = p[K.themedIcon] ?: d.themedIcon,
            typewriterMode = p[K.typewriterMode] ?: d.typewriterMode,
            paragraphFocus = p[K.paragraphFocus] ?: d.paragraphFocus,
            autoSaveDelayMs = p[K.autoSaveDelayMs] ?: d.autoSaveDelayMs,
            wordCountMethod = enumOrDefault(p[K.wordCountMethod], d.wordCountMethod),
            dailyGoal = p[K.dailyGoal] ?: d.dailyGoal,
            language = p[K.language] ?: d.language,
            recentSearches = p[K.recentSearches]?.split('\u001F')?.filter { it.isNotBlank() } ?: emptyList(),
            lastCategoryFilter = p[K.lastCategoryFilter] ?: d.lastCategoryFilter,
            autoBackupUri = p[K.autoBackupUri] ?: d.autoBackupUri,
            lastBackupAt = p[K.lastBackupAt] ?: d.lastBackupAt,
            lockEnabled = p[K.lockEnabled] ?: d.lockEnabled,
            lockPinHash = p[K.lockPinHash] ?: d.lockPinHash,
            lockSalt = p[K.lockSalt] ?: d.lockSalt,
            lockBiometric = p[K.lockBiometric] ?: d.lockBiometric,
            lockTimeoutSec = p[K.lockTimeoutSec] ?: d.lockTimeoutSec,
        )
    }

    private suspend fun set(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    suspend fun setTheme(v: AppThemeKey) = set { it[K.theme] = v.name }
    suspend fun setAccentMode(v: AccentMode) = set { it[K.accentMode] = v.name }
    suspend fun setAccentColor(v: Int) = set { it[K.accentColor] = v }
    suspend fun setDynamicColor(v: Boolean) = set { it[K.dynamicColor] = v }
    suspend fun setBodyFont(v: String) = set { it[K.bodyFont] = v }
    suspend fun setTitleFont(v: String) = set { it[K.titleFont] = v }
    suspend fun setMyanmarFont(v: String) = set { it[K.myanmarFont] = v }
    suspend fun setBodySize(v: Int) = set { it[K.bodySize] = v.coerceIn(14, 30) }
    suspend fun setTitleSize(v: TitleSize) = set { it[K.titleSize] = v.name }
    suspend fun setLineHeight(v: Float) = set { it[K.lineHeight] = v }
    suspend fun setMargin(v: MarginMode) = set { it[K.margin] = v.name }
    suspend fun setCardPreviewSize(v: Int) = set { it[K.cardPreviewSize] = v.coerceIn(13, 17) }
    suspend fun setUiDensity(v: UiDensity) = set { it[K.uiDensity] = v.name }
    suspend fun setPaperTexture(v: PaperTexture) = set { it[K.paperTexture] = v.name }
    suspend fun setCardStyle(v: CardStyle) = set { it[K.cardStyle] = v.name }
    suspend fun setViewMode(v: ViewMode) = set { it[K.viewMode] = v.name }
    suspend fun setSortMode(v: SortMode) = set { it[K.sortMode] = v.name }
    suspend fun setThemedIcon(v: Boolean) = set { it[K.themedIcon] = v }
    suspend fun setTypewriterMode(v: Boolean) = set { it[K.typewriterMode] = v }
    suspend fun setParagraphFocus(v: Boolean) = set { it[K.paragraphFocus] = v }
    suspend fun setAutoSaveDelayMs(v: Int) = set { it[K.autoSaveDelayMs] = v.coerceIn(300, 5000) }
    suspend fun setWordCountMethod(v: WordCountMethod) = set { it[K.wordCountMethod] = v.name }
    suspend fun setDailyGoal(v: Int) = set { it[K.dailyGoal] = v.coerceIn(0, 20000) }
    suspend fun setLanguage(v: String) = set { it[K.language] = v }
    suspend fun setLastCategoryFilter(v: Long) = set { it[K.lastCategoryFilter] = v }

    suspend fun addRecentSearch(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        set {
            val cur = it[K.recentSearches]?.split('\u001F')?.filter { s -> s.isNotBlank() } ?: emptyList()
            val next = (listOf(t) + cur.filter { s -> !s.equals(t, ignoreCase = true) }).take(8)
            it[K.recentSearches] = next.joinToString("\u001F")
        }
    }

    suspend fun clearRecentSearches() = set { it.remove(K.recentSearches) }

    // ---- Phase 2 ----
    suspend fun setAutoBackupUri(v: String) = set { it[K.autoBackupUri] = v }
    suspend fun setLastBackupAt(v: Long) = set { it[K.lastBackupAt] = v }
    suspend fun setLockEnabled(v: Boolean) = set { it[K.lockEnabled] = v }
    suspend fun setLockBiometric(v: Boolean) = set { it[K.lockBiometric] = v }
    suspend fun setLockTimeoutSec(v: Int) = set { it[K.lockTimeoutSec] = v }
    suspend fun setLockPin(hash: String, salt: String) = set { it[K.lockPinHash] = hash; it[K.lockSalt] = salt }
    suspend fun clearLock() = set {
        it.remove(K.lockPinHash); it.remove(K.lockSalt); it[K.lockEnabled] = false
    }

    /**
     * Snapshot of every stored preference as strings (for backup files). Security-sensitive keys
     * (PIN hash/salt) and device-specific keys (SAF folder URI) are deliberately excluded.
     */
    suspend fun exportAll(): Map<String, String> {
        val p = store.data.first()
        val out = LinkedHashMap<String, String>()
        for ((key, value) in p.asMap()) {
            if (key.name in EXCLUDED_FROM_BACKUP) continue
            out[key.name] = when (value) {
                is Boolean -> "b:$value"
                is Int -> "i:$value"
                is Long -> "l:$value"
                is Float -> "f:$value"
                is Double -> "d:$value"
                else -> "s:$value"
            }
        }
        return out
    }

    /** Restores preferences written by [exportAll]. Unknown or malformed entries are skipped. */
    suspend fun importAll(map: Map<String, String>) = set { prefs ->
        for ((name, raw) in map) {
            if (name in EXCLUDED_FROM_BACKUP || raw.length < 2 || raw[1] != ':') continue
            val v = raw.substring(2)
            try {
                when (raw[0]) {
                    'b' -> prefs[booleanPreferencesKey(name)] = v.toBoolean()
                    'i' -> prefs[intPreferencesKey(name)] = v.toInt()
                    'l' -> prefs[longPreferencesKey(name)] = v.toLong()
                    'f' -> prefs[floatPreferencesKey(name)] = v.toFloat()
                    'd' -> prefs[doublePreferencesKey(name)] = v.toDouble()
                    's' -> prefs[stringPreferencesKey(name)] = v
                }
            } catch (_: Exception) {
                // skip bad value
            }
        }
    }

    private companion object {
        val EXCLUDED_FROM_BACKUP = setOf("lockPinHash", "lockSalt", "lockEnabled", "autoBackupUri", "lastBackupAt")
    }
}
