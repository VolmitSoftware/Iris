package art.arcane.iris;

import art.arcane.iris.configuration.IrisSettings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IrisErrorReportingTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void debugReportingPreservesStacklessFailures() throws Exception {
        ArrayIndexOutOfBoundsException failure = new ArrayIndexOutOfBoundsException("raster index");
        failure.setStackTrace(new StackTraceElement[0]);

        String output = reportWithDebug(failure);

        assertEquals(1L, output.lines().filter(line -> line.contains("raster index")).count());
    }

    @Test
    public void debugReportingPreservesFailureWithStacklessCause() throws Exception {
        ArrayIndexOutOfBoundsException cause = new ArrayIndexOutOfBoundsException("raster index");
        cause.setStackTrace(new StackTraceElement[0]);
        IllegalStateException failure = new IllegalStateException("Hydrology failed", cause);

        String output = reportWithDebug(failure);

        assertEquals(1L, output.lines().filter(line -> line.contains("Hydrology failed")).count());
        assertTrue(output.contains("Caused by: java.lang.ArrayIndexOutOfBoundsException: raster index"));
        assertTrue(output.contains("IrisErrorReportingTest.debugReportingPreservesFailureWithStacklessCause"));
    }

    private String reportWithDebug(Throwable failure) throws Exception {
        Iris previousPlugin = Iris.instance;
        IrisSettings previousSettings = IrisSettings.settings;
        PrintStream previousError = System.err;
        Iris plugin = mock(Iris.class);
        Field deferred = Iris.class.getDeclaredField("serverStopTeardownDeferred");
        deferred.setAccessible(true);
        deferred.set(plugin, new AtomicBoolean());
        File report = new File(temporaryFolder.getRoot(), "failure.txt");
        when(plugin.getDataFile(any(String[].class))).thenReturn(report);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream errors = new PrintStream(output, true, StandardCharsets.UTF_8);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            Iris.instance = plugin;
            IrisSettings.settings = new IrisSettings();
            IrisSettings.settings.getGeneral().setDebug(true);
            System.setErr(errors);
            iris.when(() -> Iris.reportError(failure)).thenCallRealMethod();

            Iris.reportError(failure);

            assertTrue(Files.readString(report.toPath()).contains(failure.toString()));
            return output.toString(StandardCharsets.UTF_8);
        } finally {
            System.setErr(previousError);
            IrisSettings.settings = previousSettings;
            Iris.instance = previousPlugin;
        }
    }
}
