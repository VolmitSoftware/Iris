package com.volmit.iris.core.scripting.kotlin.runner

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.CompoundDependenciesResolver
import kotlin.script.experimental.dependencies.addRepository
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

private val workDir = File(".").normalize()
internal fun createResolver(baseDir: File = workDir) = CompoundDependenciesResolver(FileDependenciesResolver(baseDir), MavenDependenciesResolver())

private val resolver = createResolver()
internal fun configureMavenDepsOnAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
    val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.takeIf { it.isNotEmpty() }
        ?: return context.compilationConfiguration.asSuccess()

    val resolver = context.compilationConfiguration[ScriptCompilationConfiguration.dependencyResolver] ?: resolver
    val packDirectory = context.compilationConfiguration[ScriptCompilationConfiguration.packDirectory] ?: context.script.locationId?.let(::File)?.takeIf { it.exists() }?.run {
        val parts = normalize().absolutePath.split(File.separatorChar)

        var packDir: File? = null
        for (i in parts.size - 1 downTo 1) {
            if (parts[i] != "scripts") continue
            val pack = File(parts.subList(0, i).joinToString(File.separator))
            if (!File(pack, "dimensions${File.separator}${parts[i - 1]}.json").exists())
                continue
            packDir = pack
            break
        }
        packDir
    } ?: workDir

    return runBlocking {
        resolver.addRepository(packDirectory.toURI().toURL().toString())
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
val ScriptCompilationConfigurationKeys.packDirectory by PropertiesCollection.key<File>()