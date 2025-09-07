package com.volmit.iris.core.scripting.kotlin.runner.resolver

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates
import kotlin.script.experimental.dependencies.impl.makeResolveFailureResult
import kotlin.script.experimental.dependencies.impl.toRepositoryUrlOrNull

class FileDependenciesResolver(
    private val baseDir: File,
) : DependenciesResolver {
    private val localRepos = ConcurrentHashMap.newKeySet<File>(1).also { it.add(baseDir) }

    private fun String.toRepositoryFileOrNull(): File? =
        File(baseDir, this).takeIf { it.exists() && it.isDirectory }

    private fun RepositoryCoordinates.toFilePath() =
        (this.toRepositoryUrlOrNull()?.takeIf { it.protocol == "file" }?.path ?: string).toRepositoryFileOrNull()

    override fun addRepository(
        repositoryCoordinates: RepositoryCoordinates,
        options: ExternalDependenciesResolver.Options,
        sourceCodeLocation: SourceCode.LocationWithId?
    ): ResultWithDiagnostics<Boolean> {
        if (!acceptsRepository(repositoryCoordinates)) return false.asSuccess()

        val repoDir = repositoryCoordinates.toFilePath()
            ?: return makeResolveFailureResult("Invalid repository location: '${repositoryCoordinates}'", sourceCodeLocation)

        localRepos.add(repoDir)

        return true.asSuccess()
    }

    override suspend fun resolve(
        artifactCoordinates: String,
        options: ExternalDependenciesResolver.Options,
        sourceCodeLocation: SourceCode.LocationWithId?
    ): ResultWithDiagnostics<List<File>> {
        if (!acceptsArtifact(artifactCoordinates)) throw IllegalArgumentException("Path is invalid")

        val messages = mutableListOf<String>()

        for (repo in localRepos) {
            // TODO: add coordinates and wildcard matching
            val file = File(repo, artifactCoordinates)
            when {
                !file.exists() -> messages.add("File '$file' not found")
                !file.isFile && !file.isDirectory -> messages.add("Path '$file' is neither file nor directory")
                else -> return ResultWithDiagnostics.Success(listOf(file))
            }
        }
        return makeResolveFailureResult(messages.joinToString("; "), sourceCodeLocation)
    }

    override fun acceptsArtifact(artifactCoordinates: String) =
        !artifactCoordinates.isBlank() // TODO: make check stronger, e.g. using NIO's Path

    override fun acceptsRepository(repositoryCoordinates: RepositoryCoordinates): Boolean = repositoryCoordinates.toFilePath() != null

    override fun addPack(directory: File) {
        localRepos.add(directory)
    }

}