package com.alal.notes.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start to Home without ahead-of-time compilation ([CompilationMode.None]) and with the
 * bundled Baseline Profile ([CompilationMode.Partial]). Home holds a scrollable grid of notes,
 * as it does for a regular user.
 *
 * Run `./gradlew :baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest
 * -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartupCompilationNone() = coldStartup(CompilationMode.None())

    @Test
    fun coldStartupCompilationPartial() =
        coldStartup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun coldStartup(compilationMode: CompilationMode) {
        var homeReady = false
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 10,
            setupBlock = {
                // Runs before the process is killed for each cold start; only the first
                // iteration needs to make sure the notes exist.
                if (!homeReady) {
                    startActivityAndWait()
                    waitForHome()
                    ensureScrollableHome()
                    homeReady = true
                }
                pressHome()
            },
        ) {
            startActivityAndWait()
            waitForHome()
        }
    }
}
