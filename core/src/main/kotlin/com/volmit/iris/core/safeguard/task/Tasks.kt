package com.volmit.iris.core.safeguard.task

import com.volmit.iris.Iris
import com.volmit.iris.core.IrisWorlds
import com.volmit.iris.core.nms.INMS
import com.volmit.iris.core.nms.v1X.NMSBinding1X
import com.volmit.iris.core.safeguard.Mode
import com.volmit.iris.core.safeguard.Mode.*
import com.volmit.iris.core.safeguard.task.Diagnostic.Logger.*
import com.volmit.iris.core.safeguard.task.Task.Companion.of
import com.volmit.iris.util.agent.Agent
import com.volmit.iris.util.misc.getHardware
import org.bukkit.Bukkit
import java.util.stream.Collectors
import javax.tools.ToolProvider
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

private val memory by task {
    val mem = getHardware.getProcessMemory()
    if (mem >= 5999) STABLE.withDiagnostics()
    else STABLE.withDiagnostics(
        WARN.create("Low Memory"),
        WARN.create("- 6GB+ Ram is recommended"),
        WARN.create("- Process Memory: $mem MB")
    )
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
        "purpur",
        "pufferfish",
        "paper",
        "spigot",
        "bukkit"
    )

    if (supported.any { server.name.contains(it, true) }) STABLE.withDiagnostics()
    else WARNING.withDiagnostics(
        WARN.create("Unsupported Server Software"),
        WARN.create("- Please consider using Paper or Purpur instead.")
    )
}

private val version by task {
    val parts = Iris.instance.description.version.split('-')
    val minVersion = parts[1]
    val maxVersion = parts[2]

    if (INMS.get() !is NMSBinding1X) STABLE.withDiagnostics()
    else UNSTABLE.withDiagnostics(
        ERROR.create("Server Version"),
        ERROR.create("- Iris only supports $minVersion > $maxVersion")
    )
}

private val injection by task {
    if (!Agent.install()) UNSTABLE.withDiagnostics(
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
    val jdk = runCatching { ToolProvider.getSystemJavaCompiler() }.getOrNull() != null
    if (version in setOf(21) && jdk) STABLE.withDiagnostics()
    else WARNING.withDiagnostics(
        WARN.create("Unsupported Java version"),
        WARN.create("- Please consider using JDK 21 Instead of ${if(jdk) "JDK" else "JRE"} $version")
    )
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
private fun <T> MutableList<T>.addAll(vararg values: T) = values.forEach(this::add)
fun task(action: () -> ValueWithDiagnostics<Mode>) = PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Task>> { _, _ ->
    ReadOnlyProperty { _, property -> of(property.name, action) }
}