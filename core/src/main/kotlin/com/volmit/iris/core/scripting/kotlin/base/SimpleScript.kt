package com.volmit.iris.core.scripting.kotlin.base

import com.volmit.iris.core.scripting.kotlin.runner.configureMavenDepsOnAnnotations
import com.volmit.iris.util.misc.SlimJar
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.isStandalone
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.dependencies.DependsOn
import kotlin.script.experimental.dependencies.Repository
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm

@KotlinScript(fileExtension = "simple.kts", compilationConfiguration = SimpleScriptDefinition::class)
abstract class SimpleScript

object SimpleScriptDefinition : ScriptCompilationConfiguration({
    SlimJar.load(null)

    isStandalone(false)
    defaultImports(
        "kotlin.script.experimental.dependencies.DependsOn",
        "kotlin.script.experimental.dependencies.Repository",
        "com.volmit.iris.Iris.info",
        "com.volmit.iris.Iris.debug",
        "com.volmit.iris.Iris.warn",
        "com.volmit.iris.Iris.error"
    )

    jvm {
        dependenciesFromClassContext(SimpleScript::class, wholeClasspath = true)
    }

    refineConfiguration {
        onAnnotations(DependsOn::class, Repository::class, handler = ::configureMavenDepsOnAnnotations)
    }
}) {
    private fun readResolve(): Any = SimpleScriptDefinition
}