package com.volmit.iris.core.scripting.kotlin.runner

import com.volmit.iris.core.scripting.kotlin.runner.resolver.CompoundDependenciesResolver
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.script.experimental.api.*
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
internal fun createResolver(baseDir: File = workDir) = CompoundDependenciesResolver(baseDir)

private val resolver = createResolver()
internal fun configureMavenDepsOnAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
    val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.takeIf { it.isNotEmpty() }
        ?: return context.compilationConfiguration.asSuccess()

    val reports = mutableListOf<ScriptDiagnostic>()
    val resolver = context.compilationConfiguration[ScriptCompilationConfiguration.dependencyResolver] ?: resolver
    context.compilationConfiguration[ScriptCompilationConfiguration.packDirectory]
        ?.addPack(resolver)
        ?: context.script.locationId
            ?.let(::File)
            ?.takeIf { it.exists() }
            ?.run {
                val location = SourceCode.LocationWithId(context.script.locationId!!, SourceCode.Location(SourceCode.Position(0, 0)))
                val parts = normalize().absolutePath.split(File.separatorChar)
                for (i in parts.size - 1 downTo 1) {
                    if (parts[i] != "scripts") continue
                    val pack = File(parts.subList(0, i).joinToString(File.separator))
                    if (!File(pack, "dimensions${File.separator}${parts[i - 1]}.json").exists())
                        continue
                    pack.addPack(resolver)
                    reports.add(ScriptDiagnostic(
                        ScriptDiagnostic.unspecifiedInfo,
                        "Adding pack \"$pack\"",
                        ScriptDiagnostic.Severity.INFO,
                        location
                    ))
                }
            }

    return runBlocking {
        resolver.resolveFromScriptSourceAnnotations(annotations)
    }.onSuccess {
        context.compilationConfiguration.with {
            updateClasspath(it)
        }.asSuccess()
    }.appendReports(reports)
}

internal val ClassLoader.classpath get() = classpathFromClassloader(this) ?: emptyList()

fun <R> ResultWithDiagnostics<R>.valueOrThrow(message: CharSequence): R = valueOr {
    throw RuntimeException(it.reports.joinToString("\n", "$message\n") { r -> r.render(withStackTrace = true) })
}

val ScriptCompilationConfigurationKeys.dependencyResolver by PropertiesCollection.key(resolver)
val ScriptCompilationConfigurationKeys.packDirectory by PropertiesCollection.key<File>()

private fun File.addPack(resolver: CompoundDependenciesResolver) = resolver.addPack(this)
private fun <R> ResultWithDiagnostics<R>.appendReports(reports : Collection<ScriptDiagnostic>) =
    if (reports.isEmpty()) this
    else when (this) {
        is ResultWithDiagnostics.Success -> ResultWithDiagnostics.Success(value, this.reports + reports)
        is ResultWithDiagnostics.Failure -> ResultWithDiagnostics.Failure(this.reports + reports)
    }