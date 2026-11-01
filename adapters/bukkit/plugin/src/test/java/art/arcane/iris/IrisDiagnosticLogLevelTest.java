package art.arcane.iris;

import art.arcane.iris.spi.LogLevel;
import org.junit.Test;

import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Core states a severity on every message it logs and the modded adapters honour it. Bukkit must preserve
 * that severity through the shared component logger so diagnostics remain discoverable in logs/latest.log.
 */
public class IrisDiagnosticLogLevelTest {
    @Test
    public void coreDiagnosticsKeepTheirSeverity() {
        assertEquals(Level.WARNING, IrisRuntime.diagnosticLevel(LogLevel.WARN));
        assertEquals(Level.SEVERE, IrisRuntime.diagnosticLevel(LogLevel.ERROR));
    }

    @Test
    public void informationalAndDebugMessagesStayOnTheInformationalPath() {
        assertNull(IrisRuntime.diagnosticLevel(LogLevel.INFO));
        assertNull(IrisRuntime.diagnosticLevel(LogLevel.DEBUG));
    }

    @Test
    public void lifecycleNoticesReachThePluginLoggerAtInfo() {
        assertEquals(Level.INFO, IrisRuntime.diagnosticLevel(LogLevel.NOTICE));
    }
}
