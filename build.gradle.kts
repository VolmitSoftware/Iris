import com.volmit.nmstools.NMSToolsExtension
import com.volmit.nmstools.NMSToolsPlugin
import de.undercouch.gradle.tasks.download.Download
import xyz.jpenilla.runpaper.task.RunServer
import xyz.jpenilla.runtask.service.DownloadsAPIService
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
    dependencies.classpath("com.github.VolmitSoftware:NMSTools:c5cbc46ce6")
}

plugins {
    java
    `java-library`
    id("com.gradleup.shadow") version "8.3.6"
    id("de.undercouch.download") version "5.0.1"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("io.sentry.jvm.gradle") version "5.7.0"
}

version = "3.6.11-1.20.1-1.21.5"

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
registerCustomOutputTaskUnix("PsychoLT", "/Users/brianfopiano/Developer/RemoteGit/Server/plugins")
registerCustomOutputTaskUnix("PixelMac", "/Users/test/Desktop/mcserver/plugins")
registerCustomOutputTaskUnix("CrazyDev22LT", "/home/julian/Desktop/server/plugins")
// ==============================================================

val serverMinHeap = "2G"
val serverMaxHeap = "8G"
//Valid values are: none, truecolor, indexed256, indexed16, indexed8
val color = "truecolor"
val errorReporting = false

val nmsBindings = mapOf(
        "v1_21_R4" to "1.21.5-R0.1-SNAPSHOT",
        "v1_21_R3" to "1.21.4-R0.1-SNAPSHOT",
        "v1_21_R2" to "1.21.3-R0.1-SNAPSHOT",
        "v1_21_R1" to "1.21.1-R0.1-SNAPSHOT",
        "v1_20_R4" to "1.20.6-R0.1-SNAPSHOT",
        "v1_20_R3" to "1.20.4-R0.1-SNAPSHOT",
        "v1_20_R2" to "1.20.2-R0.1-SNAPSHOT",
        "v1_20_R1" to "1.20.1-R0.1-SNAPSHOT",
)
val jvmVersion = mapOf<String, Int>()
nmsBindings.forEach { key, value ->
    project(":nms:$key") {
        apply<JavaPlugin>()
        apply<NMSToolsPlugin>()

        repositories {
            maven("https://libraries.minecraft.net")
        }

        extensions.configure(NMSToolsExtension::class) {
            jvm = jvmVersion.getOrDefault(key, 21)
            version = value
        }

        dependencies {
            compileOnly(project(":core"))
            compileOnly("org.jetbrains:annotations:26.0.2")
        }
    }

    tasks.register<RunServer>("runServer-$key") {
        group = "servers"
        minecraftVersion(value.split("-")[0])
        minHeapSize = serverMinHeap
        maxHeapSize = serverMaxHeap
        pluginJars(tasks.jar.flatMap { it.archiveFile })
        javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(jvmVersion.getOrDefault(key, 21))}
        runDirectory.convention(layout.buildDirectory.dir("run/$key"))
        systemProperty("disable.watchdog", "")
        systemProperty("net.kyori.ansi.colorLevel", color)
        systemProperty("com.mojang.eula.agree", true)
        systemProperty("iris.suppressReporting", !errorReporting)
    }

    tasks.register<RunServer>("runFolia-$key") {
        group = "servers"
        downloadsApiService = DownloadsAPIService.folia(project)
        minecraftVersion(value.split("-")[0])
        minHeapSize = serverMinHeap
        maxHeapSize = serverMaxHeap
        pluginJars(tasks.jar.flatMap { it.archiveFile })
        javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(jvmVersion.getOrDefault(key, 21))}
        runDirectory.convention(layout.buildDirectory.dir("run/$key"))
        systemProperty("disable.watchdog", "")
        systemProperty("net.kyori.ansi.colorLevel", color)
        systemProperty("com.mojang.eula.agree", true)
    }
}

tasks {
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        nmsBindings.forEach { key, _ ->
            from(project(":nms:$key").tasks.named("remap").map { zipTree(it.outputs.files.singleFile) })
        }
        from(project(":core").tasks.shadowJar.flatMap { it.archiveFile }.map { zipTree(it) })
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
            val authToken = project.findProperty("sentry.auth.token") ?: System.getenv("SENTRY_AUTH_TOKEN")
            val org = "volmit-software"
            val projectName = "iris"
            exec(cli, "releases", "new", "--auth-token", authToken, "-o", org, "-p", projectName, version)
            exec(cli, "releases", "set-commits", "--auth-token", authToken, "-o", org, "-p", projectName, version, "--auto", "--ignore-missing")
            exec(cli, "releases", "finalize", "--auth-token", authToken, "-o", org, "-p", projectName, version)
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

dependencies {
    implementation(project(":core"))
}

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(60, "minutes")
    resolutionStrategy.cacheDynamicVersionsFor(60, "minutes")
}

allprojects {
    apply<JavaPlugin>()

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.org/repository/maven-public/")
        maven("https://mvn.lumine.io/repository/maven-public/")
        maven("https://jitpack.io")

        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
        maven("https://mvn.lumine.io/repository/maven/")
        maven("https://repo.triumphteam.dev/snapshots")
        maven("https://repo.mineinabyss.com/releases")
        maven("https://hub.jeff-media.com/nexus/repository/jeff-media-public/")
        maven("https://repo.nexomc.com/releases/")
    }

    dependencies {
        // Provided or Classpath
        compileOnly("org.projectlombok:lombok:1.18.36")
        annotationProcessor("org.projectlombok:lombok:1.18.36")
    }

    /**
     * We need parameter meta for the decree command system
     */
    tasks {
        compileJava {
            options.compilerArgs.add("-parameters")
            options.encoding = "UTF-8"
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

if (JavaVersion.current().toString() != "21") {
    System.err.println()
    System.err.println("=========================================================================================================")
    System.err.println("You must run gradle on Java 21. You are using " + JavaVersion.current())
    System.err.println()
    System.err.println("=== For IDEs ===")
    System.err.println("1. Configure the project for Java 21")
    System.err.println("2. Configure the bundled gradle to use Java 21 in settings")
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
