from pathlib import Path

path = Path("app/src/main/java/com/alal/notes/ui/AlalRoot.kt")
s = path.read_text()
old = '''/** One surface transition, started only after the selected note is ready. */
private const val SLIDE_MS = 240

private fun openSlideSpec() = tween<IntOffset>(SLIDE_MS, easing = FastOutSlowInEasing)
'''
new = '''/**
 * Keep Home completely still while the already-composed editor gets its first text layout.
 * The editor is off-screen during this hold, so its empty/default frame can never flash.
 */
private const val OPEN_RENDER_HOLD_MS = 450
private const val SLIDE_MS = 240

private fun openSlideSpec() = tween<IntOffset>(
    durationMillis = SLIDE_MS,
    delayMillis = OPEN_RENDER_HOLD_MS,
    easing = FastOutSlowInEasing,
)
'''
assert old in s, "open-slide anchor missing"
s = s.replace(old, new, 1)
s = s.replace(
'''        // The editor begins only after its note is in the LRU. Do not cross-fade an empty first
        // frame over Home: one horizontal surface transition is visually stable.
''',
'''        // Navigation composes the editor off-screen immediately, then holds Home for 450 ms.
        // Room/cache delivery, TextFieldState setup and the first long-text layout finish during
        // that hidden window; only then does the single horizontal slide become visible.
''',
1,
)
path.write_text(s)
print("Added a 450 ms hidden editor render hold before the visible slide")
