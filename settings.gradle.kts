pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kotlin-fake-casino"
include(":app")

// The SDK comes from Maven Central, like it does for any other consumer — there
// is deliberately no local override, so what this demo builds against is exactly
// what a client gets. To debug against unpublished SDK changes, add
// `includeBuild("../kotlin-public-api")` here temporarily; Gradle then
// substitutes the local project for the ai.smartico coordinate.
