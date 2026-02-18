import de.undercouch.gradle.tasks.download.Download
import org.gradle.jvm.toolchain.JavaLanguageVersion
import kotlin.system.exitProcess

/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

buildscript {
    repositories.maven("https://jitpack.io")
    dependencies.classpath("com.github.VolmitSoftware:NMSTools:c88961416f")
}

plugins {
    java
    `java-library`
    alias(libs.plugins.download)
}

group = "art.arcane"
version = "4.0.0-1.21.11"
val volmLibCoordinate: String = providers.gradleProperty("volmLibCoordinate")
    .orElse("com.github.VolmitSoftware:VolmLib:master-SNAPSHOT")
    .get()

apply<ApiGenerator>()

// ADD YOURSELF AS A NEW LINE IF YOU WANT YOUR OWN BUILD TASK GENERATED
// ======================== WINDOWS =============================
registerCustomOutputTask("Cyberpwn", "C://Users/cyberpwn/Documents/development/server/plugins")
registerCustomOutputTask("Psycho", "C://Dan/MinecraftDevelopment/Server/plugins")
registerCustomOutputTask("ArcaneArts", "C://Users/arcane/Documents/development/server/plugins")
registerCustomOutputTask("Coco", "D://mcsm/plugins")
registerCustomOutputTask("Strange", "D://Servers/1.17 Test Server/plugins")
registerCustomOutputTask("Vatuu", "D://Minecraft/Servers/1.19.4/plugins")
registerCustomOutputTask("CrazyDev22", "C://Users/Julian/Desktop/server/plugins")
registerCustomOutputTask("PixelFury", "C://Users/repix/workplace/Iris/1.21.3 - Development-Public-v3/plugins")
registerCustomOutputTask("PixelFuryDev", "C://Users/repix/workplace/Iris/1.21 - Development-v3/plugins")
// ========================== UNIX ==============================
registerCustomOutputTaskUnix("CyberpwnLT", "/Users/danielmills/development/server/plugins")
registerCustomOutputTaskUnix("PsychoLT", "/Users/brianfopiano/Developer/RemoteGit/[Minecraft Server]/consumers/plugin-consumers/dropins/plugins")
registerCustomOutputTaskUnix("PixelMac", "/Users/test/Desktop/mcserver/plugins")
registerCustomOutputTaskUnix("CrazyDev22LT", "/home/julian/Desktop/server/plugins")
// ==============================================================

val nmsBindings = mapOf(
    "v1_21_R7" to "1.21.11-R0.1-SNAPSHOT",
)
nmsBindings.forEach { (key, value) ->
    project(":nms:$key") {
        apply<JavaPlugin>()

        nmsBinding {
            jvm = 21
            version = value
            type = NMSBinding.Type.DIRECT
        }

        dependencies {
            compileOnly(project(":core"))
            compileOnly(volmLibCoordinate) {
                isChanging = true
                isTransitive = false
            }
            compileOnly(rootProject.libs.annotations)
            compileOnly(rootProject.libs.byteBuddy.core)
        }
    }
}

val included: Configuration by configurations.creating
val jarJar: Configuration by configurations.creating
dependencies {
    for (key in nmsBindings.keys) {
        included(project(":nms:$key", "reobf"))
    }
    included(project(":core", "shadow"))
    jarJar(project(":core:agent"))
}

tasks {
    jar {
        inputs.files(included)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(jarJar, provider { included.resolve().map(::zipTree) })
        archiveFileName.set("Iris-${project.version}.jar")
    }

    register<Copy>("iris") {
        group = "iris"
        dependsOn("jar")
        from(layout.buildDirectory.file("libs/Iris-${project.version}.jar"))
        into(layout.buildDirectory)
    }

    register<Copy>("irisDev") {
        group = "iris"
        from(project(":core").layout.buildDirectory.files("libs/core-javadoc.jar", "libs/core-sources.jar"))
        rename { it.replace("core", "Iris-${project.version}") }
        into(layout.buildDirectory)
        dependsOn(":core:sourcesJar")
        dependsOn(":core:javadocJar")
    }

    val cli = file("sentry-cli.exe")
    register<Download>("downloadCli") {
        group = "io.sentry"
        src("https://release-registry.services.sentry.io/apps/sentry-cli/latest?response=download&arch=x86_64&platform=${System.getProperty("os.name")}&package=sentry-cli")
        dest(cli)

        doLast {
            cli.setExecutable(true)
        }
    }

    register("release") {
        group = "io.sentry"
        dependsOn("downloadCli")
        doLast {
            val url = "http://sentry.volmit.com:8080"
            val authToken = project.findProperty("sentry.auth.token") ?: System.getenv("SENTRY_AUTH_TOKEN")
            val org = "sentry"
            val projectName = "iris"
            exec(cli, "--url", url , "--auth-token", authToken, "releases", "new", "-o", org, "-p", projectName, version)
            exec(cli, "--url", url , "--auth-token", authToken, "releases", "set-commits", "-o", org, "-p", projectName, version, "--auto", "--ignore-missing")
            //exec(cli, "--url", url, "--auth-token", authToken, "releases", "finalize", "-o", org, "-p", projectName, version)
            cli.delete()
        }
    }
}

fun exec(vararg command: Any) {
    val p = ProcessBuilder(command.map { it.toString() })
        .start()
    p.inputStream.reader().useLines { it.forEach(::println) }
    p.errorStream.reader().useLines { it.forEach(::println) }
    p.waitFor()
}

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
}

allprojects {
    apply<JavaPlugin>()

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.org/repository/maven-public/")

        maven("https://jitpack.io") // EcoItems, score
        maven("https://repo.nexomc.com/releases/") // nexo
        maven("https://maven.devs.beer/") // itemsadder
        maven("https://repo.extendedclip.com/releases/") // placeholderapi
        maven("https://mvn.lumine.io/repository/maven-public/") // mythic
        maven("https://nexus.phoenixdevt.fr/repository/maven-public/") //MMOItems
        maven("https://repo.onarandombox.com/content/groups/public/") //Multiverse Core
    }

    dependencies {
        // Provided or Classpath
        compileOnly(rootProject.libs.lombok)
        annotationProcessor(rootProject.libs.lombok)
    }

    /**
     * We need parameter meta for the decree command system
     */
    tasks {
        compileJava {
            options.compilerArgs.add("-parameters")
            options.encoding = "UTF-8"
            options.debugOptions.debugLevel = "none"
        }

        javadoc {
            options.encoding = "UTF-8"
            options.quiet()
            //options.addStringOption("Xdoclint:none") // TODO: Re-enable this
        }

        register<Jar>("sourcesJar") {
            archiveClassifier.set("sources")
            from(sourceSets.main.map { it.allSource })
        }

        register<Jar>("javadocJar") {
            archiveClassifier.set("javadoc")
            from(javadoc.map { it.destinationDir!! })
        }
    }
}

if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
    System.err.println()
    System.err.println("=========================================================================================================")
    System.err.println("You must run gradle on Java 21 or newer. You are using " + JavaVersion.current())
    System.err.println()
    System.err.println("=== For IDEs ===")
    System.err.println("1. Configure the project for Java 21 toolchain")
    System.err.println("2. Configure the bundled gradle to use Java 21+ in settings")
    System.err.println()
    System.err.println("=== For Command Line (gradlew) ===")
    System.err.println("1. Install JDK 21 from https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html")
    System.err.println("2. Set JAVA_HOME environment variable to the new jdk installation folder such as C:\\Program Files\\Java\\jdk-21.0.4")
    System.err.println("3. Open a new command prompt window to get the new environment variables if need be.")
    System.err.println("=========================================================================================================")
    System.err.println()
    exitProcess(69)
}


fun registerCustomOutputTask(name: String, path: String) {
    if (!System.getProperty("os.name").lowercase().contains("windows")) {
        return
    }

    tasks.register<Copy>("build$name") {
        group = "development"
        outputs.upToDateWhen { false }
        dependsOn("iris")
        from(layout.buildDirectory.file("Iris-${project.version}.jar"))
        into(file(path))
        rename { "Iris.jar" }
    }
}

fun registerCustomOutputTaskUnix(name: String, path: String) {
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        return
    }

    tasks.register<Copy>("build$name") {
        group = "development"
        outputs.upToDateWhen { false }
        dependsOn("iris")
        from(layout.buildDirectory.file("Iris-${project.version}.jar"))
        into(file(path))
        rename { "Iris.jar" }
    }
}
