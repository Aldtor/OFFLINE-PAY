pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OfflinePay"

// Application module
include(":app")

// Core modules
include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:security")
include(":core:analytics")
include(":core:connectivity")
include(":core:telephony")

// Feature modules
include(":feature:onboarding")
include(":feature:dashboard")
include(":feature:scanner")
include(":feature:payment")
include(":feature:ussd")
include(":feature:pay123")
include(":feature:history")
include(":feature:merchant")
include(":feature:settings")
