package com.volmit.iris.core.scripting.kotlin.environment

import com.volmit.iris.Iris
import com.volmit.iris.core.IrisSettings
import com.volmit.iris.core.scripting.environment.SimpleEnvironment
import com.volmit.iris.core.scripting.kotlin.base.*
import com.volmit.iris.core.scripting.kotlin.runner.FileComponents
import com.volmit.iris.core.scripting.kotlin.runner.Script
import com.volmit.iris.core.scripting.kotlin.runner.ScriptRunner
import com.volmit.iris.core.scripting.kotlin.runner.classpath
import com.volmit.iris.core.scripting.kotlin.runner.value
import com.volmit.iris.core.scripting.kotlin.runner.valueOrThrow
import com.volmit.iris.util.collection.KMap
import com.volmit.iris.util.data.KCache
import com.volmit.iris.util.format.C
import java.io.File
import kotlin.reflect.KClass
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.text.split

open class IrisSimpleExecutionEnvironment(
    baseDir: File = File(".").absoluteFile
) : SimpleEnvironment {
    protected val compileCache = KCache<String, KMap<KClass<*>, ResultWithDiagnostics<Script>>>({ _ -> KMap() }, IrisSettings.get().performance.cacheSize.toLong())
    protected val runner = ScriptRunner(baseDir)

    override fun execute(
        script: String
    ) = execute(script, SimpleScript::class.java, null)

    override fun execute(
        script: String,
        type: Class<*>,
        vars: Map<String, Any?>?
    ) {
        Iris.debug("Execute Script (void) " + C.DARK_GREEN + script)
        evaluate0(script, type.kotlin, vars)
    }

    override fun evaluate(
        script: String
    ): Any? = evaluate(script, SimpleScript::class.java, null)

    override fun evaluate(
        script: String,
        type: Class<*>,
        vars: Map<String, Any?>?
    ): Any? {
        Iris.debug("Execute Script (for result) " + C.DARK_GREEN + script)
        return evaluate0(script, type.kotlin, vars)
    }

    override fun close() {
        compileCache.invalidate()
        runner.clear()
    }

    protected open fun compile(script: String, type: KClass<*>) =
        compileCache.get(script)
            .computeIfAbsent(type) { _ -> runner.compile(type, script) }
            .valueOrThrow("Failed to compile script")

    private fun evaluate0(name: String, type: KClass<*>, properties: Map<String, Any?>? = null): Any? {
        val current = Thread.currentThread()
        val loader = current.contextClassLoader
        current.contextClassLoader = this.javaClass.classLoader
        try {
            return compile(name, type)
                .evaluate(properties)
                .valueOrThrow("Failed to evaluate script")
                .value()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        current.contextClassLoader = loader

        return null
    }

    override fun configureProject() {
        runner.baseDir.mkdirs()
        val libs = listOf(javaClass.classLoader.classpath, KotlinScript::class.java.classLoader.classpath)
            .flatMap { it }
            .sortedBy { it.absolutePath }
            .toMutableList()

        File(runner.baseDir, "build.gradle.kts")
            .updateClasspath(libs)
    }

    companion object {
        private const val CLASSPATH = "val classpath = mapOf("

        private fun File.updateClasspath(classpath: List<File>) {
            val test = if (exists()) readLines() else BASE_GRADLE
            writeText(test.updateClasspath(classpath))
        }

        private fun List<String>.updateClasspath(classpath: List<File>): String {
            val components = linkedMapOf<String, FileComponents>()
            classpath.forEach {
                val parts = it.canonicalPath.split(File.separatorChar)
                if (parts.size <= 1) {
                    Iris.error("Invalid classpath entry: $it")
                    return@forEach
                }

                var parent = components.computeIfAbsent(parts[0]) { FileComponents(parts[0], true) }
                for (part in parts.subList(1, parts.size)) {
                    parent = parent.append(part)
                }
            }

            val mapped = components.values.associate {
                var current = it
                val root = buildString {
                    while (current.children.size == 1) {
                        append(current.segment)
                        append(File.separatorChar)
                        current = current.children.first()
                    }
                    append(current.segment)
                    append(File.separatorChar)
                }

                val result = mutableSetOf<String>()
                val queue = ArrayDeque<Pair<String?, Collection<FileComponents>>>()
                queue.add(null to current.children)
                while (queue.isNotEmpty()) {
                    val pair = queue.removeFirst()
                    val path = pair.first?.let { p -> p + File.separatorChar } ?: ""
                    pair.second.forEach { child ->
                        val path = path + child.segment
                        if (child.children.isEmpty()) result.add(path)
                        else queue.add(path to child.children)
                    }
                }

                root to result
            }


            val classpath = mapped.entries.joinToString(",", CLASSPATH, ")") {
                "\"${it.key}\" to setOf(${it.value.joinToString(", ") { f -> "\"$f\"" }})"
            }


            val mod = toMutableList()
            val index = indexOfFirst { it.startsWith(CLASSPATH) }
            if (index == -1) {
                mod.clear()
                mod.addAll(BASE_GRADLE)
            }

            mod[if (index == -1) 0 else index] = classpath
            return mod.joinToString("\n")
        }

        private val File.escapedPath
            get() = absolutePath.replace("\\", "\\\\").replace("\"", "\\\"")

        private const val ARTIFACT_ID = $$"local:${it.substringBeforeLast(\".jar\")}:1.0.0"
        private val BASE_GRADLE = """
            val classpath = mapOf()
            
            plugins {
                kotlin("jvm") version("2.2.0")
            }

            repositories {
                mavenCentral()
                flatDir {
                    dirs(classpath.keys)
                }
            }

            val script by configurations.creating
            configurations.compileOnly { extendsFrom(script) }
            configurations.kotlinScriptDef { extendsFrom(script) }
            configurations.kotlinScriptDefExtensions { extendsFrom(script) }
            configurations.kotlinCompilerClasspath { extendsFrom(script) }
            configurations.kotlinCompilerPluginClasspath { extendsFrom(script) }

            dependencies {
                classpath.values.flatMap { it }.forEach { script("$ARTIFACT_ID") }
            }""".trimIndent().split("\n")
    }
}