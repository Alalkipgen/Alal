package com.alal.notes.baselineprofile

import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream

/** Application id of the app under test, passed in by the Gradle config as `targetAppId`. */
val targetAppId: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId instrumentation argument is missing")

// English UI strings: managed devices run in en-US and the app follows the system language.
private const val SEARCH_NOTES = "Search notes" // Home search pill
private const val NEW_NOTE = "New note" // Home FAB label, shown while the list is at the top
private const val BLANK_TEMPLATE = "Blank" // Template picker row
private const val TITLE_HINT = "Title" // Editor title placeholder, shown once the note has loaded
private const val EDIT_TEXT = "android.widget.EditText"

private const val TIMEOUT_MS = 10_000L
private const val TAG = "AlalJourney"

/** Waits until Home is on screen. */
fun MacrobenchmarkScope.waitForHome() {
    waitFor(By.text(SEARCH_NOTES), "Home search field")
}

/**
 * A fresh device has no notes, so there is nothing to scroll. Adds short notes until the Home
 * grid scrolls; later runs on the same device reuse them.
 */
fun MacrobenchmarkScope.ensureScrollableHome(maxNotes: Int = 20) {
    var created = 0
    while (homeGrid() == null && created < maxNotes) {
        created++
        openBlankNote()
        val title = "Market notes $created"
        var saved = false
        repeat(2) {
            if (!saved) {
                val (titleField, bodyField) = editorFields()
                titleField.text = title
                bodyField.text = SEED_BODY
                saved = device.wait(Until.hasObject(By.text(title)), 2_000) == true
            }
        }
        backToHome()
        device.waitForIdle()
    }
    homeGrid() ?: fail("scrollable Home grid after adding $created notes")
}

/** Flings the Home note grid down and back to the top (which also re-extends the FAB). */
fun MacrobenchmarkScope.scrollHome() {
    repeat(2) {
        val grid = homeGrid() ?: return
        grid.setGestureMarginPercentage(0.2f)
        grid.fling(Direction.DOWN)
        device.waitForIdle()
    }
    var canScrollUp = true
    var flings = 0
    while (canScrollUp && flings++ < 5) {
        val grid = homeGrid() ?: return
        grid.setGestureMarginPercentage(0.2f)
        canScrollUp = grid.fling(Direction.UP)
    }
    device.waitForIdle()
}

/** Creates a blank note and types [SAMPLE_PARAGRAPHS] (about 2,000 characters) into its body. */
fun MacrobenchmarkScope.createNoteAndType() {
    openBlankNote()
    editorFields().second.click()
    if (device.wait(Until.hasObject(By.clazz(EDIT_TEXT).focused(true)), TIMEOUT_MS) != true) {
        fail("focused note body")
    }
    SAMPLE_PARAGRAPHS.forEachIndexed { index, paragraph ->
        if (index > 0) device.pressEnter()
        // `input text` injects one key event per character; spaces are encoded as %s.
        paragraph.chunkedAtSpaces(INPUT_CHUNK).forEach { chunk ->
            device.executeShellCommand("input text ${chunk.replace(" ", "%s")}")
        }
    }
    device.waitForIdle()
}

/** Leaves the editor with the system Back key; the first press may only hide the keyboard. */
fun MacrobenchmarkScope.backToHome() {
    repeat(3) {
        device.pressBack()
        if (device.wait(Until.hasObject(By.text(SEARCH_NOTES)), 3_000) == true) return
    }
    fail("Home after pressing Back")
}

/** Opens Search from the Home search pill and waits for its focused query field. */
fun MacrobenchmarkScope.openSearch() {
    waitFor(By.text(SEARCH_NOTES), "Home search field").click()
    if (device.wait(Until.hasObject(By.clazz(EDIT_TEXT).focused(true)), TIMEOUT_MS) != true) {
        fail("focused search field")
    }
    device.waitForIdle()
}

/** Opens a new note from the Blank template and waits until the editor has loaded it. */
private fun MacrobenchmarkScope.openBlankNote() {
    waitFor(By.text(NEW_NOTE), "New note button").click()
    waitFor(By.text(BLANK_TEMPLATE), "Blank template").click()
    waitFor(By.clazz(EDIT_TEXT), "note editor")
    // Text entered before the note loads is replaced by the stored (empty) note; the title
    // placeholder appears once it has loaded.
    device.wait(Until.hasObject(By.text(TITLE_HINT)), 3_000)
}

/** Title and body fields of the open editor, top to bottom. */
private fun MacrobenchmarkScope.editorFields(): Pair<UiObject2, UiObject2> {
    val fields = device.findObjects(By.clazz(EDIT_TEXT)).sortedBy { it.visibleBounds.top }
    if (fields.size < 2) fail("note title and body fields")
    return fields[0] to fields[1]
}

/** The note grid, once it holds enough notes to scroll (the category chips row is shorter). */
private fun MacrobenchmarkScope.homeGrid(): UiObject2? =
    device.findObjects(By.scrollable(true))
        .filter { it.visibleBounds.height() > device.displayHeight / 3 }
        .maxByOrNull { it.visibleBounds.height() }

private fun MacrobenchmarkScope.waitFor(selector: BySelector, what: String): UiObject2 =
    device.wait(Until.findObject(selector), TIMEOUT_MS) ?: fail(what)

private fun MacrobenchmarkScope.fail(what: String): Nothing {
    val hierarchy = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString()
    hierarchy.chunked(3_000).forEach { Log.e(TAG, it) }
    throw AssertionError("$what not found; window hierarchy logged with tag $TAG")
}

private fun String.chunkedAtSpaces(maxLength: Int): List<String> {
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    for (word in split(' ')) {
        if (current.isNotEmpty() && current.length + word.length + 1 > maxLength) {
            chunks += current.append(' ').toString()
            current.clear()
        } else if (current.isNotEmpty()) {
            current.append(' ')
        }
        current.append(word)
    }
    if (current.isNotEmpty()) chunks += current.toString()
    return chunks
}

private const val INPUT_CHUNK = 200

private const val SEED_BODY =
    "Stalls open before six. Ask about the ferry, the rent and the rainy season. " +
        "Photos of the lane, then the tea stall, then the jetty."

/** Plain prose only: letters, digits, spaces, commas and full stops type the same on every keymap. */
private val SAMPLE_PARAGRAPHS = listOf(
    "Morning notes from the riverside market. The vendors arrive before six and set up their stalls " +
        "under blue tarps. Fish, greens and cut flowers share the same narrow lane, and every trader seems " +
        "to know every customer by name. I want this piece to capture that rhythm before the city wakes up.",
    "Interview plan for Thursday. Start with how long she has worked at the market and what changed " +
        "after the new bridge opened. Ask about prices, rent and the early ferry. Keep the questions short, " +
        "listen more than I talk, and write down exact words when she describes the river in the rainy season.",
    "Structure for the draft. Open with the sound of the first boat engines, then move to the tea stall " +
        "where the porters wait for work. The middle section follows one basket of fish from the jetty to a " +
        "kitchen across town. End back at the river in the evening, when the lane is quiet and the tarps come down.",
    "Things to check before filing. Confirm the spelling of every name, the opening hours and the ferry " +
        "timetable. Find two older photos of the lane for comparison. Read the draft aloud once, cut anything " +
        "that sounds like a brochure, and keep the paragraphs short enough to read on a phone.",
    "Notes on tone. Stay close to the people and their work, not to the scenery. Use plain words, concrete " +
        "numbers and small details, such as the price of a cup of tea or the time the ice truck arrives. If a " +
        "sentence needs an adjective to work, it probably needs a better verb instead.",
    "Open questions. How many stalls closed during the flood two years ago, and how many came back. Who " +
        "owns the land under the lane now. Whether the night market on the other bank takes customers away or " +
        "brings new ones. Ask the editor about length, photos and the deadline on Friday.",
    "Last line ideas. The river keeps its own hours. Everyone here is up before the city, and gone before " +
        "it notices. The lane empties, the tarps fold, and the fish smell stays until the rain. Check the tide " +
        "table before the next visit, because the jetty floods at high water.",
)
