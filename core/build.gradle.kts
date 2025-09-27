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
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.lombok)
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
    slim(libs.commons.math3)
    slim(libs.oshi)
    slim(libs.lz4)
    slim(libs.fastutil)
    slim(libs.lru)
    slim(libs.zip)
    slim(libs.gson)
    slim(libs.asm)
    slim(libs.caffeine)
    slim(libs.byteBuddy.core)
    slim(libs.byteBuddy.agent)
    slim(libs.dom4j)
    slim(libs.jaxen)

    // Script Engine
    slim(libs.kotlin.stdlib)
    slim(libs.kotlin.coroutines)
    slim(libs.kotlin.scripting.common)
    slim(libs.kotlin.scripting.jvm)
    slim(libs.kotlin.scripting.jvm.host)
    slim(libs.kotlin.scripting.dependencies.maven) {
        constraints {
            slim(libs.mavenCore)
        }
    }
}

java {
    disableAutoTargetJvm()
}

sentry {
    url = "http://sentry.volmit.com:8080/"
    autoInstallation.enabled = false
    includeSourceContext = true

    org = "sentry"
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
    relocate("org.apache.maven", "$lib.maven")
    relocate("org.codehaus.plexus", "$lib.plexus")
    relocate("org.eclipse.sisu", "$lib.sisu")
    relocate("org.eclipse.aether", "$lib.aether")
    relocate("com.google.inject", "$lib.guice")
    relocate("org.dom4j", "$lib.dom4j")
    relocate("org.jaxen", "$lib.jaxen")
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

val templateSource = file("src/main/templates")
val templateDest = layout.buildDirectory.dir("generated/sources/templates")
val generateTemplates = tasks.register<Copy>("generateTemplates") {
    inputs.properties(
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

    from(templateSource)
    into(templateDest)
    rename { "com/volmit/iris/$it" }
    expand(inputs.properties)
}

tasks.generateSentryBundleIdJava {
    dependsOn(generateTemplates)
}

rootProject.tasks.named("prepareKotlinBuildScriptModel") {
    dependsOn(generateTemplates)
}

sourceSets.main {
    java.srcDir(generateTemplates.map { it.outputs })
}