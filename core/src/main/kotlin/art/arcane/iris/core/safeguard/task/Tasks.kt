package art.arcane.iris.core.safeguard.task

import art.arcane.iris.Iris
import art.arcane.iris.core.IrisWorlds
import art.arcane.iris.core.nms.INMS
import art.arcane.iris.core.nms.v1X.NMSBinding1X
import art.arcane.iris.core.safeguard.Mode
import art.arcane.iris.core.safeguard.Mode.*
import art.arcane.iris.core.safeguard.task.Diagnostic.Logger.*
import art.arcane.iris.core.safeguard.task.Task.Companion.of
import art.arcane.iris.util.project.agent.Agent
import art.arcane.iris.util.common.misc.getHardware
import org.bukkit.Bukkit
import java.util.Locale
import java.util.stream.Collectors
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

private val memory by task {
    val mem = getHardware.getProcessMemory()
    when {
        mem >= 3072 -> STABLE.withDiagnostics()
        mem > 2048 -> STABLE.withDiagnostics(
            INFO.create("Memory Recommendation"),
            INFO.create("- 3GB+ process memory is recommended for Iris."),
            INFO.create("- Process Memory: $mem MB")
        )
        else -> WARNING.withDiagnostics(
            WARN.create("Low Memory"),
            WARN.create("- Iris is running with 2GB or less process memory."),
            WARN.create("- 3GB+ process memory is recommended for Iris."),
            WARN.create("- Process Memory: $mem MB")
        )
    }
}

private val incompatibilities by task {
    val plugins = mutableSetOf("dynmap", "Stratos")
    plugins.removeIf { server.pluginManager.getPlugin(it) == null }

    if (plugins.isEmpty()) STABLE.withDiagnostics()
    else {
        val diagnostics = mutableListOf<Diagnostic>()
        if ("dynmap" in plugins) diagnostics.addAll(
            ERROR.create("Dynmap"),
            ERROR.create("- The plugin Dynmap is not compatible with the server."),
            ERROR.create("- If you want to have a map plugin like Dynmap, consider Bluemap.")
        )
        if ("Stratos" in plugins) diagnostics.addAll(
            ERROR.create("Stratos"),
            ERROR.create("- Iris is not compatible with other worldgen plugins.")
        )
        WARNING.withDiagnostics(diagnostics)
    }
}

private val software by task {
    val supported = setOf(
        "canvas",
        "folia",
        "purpur",
        "pufferfish",
        "paper",
        "spigot",
        "bukkit"
    )

    if (isCanvasServer() || supported.any { server.name.contains(it, true) }) STABLE.withDiagnostics()
    else WARNING.withDiagnostics(
        WARN.create("Unsupported Server Software"),
        WARN.create("- Please consider using Canvas, Folia, Paper, or Purpur instead.")
    )
}

private val version by task {
    val parts = Iris.instance.description.version.split('-')
    val minVersion = parts[1]
    val maxVersion = parts[2]
    val supportedVersions = if (minVersion == maxVersion) minVersion else "$minVersion - $maxVersion"

    if (INMS.get() !is NMSBinding1X) STABLE.withDiagnostics()
    else UNSTABLE.withDiagnostics(
        ERROR.create("Server Version"),
        ERROR.create("- Iris only supports $supportedVersions")
    )
}

private val injection by task {
    if (!isPaperPreferredServer() && !Agent.isInstalled()) {
        WARNING.withDiagnostics(
            WARN.create("Java Agent"),
            WARN.create("- Skipping dynamic Java agent attach on Spigot/Bukkit to avoid runtime agent warnings."),
            WARN.create("- For full runtime injection support, run with -javaagent:" + Agent.AGENT_JAR.path + " or use Canvas/Folia/Paper/Purpur.")
        )
    } else if (!Agent.install()) UNSTABLE.withDiagnostics(
        ERROR.create("Java Agent"),
        ERROR.create("- Please enable dynamic agent loading by adding -XX:+EnableDynamicAgentLoading to your jvm arguments."),
        ERROR.create("- or add the jvm argument -javaagent:" + Agent.AGENT_JAR.path)
    )
    else if (!INMS.get().injectBukkit()) UNSTABLE.withDiagnostics(
        ERROR.create("Code Injection"),
        ERROR.create("- Failed to inject code. Please contact support")
    )
    else STABLE.withDiagnostics()
}

private val dimensionTypes by task {
    val keys = IrisWorlds.get()
        .dimensions
        .map { it.dimensionTypeKey }
        .collect(Collectors.toSet())

    if (!INMS.get().missingDimensionTypes(*keys.toTypedArray())) STABLE.withDiagnostics()
    else UNSTABLE.withDiagnostics(
        ERROR.create("Dimension Types"),
        ERROR.create("- Required Iris dimension types were not loaded."),
        ERROR.create("- If this still happens after a restart please contact support.")
    )
}

private val diskSpace by task {
    if (server.worldContainer.freeSpace.toDouble().div(0x4000_0000) > 3) STABLE.withDiagnostics()
    else WARNING.withDiagnostics(
        WARN.create("Insufficient Disk Space"),
        WARN.create("- 3GB of free space is required for Iris to function.")
    )
}

private val java by task {
    val version = Iris.getJavaVersion()
    when {
        version == 21 -> STABLE.withDiagnostics()
        version > 21 -> STABLE.withDiagnostics(
            INFO.create("Java Runtime"),
            INFO.create("- Running Java $version. Iris is tested primarily on Java 21.")
        )
        else -> WARNING.withDiagnostics(
            WARN.create("Unsupported Java version"),
            WARN.create("- Java 21+ is recommended. Current runtime: Java $version")
        )
    }
}


val tasks = listOf(
    memory,
    incompatibilities,
    software,
    version,
    injection,
    dimensionTypes,
    diskSpace,
    java,
)

private val server get() = Bukkit.getServer()
private fun isPaperPreferredServer(): Boolean {
    val name = server.name.lowercase(Locale.ROOT)
    return isCanvasServer()
            || name.contains("folia")
            || name.contains("paper")
            || name.contains("purpur")
            || name.contains("pufferfish")
}
private fun isCanvasServer(): Boolean {
    val loader: ClassLoader? = server.javaClass.classLoader
    return try {
        Class.forName("io.canvasmc.canvas.region.WorldRegionizer", false, loader)
        true
    } catch (_: Throwable) {
        server.name.contains("canvas", true)
    }
}
private fun <T> MutableList<T>.addAll(vararg values: T) = values.forEach(this::add)
fun task(action: () -> ValueWithDiagnostics<Mode>) = PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Task>> { _, _ ->
    ReadOnlyProperty { _, property -> of(property.name, action) }
}
