rootProject.name = "AiAssistant"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

include(":core:domain")
include(":core:application")
include(":core:data")
include(":shared-contract")
include(":shared-ui")
include(":backend")
include(":web-host")
