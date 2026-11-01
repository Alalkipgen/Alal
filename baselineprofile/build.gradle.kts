import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Generates the Baseline Profile for :app and benchmarks cold start.
//
//   Generate (Pixel 6 API 34 managed device):  ./gradlew :app:generateBaselineProfile
//   Benchmark cold start:                      ./gradlew :baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest \
//       -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.alal.notes.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Profile collection needs API 28+ (rooted) or API 33+.
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Managed devices are emulators. Allow Macrobenchmark to run there; results are reported
        // with an EMULATOR_ prefix and are only meaningful relative to each other.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    testOptions.managedDevices.localDevices {
        create("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    onVariants { variant ->
        // Tell the tests which application id they drive (release and benchmark variants share it).
        val artifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { artifactsLoader.load(it)?.applicationId },
        )
    }
}
