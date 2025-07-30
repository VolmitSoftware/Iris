package com.volmit.iris.core.scripting.kotlin.runner

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.CompoundDependenciesResolver
import kotlin.script.experimental.dependencies.FileSystemDependenciesResolver
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver
import kotlin.script.experimental.dependencies.resolveFromScriptSourceAnnotations
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.util.PropertiesCollection

internal fun <T, R> ResultWithDiagnostics<T>.map(transformer: (T) -> R): ResultWithDiagnostics<R> = when (this) {
    is ResultWithDiagnostics.Success -> ResultWithDiagnostics.Success(transformer(value), reports)
    is ResultWithDiagnostics.Failure -> this
}

internal fun EvaluationResult.valueOrNull() = returnValue.valueOrNull()
internal fun ResultValue.valueOrNull(): Any? =
    when (this) {
        is ResultValue.Value -> value
        else -> null
    }

internal fun createResolver(baseDir: File = File(".").normalize()) = CompoundDependenciesResolver(FileDependenciesResolver(baseDir), MavenDependenciesResolver())

private val resolver = createResolver()
internal fun configureMavenDepsOnAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
    val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.takeIf { it.isNotEmpty() }
        ?: return context.compilationConfiguration.asSuccess()

    val resolver = context.compilationConfiguration[ScriptCompilationConfiguration.dependencyResolver] ?: resolver
    return runBlocking {
        resolver.resolveFromScriptSourceAnnotations(annotations)
    }.onSuccess {
        context.compilationConfiguration.with {
            updateClasspath(it)
        }.asSuccess()
    }
}

internal val ClassLoader.classpath get() = classpathFromClassloader(this) ?: emptyList()

fun <R> ResultWithDiagnostics<R>.valueOrThrow(message: CharSequence): R = valueOr {
    throw RuntimeException(it.reports.joinToString("\n", "$message\n") { r -> r.render(withStackTrace = true) })
}

val ScriptCompilationConfigurationKeys.dependencyResolver by PropertiesCollection.key(resolver)