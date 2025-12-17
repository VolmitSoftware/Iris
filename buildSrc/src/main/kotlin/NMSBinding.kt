import NMSBinding.Type
import com.volmit.nmstools.NMSToolsExtension
import com.volmit.nmstools.NMSToolsPlugin
import io.papermc.paperweight.userdev.PaperweightUser
import io.papermc.paperweight.userdev.PaperweightUserDependenciesExtension
import io.papermc.paperweight.userdev.PaperweightUserExtension
import io.papermc.paperweight.userdev.attribute.Obfuscation
import io.papermc.paperweight.util.constants.REOBF_CONFIG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gradle.api.*
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.extensions.core.extra
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.work.DisableCachingByDefault
import java.io.RandomAccessFile
import javax.inject.Inject

class NMSBinding : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        val config = extra["nms"] as? Config ?: throw GradleException("No NMS binding configuration found")
        val jvm = config.jvm
        val type = config.type

        if (type == Type.USER_DEV) {
            plugins.apply(PaperweightUser::class.java)
            dependencies.extensions.findByType(PaperweightUserDependenciesExtension::class.java)
                ?.paperDevBundle(config.version)

            val java = extensions.findByType(JavaPluginExtension::class.java) ?: throw GradleException("Java plugin not found")
            java.toolchain.languageVersion.set(JavaLanguageVersion.of(jvm))

            val javaToolchains = project.extensions.getByType(JavaToolchainService::class.java) ?: throw GradleException("Java toolchain service not found")
            extensions.configure(PaperweightUserExtension::class.java) {
                it.javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
            }
        } else {
            extra["nmsTools.useBuildTools"] = type == Type.BUILD_TOOLS
            plugins.apply(NMSToolsPlugin::class.java)
            extensions.configure(NMSToolsExtension::class.java) {
                it.jvm.set(jvm)
                it.version.set(config.version)
            }

            configurations.register(REOBF_CONFIG) { conf ->
                conf.isCanBeConsumed = true
                conf.isCanBeResolved = false
                conf.attributes {
                    it.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                    it.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                    it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
                    it.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
                    it.attribute(Obfuscation.OBFUSCATION_ATTRIBUTE, objects.named(Obfuscation.OBFUSCATED))
                }
                conf.outgoing.artifact(tasks.named("remap"))
            }
        }

        val (major, minor) = config.version.parseVersion()
        if (major <= 20 && minor <= 4) return@with
        tasks.register("convert", ConversionTask::class.java, type)
        tasks.named("compileJava") { it.dependsOn("convert") }
        rootProject.tasks.named("prepareKotlinBuildScriptModel") { it.dependsOn("$path:convert") }
    }

    @DisableCachingByDefault
    abstract class ConversionTask @Inject constructor(type: Type) : DefaultTask() {
        private val pattern: Regex
        private val replacement: String

        init {
            group = "nms"
            inputs.property("type", type)
            val java = project.extensions.findByType(JavaPluginExtension::class.java) ?: throw GradleException("Java plugin not found")
            val source = java.sourceSets.named("main").map { it.allJava }
            inputs.files(source)
            outputs.files(source)

            if (type == Type.USER_DEV) {
                pattern = "org\\.bukkit\\.craftbukkit\\.${project.name}".toRegex()
                replacement = "org.bukkit.craftbukkit"
            } else {
                pattern = "org\\.bukkit\\.craftbukkit\\.(?!${project.name})".toRegex()
                replacement = "org.bukkit.craftbukkit.${project.name}."
            }
        }

        @TaskAction
        fun process() {
            val dispatcher = Dispatchers.IO.limitedParallelism(16)
            runBlocking {
                for (file in inputs.files) {
                    if (file.extension !in listOf("java"))
                        continue

                    launch(dispatcher) {
                        val output = ArrayList<String>()
                        var changed = false

                        file.bufferedReader().use {
                            for (line in it.lines()) {
                                if (line.startsWith("package") || line.isBlank()) {
                                    output += line
                                    continue
                                }

                                if (!line.startsWith("import")) {
                                    if (!changed) return@launch
                                    else {
                                        output += line
                                        continue
                                    }
                                }

                                if (!line.contains(pattern)) {
                                    output += line
                                    continue
                                }

                                output += line.replace(pattern, replacement)
                                changed = true
                            }
                        }

                        if (changed) {
                            RandomAccessFile(file, "r").use { raf ->
                                val bytes = ByteArray(NEW_LINE_BYTES.size)
                                raf.seek(raf.length() - bytes.size)
                                raf.readFully(bytes)
                                if (bytes.contentEquals(NEW_LINE_BYTES))
                                    output += ""
                            }

                            file.writer().use {
                                val iterator = output.iterator()
                                while (iterator.hasNext()) {
                                    it.append(iterator.next())
                                    if (iterator.hasNext())
                                        it.append(NEW_LINE)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    enum class Type {
        USER_DEV,
        BUILD_TOOLS,
        DIRECT,
    }
}

private val NEW_LINE = System.lineSeparator()
private val NEW_LINE_BYTES = NEW_LINE.encodeToByteArray()
private fun String.parseVersion() = substringBefore('-').split(".").let {
    it[1].toInt() to it[2].toInt()
}

class Config(
    var jvm: Int = 21,
    var type: Type = Type.DIRECT
) {
    lateinit var version: String
}

fun Project.nmsBinding(action: Config.() -> Unit) {
    extra["nms"] = Config().apply(action)
    plugins.apply(NMSBinding::class.java)
}

private inline fun <reified T : Named> ObjectFactory.named(name: String): T = named(T::class.java, name)