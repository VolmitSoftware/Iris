package art.arcane.iris.engine;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The pack-compat summary is the line that tells an operator a pack cannot generate on the running
 * Minecraft version. It must never take a working world down, but a failure to produce it used to leave
 * only a debug line with no class and no trace, so the missing summary looked like "the pack is fine".
 */
public class EngineDiagnosticsFailureTest {
    private IrisPlatform capturingPlatform;
    private IrisPlatform previousPlatform;

    @Before
    public void captureReports() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        capturingPlatform = mock(IrisPlatform.class);
        IrisPlatforms.bind(capturingPlatform);
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void aFailedCompatSummaryIsReportedWithItsCauseAndDoesNotThrow() {
        IrisEngine engine = mock(IrisEngine.class);
        IllegalStateException failure = new IllegalStateException("engine data unavailable");
        when(engine.getData()).thenThrow(failure);

        new EngineDiagnostics(engine).logPackCompatSummary();

        ArgumentCaptor<Throwable> reported = ArgumentCaptor.forClass(Throwable.class);
        verify(capturingPlatform).reportError(anyString(), reported.capture());
        assertEquals(failure, reported.getValue());
    }
}
