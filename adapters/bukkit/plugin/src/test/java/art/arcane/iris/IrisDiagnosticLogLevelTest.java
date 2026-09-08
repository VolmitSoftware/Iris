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
        assertEquals(Level.WARNING, Iris.diagnosticLevel(LogLevel.WARN));
        assertEquals(Level.SEVERE, Iris.diagnosticLevel(LogLevel.ERROR));
    }

    @Test
    public void informationalAndDebugMessagesStayOnTheInformationalPath() {
        assertNull(Iris.diagnosticLevel(LogLevel.INFO));
        assertNull(Iris.diagnosticLevel(LogLevel.DEBUG));
    }

    @Test
    public void lifecycleNoticesReachThePluginLoggerAtInfo() {
        assertEquals(Level.INFO, Iris.diagnosticLevel(LogLevel.NOTICE));
    }
}
