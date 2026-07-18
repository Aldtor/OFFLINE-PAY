// Top-level build file — configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    // Google Services & Firebase — require google-services.json; applied per-module when available
    // alias(libs.plugins.google.services) apply false
    // alias(libs.plugins.firebase.crashlytics) apply false
    // alias(libs.plugins.firebase.perf) apply false
}
