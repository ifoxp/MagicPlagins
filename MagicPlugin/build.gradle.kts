plugins {
    java
    // Generates .classpath/.project so the VSCode Java extension can resolve
    // paper-api and Adventure. Without it the editor flags every import as
    // unresolved even though the Gradle build is fine.
    eclipse
}

group = "dev.magic"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.123-stable")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}

// Copy the built jar straight into the test server
val deployToServer by tasks.registering(Copy::class) {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    into(rootProject.file("../Server_26.2/plugins"))
}

tasks.build { finalizedBy(deployToServer) }
