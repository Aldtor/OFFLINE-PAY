import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Apply the base Android library convention first
            pluginManager.apply(AndroidLibraryConventionPlugin::class.java)

            // Apply Hilt
            with(pluginManager) {
                apply("com.google.dagger.hilt.android")
            }
        }
    }
}
