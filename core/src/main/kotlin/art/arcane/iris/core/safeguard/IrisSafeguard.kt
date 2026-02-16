package art.arcane.iris.core.safeguard

import art.arcane.iris.Iris
import art.arcane.iris.core.safeguard.task.Diagnostic
import art.arcane.iris.core.safeguard.task.Task
import art.arcane.iris.core.safeguard.task.ValueWithDiagnostics
import art.arcane.iris.core.safeguard.task.tasks
import art.arcane.iris.util.format.C
import art.arcane.iris.util.scheduling.J
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
        Iris.warn(C.GRAY.toString() + "Some startup checks need attention. Review the messages above for tuning suggestions.")
        Iris.warn(C.GRAY.toString() + "Iris will continue startup normally.")
        Iris.warn("")
    }

    private fun unstable() {
        Iris.error(C.DARK_RED.toString() + "Iris is running in Danger Mode")
        Iris.error("")
        Iris.error(C.DARK_GRAY.toString() + "--==<" + C.RED + " IMPORTANT " + C.DARK_GRAY + ">==--")
        Iris.error("Critical startup checks failed. Iris will continue startup in 10 seconds.")
        Iris.error("Review and resolve the errors above as soon as possible.")
        J.sleep(10000L)
        Iris.info("")
    }
}
