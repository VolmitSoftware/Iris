package art.arcane.iris;

import art.arcane.iris.spi.LogLevel;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void adapterSideWarningsCarryTheSameSeverityAsCoreWarnings() throws Exception {
        String source = Files.readString(Path.of("src/main/java/art/arcane/iris/Iris.java")).replace("\r\n", "\n");

        String warn = method(source, "public static void warn(String format, Object... objs)");
        assertTrue(warn, warn.contains("diagnostic(Level.WARNING"));
        assertFalse(warn, warn.contains("msg("));

        String error = method(source, "public static void error(String format, Object... objs)");
        assertTrue(error, error.contains("diagnostic(Level.SEVERE"));
        assertFalse(error, error.contains("msg("));
    }

    @Test
    public void theCoreLogBridgeRoutesDiagnosticsToThePluginLogger() throws Exception {
        String source = Files.readString(Path.of("src/main/java/art/arcane/iris/Iris.java")).replace("\r\n", "\n");
        String bridge = method(source, "private static void bridgeLog(LogLevel level, String message)");

        assertTrue(bridge, bridge.contains("diagnosticLevel(target)"));
        assertTrue(bridge, bridge.contains("diagnostic(diagnostic, message)"));
        String diagnostic = method(source, "private static void diagnostic(Level level, String message)");
        assertTrue(diagnostic, diagnostic.contains("ComponentLog.log("));
        assertTrue(diagnostic, diagnostic.contains("ComponentText.section(message)"));
    }

    @Test
    public void informationalMessagesUseTheSharedComponentLogger() throws Exception {
        String source = Files.readString(Path.of("src/main/java/art/arcane/iris/Iris.java")).replace("\r\n", "\n");
        String message = method(source, "public static void msg(String string)");

        assertTrue(message, message.contains("ComponentLog.logMarkup("));
        assertTrue(message, message.contains("logPrefix(plugin)"));
        assertFalse(message, message.contains("getSender().sendMessage"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("method not found: " + signature, start >= 0);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }
        throw new AssertionError("unterminated method: " + signature);
    }
}
