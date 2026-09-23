package com.alal.notes.domain.model

enum class NoteStatus { IDEA, RESEARCH, DRAFT, EDITING, SUBMITTED, PUBLISHED }

enum class PaperTexture { PLAIN, DOTTED, LINED, GRID }

enum class ViewMode { GRID, LIST, COMPACT }

enum class SortMode { MODIFIED, CREATED, TITLE, WORDS }

/** MYANMAR_SYLLABLE (name kept for stored prefs) = ICU dictionary-based word segmentation, locale "my". */
enum class WordCountMethod { MYANMAR_SYLLABLE, SPACE_SPLIT }

enum class UiDensity { COMPACT, DEFAULT, COMFORTABLE }

enum class CardStyle { SOFT, DIVIDER }

enum class MarginMode { NARROW, NORMAL, WIDE }

enum class TitleSize { S, M, L }

enum class AccentMode { DYNAMIC, PRESET, CUSTOM }

/** Theme keys from the spec (§3.4). `isDark == null` means follow the OS. */
enum class AppThemeKey(val isDark: Boolean?) {
    MATERIAL(false), PAPER(false), WHITE(false), SEPIA(false), MINT(false), SKY(false), ROSE(false),
    MIDNIGHT(true), INK(true), SLATE(true), FOREST(true), BLACK(true),
    SYSTEM(null);
}

inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
