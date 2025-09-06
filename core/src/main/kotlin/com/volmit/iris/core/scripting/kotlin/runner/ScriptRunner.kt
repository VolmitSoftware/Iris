package com.volmit.iris.core.scripting.kotlin.runner

import com.volmit.iris.core.scripting.kotlin.base.SimpleScript
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.host.withDefaultsFrom
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

class ScriptRunner(
    private val host: BasicJvmScriptingHost,
    val baseDir: File
) {
    constructor(baseDir: File) : this(BasicJvmScriptingHost(), baseDir)

    private val configs = ConcurrentHashMap<KClass<*>, ScriptCompilationConfiguration>()
    private val hostConfig = host.baseHostConfiguration.withDefaultsFrom(defaultJvmScriptingHostConfiguration)
    private val sharedClassLoader = SharedClassLoader()
    private var resolver = createResolver(baseDir)

    fun compile(type: KClass<*>, raw: String, name: String? = null) = compile(type, raw.toScriptSource(name))
    fun compile(type: KClass<*>, file: File, preloaded: String? = null) = compile(type, FileScriptSource(file, preloaded))

    fun clear() {
        configs.clear()
        resolver = createResolver(baseDir)
    }

    private fun compile(
        type: KClass<*>,
        code: SourceCode
    ): ResultWithDiagnostics<Script> = host.runInCoroutineContext {
        host.compiler(code, configs.computeIfAbsent(type, ::createConfig))
            .map { CachedScript(it, host, hostConfig) }
    }

    private fun createConfig(type: KClass<*>) = createCompilationConfigurationFromTemplate(
        KotlinType(type),
        hostConfig,
        type
    ) {
        dependencyResolver(resolver)
        packDirectory(baseDir)
        sharedClassloader(sharedClassLoader)

        if (SimpleScript::class.java.isAssignableFrom(type.java))
            return@createCompilationConfigurationFromTemplate

        jvm {
            dependenciesFromClassContext(type, wholeClasspath = true)
            dependenciesFromClassContext(this::class, wholeClasspath = true)
            dependenciesFromClassContext(KotlinScript::class, wholeClasspath = true)
        }

        configure()
    }
}
