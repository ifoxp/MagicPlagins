plugins {
    // Lets Gradle download the required JDK itself instead of relying on a
    // system install - Paper 26.x needs Java 25.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "MagicPlugin"
