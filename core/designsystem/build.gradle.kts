plugins {
    id("offlinepay.android.library")
}

android {
    namespace = "com.offlinepay.core.designsystem"
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.compose.runtime)
    api(libs.compose.animation)
    api(libs.compose.animation.core)
    api(libs.compose.material.icons.extended)
    debugApi(libs.compose.ui.tooling)
    api(libs.compose.ui.tooling.preview)
    api(libs.androidx.core.ktx)
}
