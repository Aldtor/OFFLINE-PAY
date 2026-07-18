/**
 * OfflinePay — :app module build configuration
 *
 * Build variants:
 *   debug   — debuggable, no obfuscation, no shrinking
 *   release — R8 full-mode obfuscation, resource shrinking, non-debuggable
 *
 * ABI splits: arm64-v8a, armeabi-v7a, x86_64 (for SQLCipher native libs)
 * AAB bundle: language, density and ABI splits enabled for Play Store distribution
 *
 * Requirements: Req 9.10 (R8/ProGuard obfuscation), Req 16.11 (AAB), Req 16.12 (APK size)
 * Design: Section 18 Decision 6 (feature modules), Section 8.3 (runtime protection)
 */
plugins {
    id("offlinepay.android.app")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.offlinepay.app"

    defaultConfig {
        applicationId = "com.offlinepay.app"
        versionCode = 1
        versionName = "1.0.0"

        // Required for Room schema export — schemas stored in app/schemas/
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }

    // ── Build Types ────────────────────────────────────────────────────────────
    buildTypes {
        /**
         * Debug build:
         * - Debuggable (allows debugger attachment)
         * - No code shrinking or obfuscation
         * - applicationIdSuffix .debug to allow parallel installation with release
         */
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        /**
         * Release build:
         * - Non-debuggable (prevents debugger attachment — Req 9.13)
         * - R8 full-mode code shrinking and obfuscation (Req 9.10)
         * - Resource shrinking removes unused resources
         * - ProGuard rules from proguard-rules.pro
         * - R8 full-mode enabled via android.enableR8.fullMode=true in gradle.properties
         */
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signing config will be configured via CI secrets or keystore file
            // signingConfig = signingConfigs.getByName("release")
        }
    }

    // ── ABI Splits (Task 2.4) ────────────────────────────────────────────────
    // Produces per-ABI APKs for SQLCipher native library size reduction.
    // Each ABI gets its own APK; versionCode is incremented per ABI.
    // Reduces individual device download size vs fat APK.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true  // Also produce universal APK for direct installs
        }
    }

    // ── Android App Bundle (Task 2.5) ────────────────────────────────────────
    // AAB is the required format for Play Store.
    // Language, density, and ABI splits allow Play to deliver only what each device needs.
    bundle {
        language {
            // Enable language splits — each language pack delivered separately
            // Supports future Indian language expansion (Req 19.3, Req 19.4)
            enableSplit = true
        }
        density {
            // Enable density splits — reduces APK size per device screen density
            enableSplit = true
        }
        abi {
            // Enable ABI splits — critical for SQLCipher native lib size reduction
            enableSplit = true
        }
    }

    // ── Lint configuration ────────────────────────────────────────────────────
    lint {
        abortOnError = false          // Don't fail build on lint errors (CI reports them)
        warningsAsErrors = false
        checkReleaseBuilds = true
        htmlReport = true
        xmlReport = true
    }
}

// ── Version code per ABI ──────────────────────────────────────────────────────
// Required when using ABI splits so each variant has a unique versionCode
// for Play Store upload compatibility.
val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86_64" to 3
)

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val name = output.filters.find {
                it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }?.identifier
            val baseVersionCode = abiCodes[name] ?: 0
            output.versionCode.set(baseVersionCode * 1000 + (android.defaultConfig.versionCode ?: 1))
        }
    }
}

dependencies {
    // ── Core modules ──────────────────────────────────────────────────────────
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:security"))
    implementation(project(":core:analytics"))
    implementation(project(":core:connectivity"))
    implementation(project(":core:telephony"))

    // ── Feature modules ───────────────────────────────────────────────────────
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:scanner"))
    implementation(project(":feature:payment"))
    implementation(project(":feature:ussd"))
    implementation(project(":feature:pay123"))
    implementation(project(":feature:history"))
    implementation(project(":feature:merchant"))
    implementation(project(":feature:settings"))

    // ── Hilt ──────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.work)
    kapt(libs.hilt.compiler.androidx)

    // ── WorkManager ───────────────────────────────────────────────────────────
    implementation(libs.workmanager.runtime.ktx)

    // ── SplashScreen ──────────────────────────────────────────────────────────
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.profileinstaller)

    // ── Timber logging ────────────────────────────────────────────────────────
    implementation(libs.timber)

    // ── Navigation ────────────────────────────────────────────────────────────
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // ── Compose ───────────────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // ── Activity & Lifecycle ──────────────────────────────────────────────────
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ── Desugaring (required for Java 8+ APIs on API 26) ──────────────────────
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
