package art.arcane.iris.spi;

import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Severity is stated by the caller and honoured by every adapter, so the levels {@link IrisLogging} offers
 * decide what an operator sees in a WARN-level scan. The once helpers exist for conditions that are worth
 * exactly one warning and would otherwise repeat per block, per sample or per chunk.
 */
public class IrisLoggingTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private final List<LogLevel> levels = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private IrisPlatform capturingPlatform;
    private IrisPlatform previousPlatform;

    @Before
    public void captureLog() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        levels.clear();
        messages.clear();
        bindCapturingPlatform();
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    /**
     * A lifecycle line is not a warning, but it is what an operator reads logs/latest.log for. NOTICE is the
     * level adapters route to their own logger at INFO instead of to the console sender.
     */
    @Test
    public void noticeCarriesItsOwnLevel() {
        IrisLogging.notice("Engine init: %s", "world");

        assertEquals(List.of(LogLevel.NOTICE), levels);
        assertEquals("Engine init: world", messages.getFirst());
    }

    @Test
    public void debugFormatsArgumentsAtDebugLevel() {
        IrisLogging.debug("chunk=%d,%d", 3, 7);

        assertEquals(List.of(LogLevel.DEBUG), levels);
        assertEquals("chunk=3,7", messages.getFirst());
    }

    @Test
    public void contextualReportUsesThePlatformReporterWithoutADuplicateLogLine() {
        RuntimeException failure = new RuntimeException("broken");

        IrisLogging.reportError("Generation failed.", failure);

        verify(capturingPlatform).reportError("Generation failed.", failure);
        assertTrue(levels.isEmpty());
    }

    @Test
    public void defaultContextualReporterLogsTheFailureContext() {
        IrisPlatform platform = mock(IrisPlatform.class, CALLS_REAL_METHODS);

        platform.reportError("Generation failed.", null);

        verify(platform).log(LogLevel.ERROR, "Generation failed.");
        verify(platform).reportError((Throwable) null);
    }

    @Test
    public void warnOnceStatesTheConditionOnceAndDropsRepeatsToDebug() {
        String key = uniqueKey();

        assertTrue(IrisLogging.warnOnce(key, "Can't find block data for %s", "mod:block"));
        assertFalse(IrisLogging.warnOnce(key, "Can't find block data for %s", "mod:block"));
        assertFalse(IrisLogging.warnOnce(key, "Can't find block data for %s", "mod:block"));

        assertEquals(List.of(LogLevel.WARN, LogLevel.DEBUG, LogLevel.DEBUG), levels);
        assertTrue(messages.toString(), messages.stream().allMatch(m -> m.equals("Can't find block data for mod:block")));
    }

    @Test
    public void aDistinctKeyIsItsOwnWarning() {
        assertTrue(IrisLogging.warnOnce(uniqueKey(), "first"));
        assertTrue(IrisLogging.warnOnce(uniqueKey(), "second"));

        assertEquals(List.of(LogLevel.WARN, LogLevel.WARN), levels);
    }

    @Test
    public void errorOnceKeepsTheSameContract() {
        String key = uniqueKey();

        assertTrue(IrisLogging.errorOnce(key, "Atomic cache supplier failed"));
        assertFalse(IrisLogging.errorOnce(key, "Atomic cache supplier failed"));

        assertEquals(List.of(LogLevel.ERROR, LogLevel.DEBUG), levels);
    }

    /**
     * An operator fixes the pack and reloads. Holding the key for the life of the JVM would hide whether the
     * fix worked, so unbinding the platform - which is what a reload does - clears them.
     */
    @Test
    public void unbindingThePlatformClearsTheOnceKeys() {
        String key = uniqueKey();
        assertTrue(IrisLogging.warnOnce(key, "Empty Block Data for %s", "plains"));

        IrisPlatforms.unbind();
        levels.clear();
        bindCapturingPlatform();

        assertTrue(IrisLogging.warnOnce(key, "Empty Block Data for %s", "plains"));
        assertEquals(List.of(LogLevel.WARN), levels);
    }

    private void bindCapturingPlatform() {
        IrisPlatform platform = mock(IrisPlatform.class);
        capturingPlatform = platform;
        doAnswer(invocation -> {
            levels.add(invocation.getArgument(0, LogLevel.class));
            messages.add(invocation.getArgument(1, String.class));
            return null;
        }).when(platform).log(any(LogLevel.class), anyString());
        IrisPlatforms.bind(platform);
    }

    private static String uniqueKey() {
        return "iris-logging-test:" + UUID.randomUUID();
    }
}
