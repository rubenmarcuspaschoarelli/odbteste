pluginManagement {
    repositories {
        google()  // Remove the content filters
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }  // Add JitPack here too
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io")
            credentials {
                username = "CarDr Team (CarDr-com)"
                password = "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"
            }}
    }
}
rootProject.name = "OBDIQAndroidSdk"
include(":app")
include(":OBDIQAndroidSdk")
