// :core:domain is a pure Kotlin JVM module — zero Android dependencies.
// Per design.md Section 3.3: "Zero Android dependencies."
// Paging PagingData is an Android-specific type; domain layer exposes
// plain List<T> / Flow<List<T>> and the data layer wraps to PagingData.
plugins {
    id("offlinepay.kotlin.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:common"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
