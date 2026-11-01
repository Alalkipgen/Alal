package com.alal.notes.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the Baseline Profile for the most common path through the app:
 * cold start, scroll Home, write a long note, go back, open Search.
 *
 * Run `./gradlew :app:generateBaselineProfile`; the result is written to
 * `app/src/release/generated/baselineProfiles/baseline-prof.txt`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = targetAppId) {
        pressHome()
        startActivityAndWait()
        waitForHome()

        ensureScrollableHome()
        scrollHome()

        createNoteAndType()
        backToHome()

        openSearch()
    }
}
