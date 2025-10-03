package com.volmit.iris.core.safeguard

import com.volmit.iris.Iris
import com.volmit.iris.core.IrisSettings
import com.volmit.iris.core.safeguard.task.Diagnostic
import com.volmit.iris.core.safeguard.task.Task
import com.volmit.iris.core.safeguard.task.ValueWithDiagnostics
import com.volmit.iris.core.safeguard.task.tasks
import com.volmit.iris.util.format.C
import com.volmit.iris.util.scheduling.J
import org.bukkit.Bukkit
import java.util.*

object IrisSafeguard {
    @Volatile
    private var forceShutdown = false
    private var results: Map<Task, ValueWithDiagnostics<Mode>> = emptyMap()
    private var context: Map<String, String> = emptyMap()
    private var attachment: Map<String, List<String>> = emptyMap()
    private var mode = Mode.STABLE
    private var count = 0

    @JvmStatic
    fun execute() {
        val results = LinkedHashMap<Task, ValueWithDiagnostics<Mode>>(tasks.size)
        val context = LinkedHashMap<String, String>(tasks.size)
        val attachment = LinkedHashMap<String, List<String>>(tasks.size)
        var mode = Mode.STABLE
        var count = 0
        for (task in tasks) {
            var result: ValueWithDiagnostics<Mode>
            try {
                result = task.run()
            } catch (e: Throwable) {
                Iris.reportError(e)
                result = ValueWithDiagnostics(
                    Mode.WARNING,
                    Diagnostic(Diagnostic.Logger.ERROR, "Error while running task ${task.id}", e)
                )
            }
            mode = mode.highest(result.value)
            results[task] = result
            context[task.id] = result.value.id
            attachment[task.id] = result.diagnostics.flatMap { it.toString().split('\n') }
            if (result.value != Mode.STABLE) count++
        }

        this.results = Collections.unmodifiableMap(results)
        this.context = Collections.unmodifiableMap(context)
        this.attachment = Collections.unmodifiableMap(attachment)
        this.mode = mode
        this.count = count
    }

    @JvmStatic
    fun mode() = mode

    @JvmStatic
    fun asContext() = context

    @JvmStatic
    fun asAttachment() = attachment

    @JvmStatic
    fun splash() {
        Iris.instance.splash()
        printReports()
        printFooter()
    }

    @JvmStatic
    fun printReports() {
        when (mode) {
            Mode.STABLE -> Iris.info(C.BLUE.toString() + "0 Conflicts found")
            Mode.WARNING -> Iris.warn(C.GOLD.toString() + "%s Issues found", count)
            Mode.UNSTABLE -> Iris.error(C.DARK_RED.toString() + "%s Issues found", count)
        }

        results.values.forEach { it.log(withStackTrace = true) }
    }

    @JvmStatic
    fun printFooter() {
        when (mode) {
            Mode.STABLE -> Iris.info(C.BLUE.toString() + "Iris is running Stable")
            Mode.WARNING -> warning()
            Mode.UNSTABLE -> unstable()
        }
    }

    @JvmStatic
    fun isForceShutdown() = forceShutdown

    private fun warning() {
        Iris.warn(C.GOLD.toString() + "Iris is running in Warning Mode")

        Iris.warn("")
        Iris.warn(C.DARK_GRAY.toString() + "--==<" + C.GOLD + " IMPORTANT " + C.DARK_GRAY + ">==--")
        Iris.warn(C.GOLD.toString() + "Iris is running in warning mode which may cause the following issues:")
        Iris.warn("- Data Loss")
        Iris.warn("- Errors")
        Iris.warn("- Broken worlds")
        Iris.warn("- Unexpected behavior.")
        Iris.warn("- And perhaps further complications.")
        Iris.warn("")
    }

    private fun unstable() {
        Iris.error(C.DARK_RED.toString() + "Iris is running in Unstable Mode")

        Iris.error("")
        Iris.error(C.DARK_GRAY.toString() + "--==<" + C.RED + " IMPORTANT " + C.DARK_GRAY + ">==--")
        Iris.error("Iris is running in unstable mode which may cause the following issues:")
        Iris.error(C.DARK_RED.toString() + "Server Issues")
        Iris.error("- Server won't boot")
        Iris.error("- Data Loss")
        Iris.error("- Unexpected behavior.")
        Iris.error("- And More...")
        Iris.error(C.DARK_RED.toString() + "World Issues")
        Iris.error("- Worlds can't load due to corruption.")
        Iris.error("- Worlds may slowly corrupt until they can't load.")
        Iris.error("- World data loss.")
        Iris.error("- And More...")
        Iris.error(C.DARK_RED.toString() + "ATTENTION: " + C.RED + "While running Iris in unstable mode, you won't be eligible for support.")

        if (IrisSettings.get().general.isDoomsdayAnnihilationSelfDestructMode) {
            Iris.error(C.DARK_RED.toString() + "Boot Unstable is set to true, continuing with the startup process in 10 seconds.")
            J.sleep(10000L)
        } else {
            Iris.error(C.DARK_RED.toString() + "Go to plugins/iris/settings.json and set DoomsdayAnnihilationSelfDestructMode to true if you wish to proceed.")
            Iris.error(C.DARK_RED.toString() + "The server will shutdown in 10 seconds.")
            J.sleep(10000L)
            Iris.error(C.DARK_RED.toString() + "Shutting down server.")
            forceShutdown = true
            try {
                Bukkit.getPluginManager().disablePlugins()
            } finally {
                Runtime.getRuntime().halt(42)
            }
        }
        Iris.info("")
    }
}