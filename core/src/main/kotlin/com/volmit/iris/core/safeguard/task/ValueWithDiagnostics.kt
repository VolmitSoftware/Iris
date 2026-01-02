package com.volmit.iris.core.safeguard.task

import com.volmit.iris.Iris
import com.volmit.iris.util.format.C
import java.io.ByteArrayOutputStream
import java.io.PrintStream

data class ValueWithDiagnostics<out T>(
    val value: T,
    val diagnostics: List<Diagnostic>
) {
    constructor(value: T, vararg diagnostics: Diagnostic) : this(value, diagnostics.toList())

    @JvmOverloads
    fun log(
        withException: Boolean = true,
        withStackTrace: Boolean = false
    ) {
        diagnostics.forEach { it.log(withException, withStackTrace) }
    }
}

data class Diagnostic @JvmOverloads constructor(
    val logger: Logger = Logger.ERROR,
    val message: String,
    val exception: Throwable? = null
) {

    enum class Logger(
        private val logger: (String) -> Unit
    ) {
        DEBUG(Iris::debug),
        RAW(Iris::msg),
        INFO(Iris::info),
        WARN(Iris::warn),
        ERROR(Iris::error);

        fun print(message: String) = message.split('\n').forEach(logger)
        fun create(message: String, exception: Throwable? = null) = Diagnostic(this, message, exception)
    }

    @JvmOverloads
    fun log(
        withException: Boolean = true,
        withStackTrace: Boolean = false
    ) {
        logger.print(render(withException, withStackTrace))
    }

    fun render(
        withException: Boolean = true,
        withStackTrace: Boolean = false
    ): String = buildString {
        append(message)
        if (withException && exception != null) {
            append(": ")
            append(exception)
            if (withStackTrace) {
                ByteArrayOutputStream().use { os ->
                    val ps = PrintStream(os)
                    exception.printStackTrace(ps)
                    ps.flush()
                    append("\n")
                    append(os.toString())
                }
            }
        }
    }

    override fun toString(): String = C.strip(render())
}

fun <T> T.withDiagnostics(vararg diagnostics: Diagnostic) = ValueWithDiagnostics(this, diagnostics.toList())
fun <T> T.withDiagnostics(diagnostics: List<Diagnostic>) = ValueWithDiagnostics(this, diagnostics)