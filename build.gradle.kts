import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar

plugins {
    `java`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.46.0.0")
}

tasks.test {
    useJUnitPlatform()
}

val serverDirectory = providers.gradleProperty("serverDir").orElse("server")

tasks.register<Copy>("copyPluginToServer") {
    description = "Builds and copies the plugin JAR into the local Paper server plugins directory."
    group = "distribution"
    dependsOn(tasks.named("build"))

    val pluginsDir = layout.projectDirectory.dir("${serverDirectory.get()}/plugins")

    from(tasks.named<Jar>("jar"))
    into(pluginsDir)

    doFirst {
        pluginsDir.asFile.mkdirs()
    }
}
