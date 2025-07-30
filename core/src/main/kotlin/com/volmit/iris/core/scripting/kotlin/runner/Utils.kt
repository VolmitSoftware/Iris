package com.volmit.iris.core.scripting.kotlin.runner

import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.RuntimeException
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.CompoundDependenciesResolver
import kotlin.script.experimental.dependencies.FileSystemDependenciesResolver
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver
import kotlin.script.experimental.dependencies.resolveFromScriptSourceAnnotations
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.classpathFromClassloader

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


internal val resolver = CompoundDependenciesResolver(FileSystemDependenciesResolver(), MavenDependenciesResolver())
internal fun configureMavenDepsOnAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
    val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.takeIf { it.isNotEmpty() }
        ?: return context.compilationConfiguration.asSuccess()
    return runBlocking {
        resolver.resolveFromScriptSourceAnnotations(annotations)
    }.onSuccess {
        context.compilationConfiguration.with {
            updateClasspath(it)
        }.asSuccess()
    }
}

internal fun Collection<File>.format(projectDir: File): Collection<String> {
    val projectDir = projectDir.absolutePath
    val home = File(System.getProperty("user.home")).absolutePath
    return map { format(it, projectDir, home) }.toSet()
}

internal val ClassLoader.classpath get() = classpathFromClassloader(this) ?: emptyList()

private fun format(file: File, projectDir: String, home: String): String {
    val path = file.absolutePath
    return when {
        path.startsWith(projectDir) -> $$"$PROJECT_DIR$/$${path.substring(projectDir.length + 1)}"
        path.startsWith(home) -> $$"$USER_HOME$/$${path.substring(home.length + 1)}"
        else -> path
    }
}

fun <R> ResultWithDiagnostics<R>.valueOrThrow(message: CharSequence): R = valueOr {
    throw RuntimeException(it.reports.joinToString("\n", "$message\n") { r -> r.render(withStackTrace = true) })
}