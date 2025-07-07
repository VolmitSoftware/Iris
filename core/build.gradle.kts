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

plugins {
    java
    `java-library`
    id("com.gradleup.shadow")
    id("io.sentry.jvm.gradle")
}

val apiVersion = "1.19"
val main = "com.volmit.iris.Iris"

val dynamic: Configuration by configurations.creating
configurations.compileOnly { extendsFrom(dynamic) }

/**
 * Dependencies.
 *
 * Provided or classpath dependencies are not shaded and are available on the runtime classpath
 *
 * Shaded dependencies are not available at runtime, nor are they available on mvn central so they
 * need to be shaded into the jar (increasing binary size)
 *
 * Dynamically loaded dependencies are defined in the plugin.yml (updating these must be updated in the
 * plugin.yml also, otherwise they wont be available). These do not increase binary size). Only declare
 * these dependencies if they are available on mvn central.
 */
dependencies {
    // Provided or Classpath
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.apache.logging.log4j:log4j-api:2.19.0")
    compileOnly("org.apache.logging.log4j:log4j-core:2.19.0")

    // Third Party Integrations
    compileOnly("com.nexomc:nexo:1.6.0")
    compileOnly("com.github.LoneDev6:api-itemsadder:3.4.1-r4")
    compileOnly("com.github.PlaceholderAPI:placeholderapi:2.11.3")
    compileOnly("com.github.Ssomar-Developement:SCore:4.23.10.8")
    compileOnly("net.Indyuce:MMOItems-API:6.9.5-SNAPSHOT")
    compileOnly("com.willfp:EcoItems:5.44.0")
    compileOnly("io.lumine:Mythic-Dist:5.2.1")
    compileOnly("io.lumine:MythicCrucible-Dist:2.0.0")
    compileOnly("me.kryniowesegryderiusz:kgenerators-core:7.3") {
        isTransitive = false
    }
    compileOnly("org.mvplugins.multiverse.core:multiverse-core:5.1.0")
    //implementation files("libs/CustomItems.jar")

    // Shaded
    implementation("com.dfsek:paralithic:0.8.1")
    implementation("io.papermc:paperlib:1.0.5")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("net.kyori:adventure-platform-bukkit:4.3.4")
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("org.bstats:bstats-bukkit:3.1.0")

    // Dynamically Loaded
    dynamic("commons-io:commons-io:2.13.0")
    dynamic("commons-lang:commons-lang:2.6")
    dynamic("com.github.oshi:oshi-core:6.6.5")
    dynamic("org.lz4:lz4-java:1.8.0")
    dynamic("it.unimi.dsi:fastutil:8.5.8")
    dynamic("com.googlecode.concurrentlinkedhashmap:concurrentlinkedhashmap-lru:1.4.2")
    dynamic("org.zeroturnaround:zt-zip:1.14")
    dynamic("com.google.code.gson:gson:2.10.1")
    dynamic("org.ow2.asm:asm:9.8")
    dynamic("com.github.ben-manes.caffeine:caffeine:3.0.6")
    dynamic("org.apache.commons:commons-lang3:3.12.0")
    dynamic("net.bytebuddy:byte-buddy:1.17.5")
    dynamic("net.bytebuddy:byte-buddy-agent:1.17.5")
    dynamic("org.dom4j:dom4j:2.1.4")
}

java {
    disableAutoTargetJvm()
}

sentry {
    includeSourceContext = true

    org = "volmit-software"
    projectName = "iris"
    authToken = findProperty("sentry.auth.token") as String? ?: System.getenv("SENTRY_AUTH_TOKEN")
}

tasks {
    /**
     * We need parameter meta for the decree command system
     */
    compileJava {
        options.compilerArgs.add("-parameters")
        options.encoding = "UTF-8"
    }

    /**
     * Expand properties into plugin yml
     */
    processResources {
        inputs.properties(
            "name" to rootProject.name,
            "version" to rootProject.version,
            "apiVersion" to apiVersion,
            "main" to main,
            "libraries" to dynamic.allDependencies.map { "\n  - $it" }.sorted().joinToString("")
        )
        filesMatching("**/plugin.yml") {
            expand(inputs.properties)
        }
    }

    shadowJar {
        mergeServiceFiles()
        relocate("com.dfsek.paralithic", "com.volmit.iris.util.paralithic")
        relocate("io.papermc.lib", "com.volmit.iris.util.paper")
        relocate("net.kyori", "com.volmit.iris.util.kyori")
        relocate("org.bstats", "com.volmit.iris.util.metrics")
        relocate("io.sentry", "com.volmit.iris.util.sentry")

        //minimize()
        dependencies {
            exclude(dependency("org.ow2.asm:asm:"))
            exclude(dependency("org.jetbrains:"))
        }
    }
}

/**
 * Gradle is weird sometimes, we need to delete the plugin yml from the build folder to actually filter properly.
 */
afterEvaluate {
    layout.buildDirectory.file("resources/main/plugin.yml").get().asFile.delete()
}