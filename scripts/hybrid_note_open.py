#!/usr/bin/env python3
from pathlib import Path

root = Path("app/src/main/java/com/alal/notes")

# Repository: a small, bounded LRU of complete notes. Home warms recent/visible notes; a tap
# fetches only on a miss. EditorViewModel's normal getNote call then resolves from memory.
p = root / "data/repository/NoteRepository.kt"
s = p.read_text()
s = s.replace("import javax.inject.Singleton\nimport kotlin.math.abs\n", "import javax.inject.Singleton\nimport kotlin.collections.LinkedHashMap\nimport kotlin.math.abs\n", 1)
s = s.replace(
"    // ---- observe ----\n",
"""    // A bounded open cache: enough for the visible/recent cards, never every note body.
    // Access order makes this an LRU, and the lock keeps Home warm-up and editor reads safe.
    private val openCacheLock = Any()
    private val openCache = object : LinkedHashMap<Long, Note>(OPEN_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Note>?): Boolean =
            size > OPEN_CACHE_SIZE
    }

    fun warmOpenCache(notes: List<Note>) {
        synchronized(openCacheLock) {
            notes.take(OPEN_CACHE_SIZE).forEach { openCache[it.id] = it }
        }
    }

    fun peekOpenCache(id: Long): Note? = synchronized(openCacheLock) { openCache[id] }

    private fun cacheOpenNote(note: Note): Note {
        synchronized(openCacheLock) { openCache[note.id] = note }
        return note
    }

    /** Fetches only on an LRU miss; callers navigate after this returns. */
    suspend fun prepareForOpen(id: Long): Note? {
        peekOpenCache(id)?.let { return it }
        return withContext(Dispatchers.IO) { noteDao.getById(id) }?.let(::cacheOpenNote)
    }

    // ---- observe ----
""", 1)
s = s.replace("    suspend fun getNote(id: Long): Note? = noteDao.getById(id)\n", "    suspend fun getNote(id: Long): Note? = prepareForOpen(id)\n", 1)
s = s.replace(
"""        noteDao.update(updated)
        updated
    }
""",
"""        noteDao.update(updated)
        cacheOpenNote(updated)
    }
""", 1)
s = s.replace(
"        const val MAX_VERSIONS_PER_NOTE = 50\n",
"        private const val OPEN_CACHE_SIZE = 16\n        const val MAX_VERSIONS_PER_NOTE = 50\n", 1)
p.write_text(s)

# Home already receives complete Note rows for its cards. Retain only the first 16 (current sort
# order from Room) so most taps are warm without loading every body into a second copy.
p = root / "ui/home/HomeViewModel.kt"
s = p.read_text()
s = s.replace("import kotlinx.coroutines.flow.first\n", "import kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.flow.onEach\n", 1)
s = s.replace("        repository.observeActive(),\n", "        repository.observeActive().onEach(repository::warmOpenCache),\n", 1)
p.write_text(s)

# Main coordinator: no 120 ms escape hatch. Either the selected note is ready or navigation does
# not start, which prevents a cold/long note from entering as an empty frame.
p = root / "ui/MainViewModel.kt"
s = p.read_text()
old = '''    /**
     * Reads a note before its editor is shown. Navigating only after this returns means the
     * editor's first frame already carries the text, instead of drawing an empty page and
     * filling it a frame or two later (the "flash" when opening a note).
     */
    suspend fun prefetch(noteId: Long) {
        repository.getNote(noteId)
    }
'''
new = '''    /** Warms the selected note and reports whether navigation may safely start. */
    suspend fun prepareNote(noteId: Long): Boolean = repository.prepareForOpen(noteId) != null
'''
assert old in s
p.write_text(s.replace(old, new, 1))

# Navigation: prepare data completely, then perform one slide with no incoming fade.
p = root / "ui/AlalRoot.kt"
s = p.read_text()
s = s.replace("import kotlinx.coroutines.withTimeoutOrNull\n", "", 1)
s = s.replace(
'''/**
 * Opening a note starts moving immediately. The old 400 ms "preparation" hold (added so the
 * editor's first empty frame never slid in before Room delivered the text) made every open feel
 * like a ~600 ms lag. The editor now fades its content in once the note is loaded instead
 * (see EditorScreen), so the surfaces can move right away and the total open time is just the
 * slide itself.
 */
private const val SLIDE_MS = 240

/**
 * Longest the UI waits for the tapped note to be read before it starts the slide anyway. It sits
 * well under the ~100 ms that still reads as "instant", so a cold database can never turn the
 * open into a visible stall.
 */
private const val OPEN_PREFETCH_CAP_MS = 120L
''',
'''/** One surface transition, started only after the selected note is ready. */
private const val SLIDE_MS = 240
''', 1)
s = s.replace(
'''            action == MainActivity.ACTION_NEW_NOTE -> {
                val id = mainVm.createNote()
                navController.navigate(Route.Editor(id))
                onActionConsumed()
            }
''',
'''            action == MainActivity.ACTION_NEW_NOTE -> {
                val id = mainVm.createNote()
                if (mainVm.prepareNote(id)) navController.navigate(Route.Editor(id))
                onActionConsumed()
            }
''', 1)
s = s.replace(
'''            action.startsWith(openPrefix) -> {
                action.removePrefix(openPrefix).toLongOrNull()?.let { id -> navController.navigate(Route.Editor(id)) }
                onActionConsumed()
            }
''',
'''            action.startsWith(openPrefix) -> {
                action.removePrefix(openPrefix).toLongOrNull()?.let { id ->
                    if (mainVm.prepareNote(id)) navController.navigate(Route.Editor(id))
                }
                onActionConsumed()
            }
''', 1)
s = s.replace(
'''    // Read the note first (normally a few milliseconds), then slide. The tapped card stays
    // pressed while that happens, so the wait is covered by touch feedback instead of an empty
    // editor frame that has to be filled in afterwards.
    val openNote: (Long) -> Unit = { id ->
        scope.launch {
            withTimeoutOrNull(OPEN_PREFETCH_CAP_MS) { mainVm.prefetch(id) }
            navController.navigate(Route.Editor(id))
        }
    }
''',
'''    // Hybrid open: recent/visible notes hit the bounded memory cache; a cold note is read on
    // demand. Navigation never outruns that work, while the card's press/ripple covers the wait.
    val openNote: (Long) -> Unit = { id ->
        scope.launch {
            if (mainVm.prepareNote(id)) navController.navigate(Route.Editor(id))
        }
    }
''', 1)
s = s.replace(
'''        // Both complete surfaces move together as soon as the note is tapped; a short fade on the
        // incoming editor softens the first frame. Back navigation mirrors it.
''',
'''        // The editor begins only after its note is in the LRU. Do not cross-fade an empty first
        // frame over Home: one horizontal surface transition is visually stable.
''', 1)
s = s.replace(
"if (targetState.isEditor()) slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, openSlideSpec()) + fadeIn(tween(SLIDE_MS / 2))",
"if (targetState.isEditor()) slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, openSlideSpec())", 1)
s = s.replace(
"TemplatesScreen(onBack = back, onCreated = { id -> navController.navigate(Route.Editor(id)) })",
"TemplatesScreen(onBack = back, onCreated = openNote)", 1)
p.write_text(s)

# Initial stats must not scan a huge body on the main thread. Persisted totals are immediate;
# detailed counting remains an explicit/background operation.
p = root / "ui/editor/EditorViewModel.kt"
s = p.read_text()
old = '''        var charsNoSpaces = 0
        var hasMyanmar = false
        for (c in note.body) {
            if (!c.isWhitespace()) charsNoSpaces++
            if (!hasMyanmar && isMyanmarChar(c)) hasMyanmar = true
        }
'''
new = '''        // Only sample enough text to choose the script-specific reading speed. Walking an
        // entire long note here blocked the main thread during the opening transition.
        val sampleLength = minOf(note.body.length, INITIAL_SCRIPT_SAMPLE_CHARS)
        var sampledNoSpaces = 0
        var hasMyanmar = false
        for (i in 0 until sampleLength) {
            val c = note.body[i]
            if (!c.isWhitespace()) sampledNoSpaces++
            if (!hasMyanmar && isMyanmarChar(c)) hasMyanmar = true
        }
        val charsNoSpaces = if (sampleLength == note.body.length) sampledNoSpaces else chars
'''
assert old in s
s = s.replace(old, new, 1)
s = s.replace("        const val LONG_NOTE_CHARS = 16_000\n", "        const val LONG_NOTE_CHARS = 16_000\n        const val INITIAL_SCRIPT_SAMPLE_CHARS = 2_048\n", 1)
p.write_text(s)

print("Applied bounded warm cache, ready-before-navigation, single transition and bounded initial work")
