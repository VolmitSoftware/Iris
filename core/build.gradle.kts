import io.github.slimjar.func.slimjarHelper
import io.github.slimjar.resolver.data.Mirror
import org.ajoberstar.grgit.Grgit
import java.net.URI

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
    alias(libs.plugins.shadow)
    alias(libs.plugins.sentry)
    alias(libs.plugins.slimjar)
    alias(libs.plugins.grgit)
}

val apiVersion = "1.19"
val main = "com.volmit.iris.Iris"
val lib = "com.volmit.iris.util"

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
    compileOnly(libs.spigot)
    compileOnly(libs.log4j.api)
    compileOnly(libs.log4j.core)

    // Third Party Integrations
    compileOnly(libs.nexo)
    compileOnly(libs.itemsadder)
    compileOnly(libs.placeholderApi)
    compileOnly(libs.score)
    compileOnly(libs.mmoitems)
    compileOnly(libs.ecoitems)
    compileOnly(libs.mythic)
    compileOnly(libs.mythicChrucible)
    compileOnly(libs.kgenerators) {
        isTransitive = false
    }
    compileOnly(libs.multiverseCore)

    // Shaded
    implementation(slimjarHelper("spigot"))
    implementation(rootProject.libs.platformUtils) {
        isTransitive = false
    }

    // Dynamically Loaded
    slim(libs.paralithic)
    slim(libs.paperlib)
    slim(libs.adventure.api)
    slim(libs.adventure.minimessage)
    slim(libs.adventure.platform)
    slim(libs.bstats)
    slim(libs.sentry)

    slim(libs.commons.io)
    slim(libs.commons.lang)
    slim(libs.commons.lang3)
    slim(libs.oshi)
    slim(libs.lz4)
    slim(libs.fastutil)
    slim(libs.lru)
    slim(libs.zip)
    slim(libs.gson)
    slim(libs.asm)
    slim(libs.bsf)
    slim(libs.rhino)
    slim(libs.caffeine)
    slim(libs.byteBuddy.core)
    slim(libs.byteBuddy.agent)
}

java {
    disableAutoTargetJvm()
}

sentry {
    autoInstallation.enabled = false
    includeSourceContext = true

    org = "volmit-software"
    projectName = "iris"
    authToken = findProperty("sentry.auth.token") as String? ?: System.getenv("SENTRY_AUTH_TOKEN")
}

slimJar {
    mirrors = listOf(Mirror(
        URI.create("https://maven-central.storage-download.googleapis.com/maven2").toURL(),
        URI.create("https://repo.maven.apache.org/maven2/").toURL()
    ))

    relocate("com.dfsek.paralithic", "$lib.paralithic")
    relocate("io.papermc.lib", "$lib.paper")
    relocate("net.kyori", "$lib.kyori")
    relocate("org.bstats", "$lib.metrics")
    relocate("io.sentry", "$lib.sentry")
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
            "environment" to if (project.hasProperty("release")) "production" else "development",
            "commit" to provider {
                val res = runCatching { project.extensions.getByType<Grgit>().head().id }
                res.getOrDefault("")
                    .takeIf { it.length == 40 } ?: {
                    logger.error("Git commit hash not found", res.exceptionOrNull())
                    "unknown"
                }()
            },
        )
        filesMatching("**/plugin.yml") {
            expand(inputs.properties)
        }
    }

    shadowJar {
        mergeServiceFiles()
        //minimize()
        relocate("io.github.slimjar", "$lib.slimjar")
        exclude("modules/loader-agent.isolated-jar")
    }
}

/**
 * Gradle is weird sometimes, we need to delete the plugin yml from the build folder to actually filter properly.
 */
afterEvaluate {
    layout.buildDirectory.file("resources/main/plugin.yml").get().asFile.delete()
}