pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Kadb 2.1.5 SPAKE2 Android runtime is published through JitPack.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.Flyfish233") }
        }
    }
}

rootProject.name = "AiCalls"
include(":app")
include(":audio-bridge")
include(":privileged-helper")
include(":realtime-client")
include(":conversation-core")
include(":conversation-tools")
