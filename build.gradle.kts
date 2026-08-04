// =============================================================================
// MineDrop Network — Root Build Script
// =============================================================================

plugins {
    id("com.gradleup.shadow") version "8.3.5" apply false
}

allprojects {
    group = "net.minedrop"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://repo.codemc.io/repository/maven-public/")
    }
}
