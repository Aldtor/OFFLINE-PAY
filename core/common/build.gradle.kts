plugins {
    id("offlinepay.kotlin.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // javax.inject provides @Qualifier, @Inject, @Singleton — needed for Hilt-compatible
    // qualifier annotations on CoroutineDispatcher qualifiers (@IoDispatcher, etc.)
    // without introducing any Android framework dependency.
    api(libs.javax.inject)
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
