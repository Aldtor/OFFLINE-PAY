plugins {
    `kotlin-dsl`
}

group = "com.offlinepay.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Ensure both compileJava and compileKotlin agree on JVM target = 17.
// The JBR bundled in Android Studio is JDK 21; without this explicit
// alignment the Kotlin Gradle Plugin reports an "Inconsistent JVM-target"
// error because compileJava defaults to 17 (from java{} above) while
// compileKotlin defaults to the JDK version (21).
tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all")
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.android.gradlePluginApi)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApp") {
            id = "offlinepay.android.app"
            implementationClass = "AndroidAppConventionPlugin"
        }
        register("androidLibrary") {
            id = "offlinepay.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "offlinepay.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "offlinepay.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
    }
}
