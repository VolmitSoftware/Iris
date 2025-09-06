package com.volmit.iris.core.scripting.kotlin.runner.resolver

import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.set
import kotlin.script.experimental.api.IterableResultsCollector
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asErrorDiagnostics
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.dependencies.ArtifactWithLocation
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates
import kotlin.script.experimental.dependencies.impl.makeResolveFailureResult

class CompoundDependenciesResolver(
    baseDir: File
) : DependenciesResolver {
    private val resolvers = listOf(FileDependenciesResolver(baseDir), LocalMavenDependenciesResolver())

    override fun acceptsRepository(repositoryCoordinates: RepositoryCoordinates): Boolean {
        return resolvers.any { it.acceptsRepository(repositoryCoordinates) }
    }

    override fun acceptsArtifact(artifactCoordinates: String): Boolean {
        return resolvers.any { it.acceptsArtifact(artifactCoordinates) }
    }

    override fun addRepository(
        repositoryCoordinates: RepositoryCoordinates,
        options: ExternalDependenciesResolver.Options,
        sourceCodeLocation: SourceCode.LocationWithId?
    ): ResultWithDiagnostics<Boolean> {
        var success = false
        var repositoryAdded = false
        val reports = mutableListOf<ScriptDiagnostic>()

        for (resolver in resolvers) {
            when (val result = resolver.addRepository(repositoryCoordinates, options, sourceCodeLocation)) {
                is ResultWithDiagnostics.Success -> {
                    success = true
                    repositoryAdded = repositoryAdded || result.value
                    reports.addAll(result.reports)
                }
                is ResultWithDiagnostics.Failure -> reports.addAll(result.reports)
            }
        }

        return when {
            success -> repositoryAdded.asSuccess(reports)
            reports.isEmpty() -> makeResolveFailureResult(
                "No dependency resolver found that recognizes the repository coordinates '$repositoryCoordinates'",
                sourceCodeLocation
            )
            else -> ResultWithDiagnostics.Failure(reports)
        }
    }

    override suspend fun resolve(
        artifactsWithLocations: List<ArtifactWithLocation>,
        options: ExternalDependenciesResolver.Options
    ): ResultWithDiagnostics<List<File>> {
        val resultsCollector = IterableResultsCollector<File>()

        val artifactToResolverIndex = mutableMapOf<ArtifactWithLocation, Int>().apply {
            for (artifactWithLocation in artifactsWithLocations) {
                put(artifactWithLocation, -1)
            }
        }

        while (artifactToResolverIndex.isNotEmpty()) {
            val resolverGroups = mutableMapOf<Int, MutableList<ArtifactWithLocation>>()

            for ((artifactWithLocation, resolverIndex) in artifactToResolverIndex) {
                val (artifact, sourceCodeLocation) = artifactWithLocation

                var currentIndex = resolverIndex + 1
                while (currentIndex < resolvers.size) {
                    if (resolvers[currentIndex].acceptsArtifact(artifact)) break
                    ++currentIndex
                }
                if (currentIndex == resolvers.size) {
                    if (resolverIndex == -1) {
                        resultsCollector.addDiagnostic(
                            "No suitable dependency resolver found for artifact '$artifact'"
                                .asErrorDiagnostics(locationWithId = sourceCodeLocation)
                        )
                    }
                } else {
                    resolverGroups
                        .getOrPut(currentIndex) { mutableListOf() }
                        .add(artifactWithLocation)
                }
            }

            artifactToResolverIndex.clear()
            for ((resolverIndex, artifacts) in resolverGroups) {
                val resolver = resolvers[resolverIndex]
                val resolveResult = resolver.resolve(artifacts, options)
                resultsCollector.add(resolveResult)
                if (resolveResult.reports.isNotEmpty()) {
                    for (artifact in artifacts) {
                        artifactToResolverIndex[artifact] = resolverIndex
                    }
                }
            }
        }

        return resultsCollector.getResult()
    }

    override fun addPack(directory: File) {
        resolvers.forEach { it.addPack(directory) }
    }
}
