#!/usr/bin/env python3
"""One-time patch: prefetch the note before navigating + press feedback on note cards."""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/alal/notes"

EDITS = []


def edit(path, old, new):
    EDITS.append((path, old, new))


# ---------------------------------------------------------------- MainViewModel
edit(
    "ui/MainViewModel.kt",
    "    /** Creates an empty note (optionally from a template) and returns its id. */",
    """    /**
     * Reads a note before its editor is shown. Navigating only after this returns means the
     * editor's first frame already carries the text, instead of drawing an empty page and
     * filling it a frame or two later (the \"flash\" when opening a note).
     */
    suspend fun prefetch(noteId: Long) {
        repository.getNote(noteId)
    }

    /** Creates an empty note (optionally from a template) and returns its id. */""",
)

# ---------------------------------------------------------------- AlalRoot
edit(
    "ui/AlalRoot.kt",
    "import androidx.compose.runtime.getValue\n",
    "import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.rememberCoroutineScope\n",
)
edit(
    "ui/AlalRoot.kt",
    "import com.alal.notes.ui.versions.VersionsScreen\n",
    "import com.alal.notes.ui.versions.VersionsScreen\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withTimeoutOrNull\n",
)
edit(
    "ui/AlalRoot.kt",
    "private const val SLIDE_MS = 240\n",
    """private const val SLIDE_MS = 240

/**
 * Longest the UI waits for the tapped note to be read before it starts the slide anyway. It sits
 * well under the ~100 ms that still reads as \"instant\", so a cold database can never turn the
 * open into a visible stall.
 */
private const val OPEN_PREFETCH_CAP_MS = 120L
""",
)
edit(
    "ui/AlalRoot.kt",
    "        AlalNavHost(navController, settings)\n",
    "        AlalNavHost(navController, settings, mainVm)\n",
)
edit(
    "ui/AlalRoot.kt",
    """private fun AlalNavHost(navController: NavHostController, settings: Settings) {
    val openNote: (Long) -> Unit = { id -> navController.navigate(Route.Editor(id)) }
""",
    """private fun AlalNavHost(navController: NavHostController, settings: Settings, mainVm: MainViewModel) {
    val scope = rememberCoroutineScope()
    // Read the note first (normally a few milliseconds), then slide. The tapped card stays
    // pressed while that happens, so the wait is covered by touch feedback instead of an empty
    // editor frame that has to be filled in afterwards.
    val openNote: (Long) -> Unit = { id ->
        scope.launch {
            withTimeoutOrNull(OPEN_PREFETCH_CAP_MS) { mainVm.prefetch(id) }
            navController.navigate(Route.Editor(id))
        }
    }
""",
)

# ---------------------------------------------------------------- NoteCard
edit(
    "ui/home/NoteCard.kt",
    "import androidx.compose.foundation.ExperimentalFoundationApi\n",
    "import androidx.compose.foundation.ExperimentalFoundationApi\nimport androidx.compose.foundation.LocalIndication\n",
)
edit(
    "ui/home/NoteCard.kt",
    "import androidx.compose.foundation.combinedClickable\n",
    "import androidx.compose.foundation.combinedClickable\nimport androidx.compose.foundation.interaction.MutableInteractionSource\nimport androidx.compose.foundation.interaction.collectIsPressedAsState\n",
)
edit(
    "ui/home/NoteCard.kt",
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n",
)
edit(
    "ui/home/NoteCard.kt",
    '    val scale by animateFloatAsState(if (selected) 0.97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "scale")\n',
    """    // Water-touch feel: the card dips under the finger, keeps its ripple, and ticks the moment
    // it is pressed, so the tap is answered before the note has even been read.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberHaptics()
    LaunchedEffect(pressed) { if (pressed) haptics.tick() }
    val scale by animateFloatAsState(
        when {
            selected -> 0.97f
            pressed -> 0.98f
            else -> 1f
        },
        spring(stiffness = Spring.StiffnessMedium),
        label = "scale",
    )
""",
)
edit(
    "ui/home/NoteCard.kt",
    "            .combinedClickable(onClick = onClick, onLongClick = onLongClick),\n",
    """            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
""",
)

failed = []
for rel, old, new in EDITS:
    path = SRC / rel
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        failed.append(f"{rel}: expected 1 match, found {count} for: {old[:70]!r}")
        continue
    path.write_text(text.replace(old, new), encoding="utf-8")
    print(f"patched {rel}")

if failed:
    print("FAILED:")
    for f in failed:
        print(" -", f)
    sys.exit(1)
print("all patches applied")
