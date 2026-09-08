package art.arcane.iris.spi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * An optional API that is not there is not a failure, but a bare ignored catch makes it impossible to tell
 * that miss apart from a real breakage. The probe keeps the miss quiet at INFO and states the failure class
 * and message at DEBUG, so an operator running with debug on can see exactly what was not found.
 */
public class CapabilityProbeTest {
    private final List<LogLevel> levels = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
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

    @Test
    public void aFailedProbeNamesTheFailureClassAndMessageAtDebug() {
        String resolved = CapabilityProbe.attempt("world#getChunkAtAsync",
                () -> {
                    throw new NoSuchMethodException("getChunkAtAsync");
                },
                null);

        assertNull(resolved);
        assertEquals(List.of(LogLevel.DEBUG), levels);
        assertEquals("Capability probe \"world#getChunkAtAsync\" is unavailable: "
                + "java.lang.NoSuchMethodException: getChunkAtAsync", messages.getFirst());
    }

    @Test
    public void aFailedProbeReturnsTheUnavailableValue() {
        String resolved = CapabilityProbe.attempt("optional", () -> {
            throw new NoClassDefFoundError("net/example/Missing");
        }, "absent");

        assertEquals("absent", resolved);
    }

    @Test
    public void aMessagelessFailureStillNamesItsClass() {
        CapabilityProbe.attempt("optional", () -> {
            throw new IllegalStateException();
        }, null);

        assertEquals("Capability probe \"optional\" is unavailable: java.lang.IllegalStateException",
                messages.getFirst());
    }

    @Test
    public void aSuccessfulProbeReturnsItsValueWithoutLogging() {
        String resolved = CapabilityProbe.attempt("present", () -> "value", null);

        assertEquals("value", resolved);
        assertTrue(messages.toString(), messages.isEmpty());
    }

    @Test
    public void anActionProbeReportsWhetherItCompleted() {
        assertTrue(CapabilityProbe.succeeds("action", () -> {
        }));
        assertTrue(messages.toString(), messages.isEmpty());

        assertFalse(CapabilityProbe.succeeds("action", () -> {
            throw new LinkageError("bad class");
        }));
        assertEquals("Capability probe \"action\" is unavailable: java.lang.LinkageError: bad class",
                messages.getFirst());
    }

    /**
     * Swallowing an InterruptedException also swallows the interrupt, which strands whatever asked the
     * thread to stop. The probe hands the flag back.
     */
    @Test
    public void anInterruptedProbeRestoresTheInterruptFlag() {
        try {
            CapabilityProbe.attempt("interruptible", () -> {
                throw new InterruptedException("stop");
            }, null);

            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void anUnnamedCapabilityStillProducesAReadableLine() {
        CapabilityProbe.attempt(null, () -> {
            throw new IllegalArgumentException("nope");
        }, null);

        assertEquals("Capability probe \"unnamed\" is unavailable: java.lang.IllegalArgumentException: nope",
                messages.getFirst());
    }

    private void bindCapturingPlatform() {
        IrisPlatform platform = mock(IrisPlatform.class);
        doAnswer(invocation -> {
            levels.add(invocation.getArgument(0, LogLevel.class));
            messages.add(invocation.getArgument(1, String.class));
            return null;
        }).when(platform).log(any(LogLevel.class), anyString());
        IrisPlatforms.bind(platform);
    }
}
