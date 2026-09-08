package art.arcane.iris.core.pregenerator;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.LogLevel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class PregenDiagnosticsTest {
    private final List<String> emitted = new ArrayList<>();
    private final List<LogLevel> levels = new ArrayList<>();
    private IrisPlatform previousPlatform;

    @Before
    public void captureLog() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        emitted.clear();
        levels.clear();
        IrisPlatform platform = mock(IrisPlatform.class);
        doAnswer(invocation -> {
            levels.add(invocation.getArgument(0, LogLevel.class));
            emitted.add(invocation.getArgument(1, String.class));
            return null;
        }).when(platform).log(ArgumentMatchers.any(LogLevel.class), ArgumentMatchers.anyString());
        IrisPlatforms.bind(platform);
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void aFailedProbeReportsItsNameClassAndMessageAtDebug() {
        PregenDiagnostics.probeFailed("moonrise worker pool size", new IllegalStateException("no such field"));

        assertEquals(1, emitted.size());
        assertEquals(LogLevel.DEBUG, levels.get(0));
        assertTrue(emitted.get(0), emitted.get(0).contains("moonrise worker pool size"));
        assertTrue(emitted.get(0), emitted.get(0).contains(IllegalStateException.class.getName()));
        assertTrue(emitted.get(0), emitted.get(0).contains("no such field"));
    }

    @Test
    public void aThrowableWithNoMessageStillReportsItsClass() {
        PregenDiagnostics.probeFailed("engine access", new NoClassDefFoundError());

        assertEquals(1, emitted.size());
        assertTrue(emitted.get(0), emitted.get(0).contains(NoClassDefFoundError.class.getName()));
        assertTrue(emitted.get(0), emitted.get(0).contains("engine access"));
    }

    @Test
    public void aMissingProbeNameAndThrowableStillProduceAReadableLine() {
        PregenDiagnostics.probeFailed(null, null);

        assertEquals(1, emitted.size());
        assertTrue(emitted.get(0), emitted.get(0).contains("unnamed"));
        assertTrue(emitted.get(0), emitted.get(0).contains("no throwable reported"));
    }

    @Test
    public void percentSignsInAProbeNameAreNotTreatedAsFormatSpecifiers() {
        PregenDiagnostics.probeFailed("heap at 95% headroom", new IllegalArgumentException("bad %s value"));

        assertEquals(1, emitted.size());
        assertTrue(emitted.get(0), emitted.get(0).contains("heap at 95% headroom"));
        assertTrue(emitted.get(0), emitted.get(0).contains("bad %s value"));
    }
}
