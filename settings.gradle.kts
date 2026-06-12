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
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "KehuiAI"
include(":app")
include(":core:ui")
include(":core:models")
include(":feature:image-generation")
include(":feature:video-generation")
include(":feature:model-management")
include(":inference:npu-engine")
