package art.arcane.iris.platform.bukkit;

import art.arcane.iris.spi.LogLevel;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BukkitPlatformErrorReportingTest {
    @After
    public void releaseHost() {
        BukkitPlatform.releaseHost();
    }

    @Test
    public void contextualReportEmitsOneCompleteTraceThroughTheHost() {
        ArrayList<LogEntry> messages = new ArrayList<>();
        ArrayList<Throwable> reported = new ArrayList<>();
        BukkitPlatform.hostBridge(new BukkitPlatform.HostBridge(
                (level, message) -> messages.add(new LogEntry(level, message)), null,
                failure -> {
                    reported.add(failure);
                    failure.printStackTrace(System.err);
                }, null, null, null, null, null, null));
        IllegalStateException failure = new IllegalStateException("raster failure",
                new ArrayIndexOutOfBoundsException("invalid sample"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previousError = System.err;
        try (PrintStream errors = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setErr(errors);

            new BukkitPlatform().reportError("Generation failed", failure);
        } finally {
            System.setErr(previousError);
        }

        assertEquals(List.of(new LogEntry(LogLevel.ERROR, "Generation failed")), messages);
        assertEquals(List.of(failure), reported);
        String trace = output.toString(StandardCharsets.UTF_8);
        assertEquals(1L, trace.lines().filter(line -> line.contains("raster failure")).count());
        assertEquals(1L, trace.lines().filter(line -> line.contains("Caused by:")).count());
        assertTrue(trace.contains("BukkitPlatformErrorReportingTest.contextualReportEmitsOneCompleteTraceThroughTheHost"));
    }

    private record LogEntry(LogLevel level, String message) {
    }
}
