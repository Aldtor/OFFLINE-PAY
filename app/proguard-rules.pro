##
# OfflinePay — ProGuard / R8 Rules
#
# R8 full-mode is enabled via android.enableR8.fullMode=true in gradle.properties.
# Applied only in release builds (isMinifyEnabled = true in app/build.gradle.kts).
#
# Requirements:
#   Req 9.10 — R8/ProGuard obfuscation for release builds
#   Req 9.11 — No API keys, secrets, or cryptographic keys in BuildConfig
#   Req 9.12 — Signing certificate verification (CertificateVerifier must survive R8)
# Design:
#   Section 8.3 — Runtime Protection Stack
#   Section 18 Decision 1 — SQLCipher keep rules
##

# ── Preserve source file names and line numbers for crash stack traces ─────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Preserve generic signatures (required for Kotlin generics, Gson, etc.) ───
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ============================================================
# KOTLIN
# ============================================================
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Lazy { *; }

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── Kotlin serialization ──────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep all @Serializable classes and their companions
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# ============================================================
# HILT — Dependency Injection
# ============================================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class dagger.** { *; }
-dontwarn dagger.**

# Keep @HiltAndroidApp applications
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# Keep @AndroidEntryPoint classes (Activities, Fragments, Services, etc.)
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Keep @HiltViewModel annotated ViewModels
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# Keep @HiltWorker annotated WorkManager workers
-keep @dagger.hilt.android.HiltWorker class * { *; }
-keepclasseswithmembers @dagger.hilt.android.HiltWorker class * {
    @javax.inject.Inject <init>(...);
}

# Hilt generated components
-keep class **_HiltComponents { *; }
-keep class **_HiltModules { *; }
-keep class *_ComponentTreeDeps { *; }
-keep class *_GeneratedInjector { *; }
-keep class *_MembersInjector { *; }
-keep class *_Factory { *; }

# ============================================================
# ROOM — Database entities and DAOs
# ============================================================
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.TypeConverter class * { *; }
-keep @androidx.room.Embedded class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public abstract **;
}
# Keep Room generated _Impl classes
-keep class **_Impl { *; }
-keep class **_Impl$* { *; }
-dontwarn androidx.room.**

# ============================================================
# SQLCIPHER — Native JNI bindings (Task 2.3, Design Section 18 Decision 1)
# ============================================================
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keepclassmembers class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# ============================================================
# ML KIT — Barcode scanning (primary QR decoder)
# ============================================================
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-dontwarn com.google.mlkit.**

# ============================================================
# FIREBASE
# ============================================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepnames class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Crashlytics — preserve stack traces
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ============================================================
# ANDROIDX — Core, Lifecycle, Navigation, Compose, WorkManager
# ============================================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }

# WorkManager workers — must keep constructors
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Security Crypto (EncryptedSharedPreferences, MasterKey)
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Splashscreen
-keep class androidx.core.splashscreen.** { *; }

# ============================================================
# PARCELABLE
# ============================================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ============================================================
# ENUM CLASSES
# ============================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# SECURITY — CertificateVerifier (Design Section 8.3)
# The compile-time cert digest constant must survive R8 renaming so that
# runtime certificate verification (Req 9.12) works correctly.
# ============================================================
-keepclassmembers class com.offlinepay.core.security.CertificateVerifier {
    private static final java.lang.String EXPECTED_CERT_DIGEST;
}

# ============================================================
# PLAY INTEGRITY API
# ============================================================
-keep class com.google.android.play.core.integrity.** { *; }
-dontwarn com.google.android.play.core.integrity.**

# ============================================================
# ZXING — Barcode scanning fallback decoder
# ============================================================
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ============================================================
# TIMBER — Logging
# In release builds, Timber uses a no-op tree (no logs emitted).
# Keep Timber itself to avoid NoClassDefFoundError at runtime.
# ============================================================
-keep class timber.log.Timber { *; }
-keep class timber.log.Timber$* { *; }

# ============================================================
# REFLECTION — Suppress warnings for reflection-based libraries
# ============================================================
-dontwarn java.lang.reflect.**
-dontwarn sun.misc.Unsafe

# ============================================================
# GENERAL SUPPRESSION
# ============================================================
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
