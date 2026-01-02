import de.undercouch.gradle.tasks.download.Download
import xyz.jpenilla.runpaper.task.RunServer
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
    alias(libs.plugins.runPaper)
}

group = "com.volmit"
version = "3.8.1-1.20.1-1.21.10"

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
registerCustomOutputTaskUnix("PsychoLT", "/Users/brianfopiano/Developer/RemoteGit/Server/plugins")
registerCustomOutputTaskUnix("PixelMac", "/Users/test/Desktop/mcserver/plugins")
registerCustomOutputTaskUnix("CrazyDev22LT", "/home/julian/Desktop/server/plugins")
// ==============================================================

val serverMinHeap = "10G"
val serverMaxHeap = "10G"
val additionalFlags = "-XX:+AlwaysPreTouch"
//Valid values are: none, truecolor, indexed256, indexed16, indexed8
val color = "truecolor"
val errorReporting = "true" == findProperty("errorReporting")

val nmsBindings = mapOf(
        "v1_21_R6" to "1.21.10-R0.1-SNAPSHOT",
        "v1_21_R5" to "1.21.8-R0.1-SNAPSHOT",
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
nmsBindings.forEach { (key, value) ->
    project(":nms:$key") {
        apply<JavaPlugin>()

        nmsBinding {
            jvm = jvmVersion.getOrDefault(key, 21)
            version = value
            type = NMSBinding.Type.DIRECT
        }

        dependencies {
            compileOnly(project(":core"))
            compileOnly(rootProject.libs.annotations)
            compileOnly(rootProject.libs.byteBuddy.core)
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
        systemProperty("disable.watchdog", "true")
        systemProperty("net.kyori.ansi.colorLevel", color)
        systemProperty("com.mojang.eula.agree", true)
        systemProperty("iris.suppressReporting", !errorReporting)
        jvmArgs("-javaagent:${project(":core:agent").tasks.jar.flatMap { it.archiveFile }.get().asFile.absolutePath}")
        jvmArgs(additionalFlags.split(' '))
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
    resolutionStrategy.cacheChangingModulesFor(60, "minutes")
    resolutionStrategy.cacheDynamicVersionsFor(60, "minutes")
}

allprojects {
    apply<JavaPlugin>()

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
