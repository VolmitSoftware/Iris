package com.volmit.iris.core.scripting.kotlin.environment

import com.volmit.iris.Iris
import com.volmit.iris.core.IrisSettings
import com.volmit.iris.core.scripting.ExecutionEnvironment
import com.volmit.iris.core.scripting.kotlin.base.*
import com.volmit.iris.core.scripting.kotlin.runner.Script
import com.volmit.iris.core.scripting.kotlin.runner.ScriptRunner
import com.volmit.iris.core.scripting.kotlin.runner.classpath
import com.volmit.iris.core.scripting.kotlin.runner.valueOrNull
import com.volmit.iris.util.collection.KMap
import com.volmit.iris.util.data.KCache
import com.volmit.iris.util.format.C
import java.io.File
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.valueOrThrow
import kotlin.text.split

open class IrisSimpleExecutionEnvironment : ExecutionEnvironment.Simple {
    protected val compileCache = KCache<String, KMap<KClass<*>, ResultWithDiagnostics<Script>>>({ _ -> KMap() }, IrisSettings.get().performance.cacheSize.toLong())
    protected val runner = ScriptRunner()

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
        runner.clearConfigurations()
    }

    protected open fun compile(script: String, type: KClass<*>) =
        compileCache.get(script)
            .computeIfAbsent(type) { _ -> runner.compileText(type, script) }
            .valueOrThrow()

    private fun evaluate0(name: String, type: KClass<*>, properties: Map<String, Any?>? = null): Any? {
        val current = Thread.currentThread()
        val loader = current.contextClassLoader
        current.contextClassLoader = this.javaClass.classLoader
        try {
            return compile(name, type)
                .evaluate(properties)
                .valueOrThrow()
                .valueOrNull()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        current.contextClassLoader = loader

        return null
    }

    override fun configureProject(projectDir: File) {
        projectDir.mkdirs()
        val libs = javaClass.classLoader.parent.classpath
            .sortedBy { it.absolutePath }
            .toMutableList()
        libs.add(codeSource)
        libs.removeIf { libs.count { f -> f.name == it.name } > 1 }

        File(projectDir, "build.gradle.kts")
            .updateClasspath(libs)
    }

    companion object {
        private const val CLASSPATH = "val classpath = files("
        private val codeSource = File(IrisSimpleExecutionEnvironment::class.java.protectionDomain.codeSource.location.toURI())

        private fun File.updateClasspath(classpath: List<File>) {
            val test = if (exists()) readLines() else BASE_GRADLE
            writeText(test.updateClasspath(classpath))
        }

        private fun List<String>.updateClasspath(classpath: List<File>): String {
            val classpath = classpath.joinToString(",", CLASSPATH, ")") { "\"${it.escapedPath}\"" }
            val index = indexOfFirst { it.startsWith(CLASSPATH) }
            if (index == -1) {
                return "$classpath\n${joinToString("\n")}"
            }

            val mod = toMutableList()
            mod[index] = classpath
            return mod.joinToString("\n")
        }

        private val File.escapedPath
            get() = absolutePath.replace("\\", "\\\\").replace("\"", "\\\"")

        private val BASE_GRADLE = """
            val classpath = files()
            
            plugins {
                kotlin("jvm") version("2.1.20")
            }

            repositories {
                mavenCentral()
            }

            val script by configurations.creating
            configurations.compileOnly { extendsFrom(script) }
            configurations.kotlinScriptDef { extendsFrom(script) }
            configurations.kotlinScriptDefExtensions { extendsFrom(script) }
            configurations.kotlinCompilerClasspath { extendsFrom(script) }
            configurations.kotlinCompilerPluginClasspath { extendsFrom(script) }

            dependencies {
                add("script", classpath)
            }""".trimIndent().split("\n")
    }
}