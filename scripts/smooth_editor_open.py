#!/usr/bin/env python3
"""One-time patch: remove the fade / placeholder flash when opening a note."""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCREEN = ROOT / "app/src/main/java/com/alal/notes/ui/editor/EditorScreen.kt"
VM = ROOT / "app/src/main/java/com/alal/notes/ui/editor/EditorViewModel.kt"
ROOT_UI = ROOT / "app/src/main/java/com/alal/notes/ui/AlalRoot.kt"


def patch(path, old, new, name):
    text = path.read_text()
    if new in text:
        print("skip (already applied):", name)
        return
    if text.count(old) != 1:
        print("ERROR: anchor missing or not unique:", name)
        sys.exit(1)
    path.write_text(text.replace(old, new))
    print("patched:", name)


patch(
    VM,
    "    private val _noteUnlocked = MutableStateFlow(false)",
    """    /**
     * True once the open note's text has actually been pushed into the editor fields. The UI
     * uses it to stay unpainted for the one or two frames Room needs, instead of drawing an
     * empty editor and then fading the real note in.
     */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    private val _noteUnlocked = MutableStateFlow(false)""",
    "vm: loaded flow",
)

patch(
    VM,
    """        statsRefreshJob?.cancel()
        _statsReady.value = false
        noteId.value = id""",
    """        statsRefreshJob?.cancel()
        _statsReady.value = false
        _loaded.value = false
        noteId.value = id""",
    "vm: reset loaded",
)

patch(
    VM,
    """            dirty = false
            _noteUnlocked.value = !n.isLocked""",
    """            dirty = false
            _noteUnlocked.value = !n.isLocked
            _loaded.value = true""",
    "vm: set loaded",
)

patch(
    SCREEN,
    "    val noteUnlocked by vm.noteUnlocked.collectAsStateWithLifecycle()\n",
    "    val noteUnlocked by vm.noteUnlocked.collectAsStateWithLifecycle()\n"
    "    val loaded by vm.loaded.collectAsStateWithLifecycle()\n",
    "screen: collect loaded",
)

patch(
    SCREEN,
    """    // The screen slides in immediately; the body fades in as soon as Room has delivered the
    // note. This replaces the old fixed 400 ms hold before the open animation, which made
    // every note open feel laggy even though the load itself took only a few milliseconds.
    val contentAlpha by animateFloatAsState(if (current != null) 1f else 0f, tween(160), label = "content")""",
    """    // The screen slides in immediately and its content is simply not painted until the note
    // is in the text fields. The previous 160 ms cross-fade - drawn on top of empty
    // placeholders and the default "No category" / "Draft" chips - is what read as a flash on
    // every open: the editor appeared blank, then blinked into the real note. Taking the reveal
    // off the animation clock removes the blink without adding any waiting time.
    val contentAlpha = if (loaded) 1f else 0f""",
    "screen: instant reveal",
)

patch(
    SCREEN,
    "                    Column(Modifier.alpha(chromeAlpha)) {",
    "                    Column(Modifier.alpha(chromeAlpha * contentAlpha)) {",
    "screen: hide chrome until loaded",
)

patch(
    SCREEN,
    """    vm: EditorViewModel,
    settings: Settings,
    bodySize: androidx.compose.ui.unit.TextUnit,""",
    """    vm: EditorViewModel,
    settings: Settings,
    loaded: Boolean,
    bodySize: androidx.compose.ui.unit.TextUnit,""",
    "body: loaded parameter",
)

patch(
    SCREEN,
    """            EditorBody(
                vm = vm,
                settings = settings,
                bodySize = bodySize,""",
    """            EditorBody(
                vm = vm,
                settings = settings,
                loaded = loaded,
                bodySize = bodySize,""",
    "body: pass loaded",
)

patch(
    SCREEN,
    "                    if (titleEmpty) {",
    "                    if (loaded && titleEmpty) {",
    "body: gate title placeholder",
)

patch(
    SCREEN,
    "                    if (bodyEmpty) {",
    "                    if (loaded && bodyEmpty) {",
    "body: gate body placeholder",
)

patch(
    ROOT_UI,
    "            if (targetState.isEditor()) slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, openSlideSpec()) + fadeIn(tween(SLIDE_MS / 2))",
    "            if (targetState.isEditor()) slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, openSlideSpec())",
    "nav: drop extra fade on open",
)

print("done")
