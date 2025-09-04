package com.volmit.iris.core.scripting.kotlin.runner.resolver

import com.volmit.iris.util.io.IO
import org.dom4j.Document
import org.dom4j.DocumentFactory
import org.dom4j.io.SAXReader
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.dependencies.ArtifactWithLocation
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver

class LocalMavenDependenciesResolver : DependenciesResolver {
    private lateinit var localRepo: File
    private val maven = MavenDependenciesResolver(true)

    override fun acceptsRepository(repositoryCoordinates: RepositoryCoordinates) = maven.acceptsRepository(repositoryCoordinates)
    override fun acceptsArtifact(artifactCoordinates: String) = maven.acceptsArtifact(artifactCoordinates)

    override fun addRepository(
        repositoryCoordinates: RepositoryCoordinates,
        options: ExternalDependenciesResolver.Options,
        sourceCodeLocation: SourceCode.LocationWithId?
    ) = maven.addRepository(repositoryCoordinates, options, sourceCodeLocation)

    override suspend fun resolve(
        artifactsWithLocations: List<ArtifactWithLocation>,
        options: ExternalDependenciesResolver.Options
    ): ResultWithDiagnostics<List<File>> {
        val userOld: String? = System.getProperty("org.apache.maven.user-settings")
        val globalOld: String? = System.getProperty("org.apache.maven.global-settings")

        try {
            System.setProperty("org.apache.maven.user-settings", createSettings(userOld))
            System.clearProperty("org.apache.maven.global-settings")

            return maven.resolve(artifactsWithLocations, options)
        } finally {
            setProperty("org.apache.maven.user-settings", userOld)
            setProperty("org.apache.maven.global-settings", globalOld)
        }
    }

    private fun createSettings(user: String?): String {
        val settingsFile = File(localRepo, "settings.xml")
        val document = readSettings(user)
        val node = document.selectSingleNode("//localRepository")
            ?: document.rootElement.addElement("localRepository")

        if (node.text != localRepo.absolutePath) {
            node.text = localRepo.absolutePath

            IO.write(settingsFile, document)
        }
        return settingsFile.absolutePath
    }

    private fun readSettings(user: String?): Document {
        val baseFile = user?.let(::File)?.takeIf { it.exists() } ?: File(
            System.getProperty("user.home"),
            ".m2/settings.xml"
        ).takeIf { it.exists() }?.let { return SAXReader().read(it) }
        return if (baseFile != null) SAXReader().read(baseFile) else DocumentFactory.getInstance().createDocument().also {
            it.addElement("settings")
                .addAttribute("xmlns", "http://maven.apache.org/SETTINGS/1.0.0")
                .addAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
                .addAttribute("xsi:schemaLocation", "http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd")
        }
    }

    private fun setProperty(name: String, value: String?) {
        when(value) {
            null -> System.clearProperty(name)
            else -> System.setProperty(name, value)
        }
    }

    override fun addPack(directory: File) {
        if (!::localRepo.isInitialized) {
            localRepo = directory.resolve(".iris/m2")
        }
    }
}