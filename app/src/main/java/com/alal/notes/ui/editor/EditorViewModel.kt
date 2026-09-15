package com.alal.notes.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.notes.data.entity.Category
import com.alal.notes.data.entity.Note
import com.alal.notes.data.entity.NoteVersion
import com.alal.notes.data.entity.Tag
import com.alal.notes.data.entity.Template
import com.alal.notes.data.export.ExportFormat
import com.alal.notes.data.export.NoteExporter
import com.alal.notes.data.prefs.UserPreferences
import com.alal.notes.data.repository.NoteRepository
import com.alal.notes.data.work.ReminderScheduler
import com.alal.notes.domain.findreplace.FindMatch
import com.alal.notes.domain.findreplace.FindOptions
import com.alal.notes.domain.findreplace.FindReplace
import com.alal.notes.domain.findreplace.FindResult
import com.alal.notes.domain.markdown.EditResult
import com.alal.notes.domain.markdown.MarkdownToggle
import com.alal.notes.domain.markdown.Outline
import com.alal.notes.domain.markdown.OutlineItem
import com.alal.notes.domain.markdown.PrefixKind
import com.alal.notes.domain.markdown.TextSelection
import com.alal.notes.domain.model.NoteStatus
import com.alal.notes.domain.model.PaperTexture
import com.alal.notes.domain.model.WordCountMethod
import com.alal.notes.domain.wordcount.TextStats
import com.alal.notes.domain.wordcount.WordCounter
import com.alal.notes.domain.wordcount.isMyanmarChar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class FindState(
    val visible: Boolean = false,
    val query: String = "",
    val replacement: String = "",
    val options: FindOptions = FindOptions(),
    val matches: List<FindMatch> = emptyList(),
    val current: Int = -1,
    val error: String? = null,
    val showReplace: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val prefs: UserPreferences,
    private val reminders: ReminderScheduler,
    private val exporter: NoteExporter,
    private val counter: WordCounter,
) : ViewModel() {

    val titleState = TextFieldState()
    val bodyState = TextFieldState()

    private val noteId = MutableStateFlow(0L)
    private val loadedNote = MutableStateFlow<Note?>(null)
    private var loadJob: Job? = null
    private val saveMutex = Mutex()
    private var method: WordCountMethod = WordCountMethod.MYANMAR_SYLLABLE
    private var autoSaveDelay: Long = 1000L
    private var dirty = false
    private var statsRefreshJob: Job? = null
    private var statsSeedBody: String? = null

    private companion object {
        const val LONG_NOTE_CHARS = 16_000
        const val LONG_NOTE_STATS_DEBOUNCE_MS = 900L
    }

    /** Latest persisted note (metadata such as pin, status, goal, background). */
    val note: StateFlow<Note?> = noteId
        .flatMapLatest { id -> if (id == 0L) flowOf(null) else repository.observeNote(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<Tag>> = noteId
        .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else repository.observeTagsForNote(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val templates: StateFlow<List<Template>> = repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Version history for the open note (newest first). */
    val versions: StateFlow<List<NoteVersion>> = noteId
        .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else repository.observeVersions(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Per-session unlock for a locked note (reset when the editor is recreated). */
    private val _noteUnlocked = MutableStateFlow(false)
    val noteUnlocked: StateFlow<Boolean> = _noteUnlocked
    fun unlockNote() { _noteUnlocked.value = true }

    /** Statistics are seeded from the database, then refreshed off the main thread. */
    private val _stats = MutableStateFlow(TextStats.EMPTY)
    val stats: StateFlow<TextStats> = _stats
    private val _statsReady = MutableStateFlow(false)
    val statsReady: StateFlow<Boolean> = _statsReady

    /** Stats for the current selection only (null when collapsed). */
    val selectionStats: StateFlow<TextStats?> = snapshotFlow { bodyState.selection to bodyState.text }
        .debounce(200)
        .mapLatest { (sel, text) ->
            if (sel.collapsed) null
            else counter.count(text.subSequence(sel.min, sel.max.coerceAtMost(text.length)).toString(), method)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _saved = MutableStateFlow(0)
    /** Increments each time the note is written to the database (drives the "Saved" fade). */
    val savedTick: StateFlow<Int> = _saved

    private val _goalReachedTick = MutableStateFlow(0)
    val goalReachedTick: StateFlow<Int> = _goalReachedTick
    private var goalWasReached = false

    private val _find = MutableStateFlow(FindState())
    val find: StateFlow<FindState> = _find

    /** Incremented whenever the editor should scroll so the current selection (e.g. a find match) is visible. */
    private val _revealSelection = MutableStateFlow(0)
    val revealSelection: StateFlow<Int> = _revealSelection

    /** True once the note was discarded (empty on exit); blocks any late auto-save from resurrecting it. */
    private var discarded = false

    private val _message = MutableStateFlow<Int?>(null)
    /** One-shot string resource id for snackbars. */
    val message: StateFlow<Int?> = _message
    fun consumeMessage() { _message.value = null }

    init {
        viewModelScope.launch {
            prefs.settings.collect {
                method = it.wordCountMethod
                autoSaveDelay = it.autoSaveDelayMs.toLong().coerceIn(300L, 10_000L)
            }
        }
        // Recount only after real edits. The persisted count is used immediately on load so a
        // long ICU dictionary pass never competes with the first keyboard animation.
        viewModelScope.launch {
            snapshotFlow { bodyState.text }
                .drop(1)
                .debounce { text -> if (text.length >= LONG_NOTE_CHARS) LONG_NOTE_STATS_DEBOUNCE_MS else 300L }
                .collectLatest { text ->
                    val seeded = statsSeedBody
                    statsSeedBody = null
                    if (seeded != null && text.contentEquals(seeded)) return@collectLatest
                    statsRefreshJob?.cancel()
                    val value = text.toString()
                    _stats.value = withContext(Dispatchers.Default) { counter.count(value, method) }
                    _statsReady.value = true
                }
        }
        // Auto-save
        viewModelScope.launch {
            // Observe the CharSequence identities only. Calling toString() here copied the whole
            // note twice on every keystroke, which is what made typing in long notes stutter;
            // save() already compares against the stored content before writing.
            snapshotFlow { titleState.text to bodyState.text }
                .drop(1)
                .distinctUntilChanged { a, b ->
                    // contentEquals walks the chars without allocating a copy.
                    a.first.contentEquals(b.first) && a.second.contentEquals(b.second)
                }
                .map { dirty = true; it }
                .debounce { autoSaveDelay }
                .collect { save() }
        }
        // Goal celebration
        viewModelScope.launch {
            combine(stats, note.filterNotNull()) { s, n -> n.wordGoal?.let { g -> g > 0 && s.words >= g } ?: false }
                .collect { reached ->
                    if (reached && !goalWasReached && loadedNote.value != null) _goalReachedTick.value++
                    goalWasReached = reached
                }
        }
        // Re-run find on text changes while the bar is open
        viewModelScope.launch {
            snapshotFlow { bodyState.text }
                .debounce(150)
                .collect { if (_find.value.visible) runFind(keepIndex = true) }
        }
    }

    fun load(id: Long) {
        if (noteId.value == id) return
        loadJob?.cancel()
        statsRefreshJob?.cancel()
        _statsReady.value = false
        noteId.value = id
        loadJob = viewModelScope.launch {
            val n = repository.getNote(id) ?: return@launch
            loadedNote.value = n
            _stats.value = seedStats(n)
            _statsReady.value = true
            statsSeedBody = n.body
            // Open at the top of the note. Placing the caret at the end used to scroll a long
            // note straight to its bottom and fight the user's first scroll gesture.
            titleState.setTextKeepingCursor(n.title, 0)
            bodyState.setTextKeepingCursor(n.body, 0)
            bodyState.undoState.clearHistory()
            goalWasReached = n.wordGoal?.let { it > 0 && n.wordCount >= it } ?: false
            dirty = false
            _noteUnlocked.value = !n.isLocked
            // Small notes can fill detailed stats immediately. Long notes keep the persisted
            // summary until the user edits or opens Details, so no CPU-heavy ICU pass races the IME.
            if (n.body.length < LONG_NOTE_CHARS) {
                statsRefreshJob = viewModelScope.launch {
                    if (loadedNote.value?.id == n.id && !dirty && bodyState.text.contentEquals(n.body)) {
                        _stats.value = withContext(Dispatchers.Default) { counter.count(n.body, method) }
                    }
                }
            }
            // Pick up content changed elsewhere (e.g. "Restore" from Version history) while this
            // editor is open and has no unsaved typing of its own.
            repository.observeNote(id).collect { fresh ->
                val base = loadedNote.value ?: return@collect
                if (fresh == null || dirty || fresh.updatedAt <= base.updatedAt) return@collect
                if (fresh.body == bodyState.text.toString() && fresh.title == titleState.text.toString()) {
                    loadedNote.value = fresh
                    return@collect
                }
                loadedNote.value = fresh
                _stats.value = seedStats(fresh)
                _statsReady.value = true
                statsSeedBody = fresh.body
                // Keep the caret where the user left it when content changes underneath us.
                titleState.setTextKeepingCursor(fresh.title, titleState.selection.start)
                bodyState.setTextKeepingCursor(fresh.body, bodyState.selection.start)
                dirty = false
            }
        }
    }

    suspend fun save(force: Boolean = false) {
        val base = loadedNote.value ?: return
        if (discarded) return
        if (!dirty && !force) return
        saveMutex.withLock {
            val title = titleState.text.toString()
            val body = bodyState.text.toString()
            val current = repository.getNote(base.id) ?: base
            if (current.title == title && current.body == body) { dirty = false; return }
            val saved = repository.saveContent(current, title, body, method)
            loadedNote.value = saved
            dirty = false
            _saved.value++
            // Daily writing stats + automatic version snapshot (repository decides whether one is due).
            repository.afterSave(current, saved)
        }
    }

    /** Fire-and-forget save used from onPause / back. */
    fun saveNow() { viewModelScope.launch { save() } }

    /** True when the note has no title and no body text (whitespace only counts as empty). */
    fun isEmptyNote(): Boolean = titleState.text.isBlank() && bodyState.text.isBlank()

    /**
     * Called when the user leaves the editor. An empty note is deleted outright so that
     * no "Untitled" leftovers accumulate; otherwise the note is saved.
     */
    fun finishEditing() {
        val base = loadedNote.value ?: return
        if (isEmptyNote()) {
            discarded = true
            dirty = false
            // Own scope: viewModelScope may be cancelled as the screen leaves the back stack.
            GlobalScope.launch(Dispatchers.IO) { repository.deleteForever(listOf(base.id)) }
        } else {
            saveNow()
        }
    }

    // ---------------------------------------------------------------- text editing helpers

    private fun currentSelection(): TextSelection {
        val s = bodyState.selection
        return TextSelection(s.start, s.end)
    }

    /** Applies an EditResult with a minimal replace so undo history stays small. */
    private fun applyEdit(result: EditResult) {
        val old = bodyState.text.toString()
        val new = result.text
        var prefix = 0
        val maxPrefix = minOf(old.length, new.length)
        while (prefix < maxPrefix && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < minOf(old.length, new.length) - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        bodyState.edit {
            replace(prefix, old.length - suffix, new.substring(prefix, new.length - suffix))
            selection = TextRange(result.selection.start.coerceIn(0, length), result.selection.end.coerceIn(0, length))
        }
    }

    fun toggleInline(marker: String, endMarker: String = marker) =
        applyEdit(MarkdownToggle.wrap(bodyState.text.toString(), currentSelection(), marker, endMarker))

    fun toggleBold() = toggleInline("**")
    fun toggleItalic() = toggleInline("*")
    fun toggleUnderline() = toggleInline("++")
    fun toggleStrikethrough() = toggleInline("~~")
    fun toggleHighlight() = toggleInline("==")
    fun toggleInlineCode() = toggleInline("`")

    fun toggleHeading(level: Int) =
        applyEdit(MarkdownToggle.linePrefix(bodyState.text.toString(), currentSelection(), "#".repeat(level.coerceIn(1, 3)) + " ", PrefixKind.HEADING))
    fun toggleQuote() = applyEdit(MarkdownToggle.linePrefix(bodyState.text.toString(), currentSelection(), "> ", PrefixKind.QUOTE))
    fun toggleBulletList() = applyEdit(MarkdownToggle.linePrefix(bodyState.text.toString(), currentSelection(), "- ", PrefixKind.LIST))
    fun toggleNumberedList() = applyEdit(MarkdownToggle.linePrefix(bodyState.text.toString(), currentSelection(), "1. ", PrefixKind.LIST))
    fun toggleChecklist() = applyEdit(MarkdownToggle.linePrefix(bodyState.text.toString(), currentSelection(), "- [ ] ", PrefixKind.LIST))
    fun insertLink(label: String, url: String) = applyEdit(MarkdownToggle.insertLink(bodyState.text.toString(), currentSelection(), label, url))
    fun insertHorizontalRule() = applyEdit(MarkdownToggle.insertHorizontalRule(bodyState.text.toString(), currentSelection()))

    fun selectedText(): String {
        val s = bodyState.selection
        return if (s.collapsed) "" else bodyState.text.substring(s.min, s.max)
    }

    val canUndo: Boolean get() = bodyState.undoState.canUndo
    val canRedo: Boolean get() = bodyState.undoState.canRedo
    fun undo() { if (canUndo) bodyState.undoState.undo() }
    fun redo() { if (canRedo) bodyState.undoState.redo() }

    // ---------------------------------------------------------------- find & replace

    fun openFind(withReplace: Boolean = false) {
        val seed = selectedText().takeIf { it.isNotEmpty() && !it.contains('\n') }
        _find.value = _find.value.copy(visible = true, showReplace = withReplace || _find.value.showReplace, query = seed ?: _find.value.query)
        runFind(keepIndex = false)
    }
    fun closeFind() { _find.value = _find.value.copy(visible = false, matches = emptyList(), current = -1, error = null) }
    fun setFindQuery(q: String) { _find.value = _find.value.copy(query = q); runFind(keepIndex = false) }
    fun setReplacement(r: String) { _find.value = _find.value.copy(replacement = r) }
    fun toggleShowReplace() { _find.value = _find.value.copy(showReplace = !_find.value.showReplace) }
    fun setFindOptions(options: FindOptions) { _find.value = _find.value.copy(options = options); runFind(keepIndex = false) }

    private fun runFind(keepIndex: Boolean) {
        val f = _find.value
        if (f.query.isEmpty()) { _find.value = f.copy(matches = emptyList(), current = -1, error = null); return }
        when (val r = FindReplace.find(bodyState.text, f.query, f.options)) {
            is FindResult.Error -> _find.value = f.copy(matches = emptyList(), current = -1, error = r.message)
            FindResult.Empty -> _find.value = f.copy(matches = emptyList(), current = -1, error = null)
            is FindResult.Matches -> {
                val cursor = bodyState.selection.min
                val idx = if (keepIndex && f.current in r.matches.indices) f.current
                else r.matches.indexOfFirst { it.start >= cursor }.let { if (it < 0) 0 else it }
                _find.value = f.copy(matches = r.matches, current = idx, error = null)
                if (!keepIndex) selectMatch(idx)
            }
        }
    }

    private fun selectMatch(index: Int) {
        val m = _find.value.matches.getOrNull(index) ?: return
        bodyState.edit { selection = TextRange(m.start.coerceIn(0, length), m.end.coerceIn(0, length)) }
        _revealSelection.value++
    }

    fun nextMatch() {
        val f = _find.value
        if (f.matches.isEmpty()) return
        val i = (f.current + 1) % f.matches.size
        _find.value = f.copy(current = i); selectMatch(i)
    }
    fun prevMatch() {
        val f = _find.value
        if (f.matches.isEmpty()) return
        val i = if (f.current <= 0) f.matches.lastIndex else f.current - 1
        _find.value = f.copy(current = i); selectMatch(i)
    }

    fun replaceCurrent() {
        val f = _find.value
        val m = f.matches.getOrNull(f.current) ?: return
        FindReplace.replaceOne(bodyState.text.toString(), m, f.query, f.replacement, f.options)
            .onSuccess { new ->
                applyEdit(EditResult(new, TextSelection(m.start + (new.length - bodyState.text.length) + (m.end - m.start))))
                runFind(keepIndex = false)
            }
            .onFailure { _find.value = f.copy(error = it.message) }
    }

    fun replaceAll() {
        val f = _find.value
        if (f.matches.isEmpty()) return
        FindReplace.replaceAll(bodyState.text.toString(), f.query, f.replacement, f.options)
            .onSuccess { (new, _) ->
                val cursor = bodyState.selection.start.coerceIn(0, new.length)
                applyEdit(EditResult(new, TextSelection(cursor)))
                runFind(keepIndex = false)
            }
            .onFailure { _find.value = f.copy(error = it.message) }
    }

    // ---------------------------------------------------------------- metadata

    private fun updateMeta(block: (Note) -> Note) = viewModelScope.launch {
        val n = repository.getNote(noteId.value) ?: return@launch
        val updated = block(n)
        repository.updateMeta(updated)
        loadedNote.value = loadedNote.value?.let { block(it) }
    }

    fun togglePin() = updateMeta { it.copy(isPinned = !it.isPinned) }
    fun toggleLock() = updateMeta { it.copy(isLocked = !it.isLocked) }

    /** Sets (or clears, with null) the reminder and (re)schedules the notification. */
    fun setReminder(at: Long?) {
        val id = noteId.value
        if (id == 0L) return
        viewModelScope.launch {
            repository.setReminder(id, at)
            loadedNote.value = loadedNote.value?.copy(reminderAt = at)
            if (at == null) reminders.cancel(id) else reminders.schedule(id, at)
            _message.value = if (at == null) com.alal.notes.R.string.reminder_removed else com.alal.notes.R.string.reminder_set
        }
    }

    /** Restores an old snapshot into the editor. The current text is snapshotted first so nothing is lost. */
    fun restoreVersion(v: NoteVersion) {
        val id = noteId.value
        if (id == 0L) return
        viewModelScope.launch {
            save()
            repository.snapshotNow(id)
            titleState.setTextKeepingCursor(v.title, 0)
            bodyState.setTextKeepingCursor(v.body, 0)
            dirty = true
            save(force = true)
            _message.value = com.alal.notes.R.string.version_restored
        }
    }
    fun toggleFavorite() = updateMeta { it.copy(isFavorite = !it.isFavorite) }
    fun setStatus(status: NoteStatus) = updateMeta { it.copy(status = status) }
    fun setCategory(id: Long?) = updateMeta { it.copy(categoryId = id) }
    fun setWordGoal(goal: Int?) = updateMeta { it.copy(wordGoal = goal?.takeIf { g -> g > 0 }) }
    fun setBackground(color: Int?, gradient: Int?) = updateMeta { it.copy(backgroundColor = color, backgroundGradient = gradient) }
    fun setPaperTexture(texture: PaperTexture?) = updateMeta { it.copy(paperTexture = texture) }
    fun setShowOnCard(show: Boolean) = updateMeta { it.copy(showBackgroundOnCard = show) }
    fun setFontSizeOverride(size: Int?) = updateMeta { it.copy(fontSizeOverride = size?.coerceIn(14, 30)) }

    fun archive() = viewModelScope.launch { save(); repository.setArchived(listOf(noteId.value), true) }
    fun trash() = viewModelScope.launch { save(); repository.trash(listOf(noteId.value)) }
    suspend fun duplicate(): Long? { save(force = true); return repository.duplicate(noteId.value) }

    fun applyTemplate(template: Template) {
        val body = bodyState.text.toString()
        val sep = if (body.isBlank()) "" else if (body.endsWith("\n\n")) "" else if (body.endsWith("\n")) "\n" else "\n\n"
        val insert = sep + template.body
        bodyState.edit {
            replace(length, length, insert)
            placeCursorAtEnd()
        }
    }

    fun addTag(name: String) = viewModelScope.launch { if (name.isNotBlank()) repository.addTagToNote(noteId.value, name.trim()) }
    fun removeTag(tagId: Long) = viewModelScope.launch { repository.removeTagFromNote(noteId.value, tagId) }

    suspend fun shareText(): String {
        save()
        val title = titleState.text.toString().ifBlank { com.alal.notes.domain.markdown.AutoTitle.from("", bodyState.text.toString()) }
        return if (title.isBlank()) bodyState.text.toString() else "$title\n\n${bodyState.text}"
    }

    // ---------------------------------------------------------------- Phase 2.1: export / outline

    /** The saved note with the *current* editor text, for exporting without waiting for auto-save. */
    private suspend fun snapshotForExport(): Note? {
        save()
        val n = loadedNote.value ?: return null
        return n.copy(title = titleState.text.toString(), body = bodyState.text.toString())
    }

    fun exportFileName(format: ExportFormat): String {
        val n = loadedNote.value ?: return "alal-note.${format.extension}"
        return exporter.fileName(n.copy(title = titleState.text.toString(), body = bodyState.text.toString()), format)
    }

    /** Writes the export into a document picked with ACTION_CREATE_DOCUMENT. */
    fun exportTo(uri: android.net.Uri, format: ExportFormat) = viewModelScope.launch {
        val n = snapshotForExport() ?: return@launch
        _message.value = try {
            exporter.writeTo(uri, n, format); com.alal.notes.R.string.export_saved
        } catch (e: Exception) {
            com.alal.notes.R.string.export_failed
        }
    }

    /** Returns a shareable content URI + mime type, or null on failure (a message is posted). */
    suspend fun exportForShare(format: ExportFormat): Pair<android.net.Uri, String>? {
        val n = snapshotForExport() ?: return null
        return try {
            exporter.writeForShare(n, format) to format.mime
        } catch (e: Exception) {
            _message.value = com.alal.notes.R.string.export_failed
            null
        }
    }

    fun outline(): List<OutlineItem> = Outline.extract(bodyState.text.toString())

    /** Moves the caret to [offset] and asks the editor to scroll it into view. */
    fun jumpTo(offset: Int) {
        val o = offset.coerceIn(0, bodyState.text.length)
        bodyState.edit { selection = TextRange(o, o) }
        _revealSelection.value++
    }

    suspend fun currentMethod(): WordCountMethod = prefs.settings.first().wordCountMethod

    /** Computes the full breakdown only when a detail view actually needs it. */
    fun refreshDetailedStats() {
        statsRefreshJob?.cancel()
        val value = bodyState.text.toString()
        statsRefreshJob = viewModelScope.launch {
            _stats.value = withContext(Dispatchers.Default) { counter.count(value, method) }
            _statsReady.value = true
        }
    }

    private fun seedStats(note: Note): TextStats {
        val words = note.wordCount.coerceAtLeast(0)
        val chars = note.charCount.coerceAtLeast(0)
        var charsNoSpaces = 0
        var hasMyanmar = false
        for (c in note.body) {
            if (!c.isWhitespace()) charsNoSpaces++
            if (!hasMyanmar && isMyanmarChar(c)) hasMyanmar = true
        }
        val myanmarWords = if (hasMyanmar) words else 0
        val latinWords = words - myanmarWords
        val readMinutes = if (words == 0) 0 else {
            val speed = if (hasMyanmar) 150 else 200
            ((words + speed - 1) / speed).coerceAtLeast(1)
        }
        return TextStats(
            words = words,
            myanmarWords = myanmarWords,
            latinWords = latinWords,
            chars = chars,
            charsNoSpaces = charsNoSpaces,
            readMinutes = readMinutes,
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Best-effort final save on a fresh scope; viewModelScope is being cancelled.
        val base = loadedNote.value ?: return
        if (discarded) return
        if (isEmptyNote()) {
            GlobalScope.launch(Dispatchers.IO) { repository.deleteForever(listOf(base.id)) }
            return
        }
        if (!dirty) return
        val title = titleState.text.toString()
        val body = bodyState.text.toString()
        GlobalScope.launch(Dispatchers.IO) { repository.saveContent(base, title, body, method) }
    }
}

/**
 * Replaces the whole content but puts the caret at [cursor] (clamped) instead of at the end,
 * so restoring or reloading a note never yanks the view to the bottom.
 */
private fun TextFieldState.setTextKeepingCursor(value: String, cursor: Int) {
    edit {
        replace(0, length, value)
        val at = cursor.coerceIn(0, length)
        selection = TextRange(at)
    }
}
