package com.volmit.iris.core.scripting.kotlin.runner

import com.volmit.iris.core.scripting.kotlin.runner.resolver.CompoundDependenciesResolver
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URLClassLoader
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.DependsOn
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.script.experimental.dependencies.Repository
import kotlin.script.experimental.dependencies.addRepository
import kotlin.script.experimental.dependencies.impl.SimpleExternalDependenciesResolverOptionsParser
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.JvmDependencyFromClassLoader
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.script.experimental.util.filterByAnnotationType

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
private val loader = SharedClassLoader()

private fun configureMavenDepsOnAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> = runCatching {
    val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.takeIf { it.isNotEmpty() }
        ?: return context.compilationConfiguration.asSuccess()

    val reports = mutableListOf<ScriptDiagnostic>()
    val loader = context.compilationConfiguration[ScriptCompilationConfiguration.sharedClassloader] ?: loader
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
        resolver.resolveDependencies(annotations)
    }.onSuccess { classpath ->
        context.compilationConfiguration.with {
            val newClasspath = classpath.filterNewClasspath(this[ScriptCompilationConfiguration.dependencies])
                ?: return@with
            val shared = classpath.mapNotNull { p -> p.first.takeIf { p.second } }
            if (shared.isNotEmpty()) loader.addFiles(shared)

            val regular = newClasspath
                .map { p -> p.first }
                .let { JvmDependency(it) }
            ScriptCompilationConfiguration.dependencies.append(regular)
        }.asSuccess()
    }.appendReports(reports)
}.getOrElse { ResultWithDiagnostics.Failure(it.asDiagnostics()) }

private fun Collection<Pair<File, Boolean>>.filterNewClasspath(known: Collection<ScriptDependency>?): List<Pair<File, Boolean>>? {
    if (isEmpty()) return null

    val knownClasspath = known?.flatMapTo(hashSetOf()) {
        (it as? JvmDependency)?.classpath ?: emptyList()
    }

    return filterNot { knownClasspath?.contains(it.first) == true }.takeIf { it.isNotEmpty() }
}

private suspend fun ExternalDependenciesResolver.resolveDependencies(
    annotations: Iterable<ScriptSourceAnnotation<*>>
): ResultWithDiagnostics<List<Pair<File, Boolean>>> {
    val reports = mutableListOf<ScriptDiagnostic>()
    annotations.forEach { (annotation, locationWithId) ->
        when (annotation) {
            is Repository -> {
                val options = SimpleExternalDependenciesResolverOptionsParser(*annotation.options, locationWithId = locationWithId)
                    .valueOr { return it }

                for (coordinates in annotation.repositoriesCoordinates) {
                    val added = addRepository(coordinates, options, locationWithId)
                        .also { reports.addAll(it.reports) }
                        .valueOr { return it }

                    if (!added)
                        return reports + makeFailureResult(
                            "Unrecognized repository coordinates: $coordinates",
                            locationWithId = locationWithId
                        )
                }
            }
            is DependsOn -> {}
            else -> return reports + makeFailureResult("Unknown annotation ${annotation.javaClass}", locationWithId = locationWithId)
        }
    }

    return reports + annotations.filterByAnnotationType<DependsOn>()
        .flatMapSuccess { (annotation, locationWithId) ->
            SimpleExternalDependenciesResolverOptionsParser(
                *annotation.options,
                locationWithId = locationWithId
            ).onSuccess { options ->
                annotation.artifactsCoordinates.asIterable().flatMapSuccess { artifactCoordinates ->
                    resolve(artifactCoordinates, options, locationWithId)
                }.map { files -> files.map { it to (options.shared ?: false) } }
            }
        }
}

private val ExternalDependenciesResolver.Options.shared get() = flag("shared")
internal val ClassLoader.classpath get() = classpathFromClassloader(this) ?: emptyList()

internal fun <R> ResultWithDiagnostics<R>.valueOrThrow(message: CharSequence): R = valueOr {
    throw RuntimeException(it.reports.joinToString("\n", "$message\n") { r -> r.render(withStackTrace = true) })
}

internal val ScriptCompilationConfigurationKeys.dependencyResolver by PropertiesCollection.key(resolver, true)
internal val ScriptCompilationConfigurationKeys.packDirectory by PropertiesCollection.key(workDir, true)
internal val ScriptCompilationConfigurationKeys.sharedClassloader by PropertiesCollection.key(loader, true)

private fun File.addPack(resolver: CompoundDependenciesResolver) = resolver.addPack(this)
private fun <R> ResultWithDiagnostics<R>.appendReports(reports : Collection<ScriptDiagnostic>) =
    if (reports.isEmpty()) this
    else when (this) {
        is ResultWithDiagnostics.Success -> ResultWithDiagnostics.Success(value, this.reports + reports)
        is ResultWithDiagnostics.Failure -> ResultWithDiagnostics.Failure(this.reports + reports)
    }

internal class SharedClassLoader(parent: ClassLoader = SharedClassLoader::class.java.classLoader) : URLClassLoader(arrayOf(), parent) {
    val dependency = JvmDependencyFromClassLoader { this }

    fun addFiles(files: List<File>) {
        files.forEach { addURL(it.toURI().toURL()) }
    }
}

internal fun ScriptCompilationConfiguration.Builder.configure() {
    refineConfiguration {
        beforeParsing { context -> try {
            context.compilationConfiguration.with {
                ScriptCompilationConfiguration.dependencies.append((this[ScriptCompilationConfiguration.sharedClassloader] ?: loader).dependency)
            }.asSuccess()
        } catch (e: Throwable) {
            ResultWithDiagnostics.Failure(e.asDiagnostics())
        }}

        onAnnotations(DependsOn::class, Repository::class, handler = ::configureMavenDepsOnAnnotations)
    }
}