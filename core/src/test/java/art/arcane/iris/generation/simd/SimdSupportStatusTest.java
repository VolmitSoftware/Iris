package art.arcane.iris.generation.simd;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.LogLevel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class SimdSupportStatusTest {
    private final List<String> emitted = new ArrayList<>();
    private IrisPlatform previousPlatform;
    private IrisSettings previousSettings;

    @Before
    public void captureLog() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        emitted.clear();
        IrisPlatform platform = mock(IrisPlatform.class);
        doAnswer(invocation -> {
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
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void anInitializationFailureIsNamedInsteadOfBlamingTheSetting() {
        String status = SimdSupport.kernelStatus(false, "scalar", true,
                new ExceptionInInitializerError(new UnsupportedOperationException("no vector shape")), true);

        assertFalse(status, status.contains("performance.simdKernels=false"));
        assertTrue(status, status.contains(UnsupportedOperationException.class.getName()));
        assertTrue(status, status.contains("no vector shape"));
    }

    @Test
    public void anInitializationFailureOutranksADisabledSettingBecauseADisabledSettingNeverAttempts() {
        String status = SimdSupport.kernelStatus(false, "scalar", true, new LinkageError("bad class"), false);

        assertTrue(status, status.contains("bad class"));
        assertFalse(status, status.contains("performance.simdKernels=false"));
    }

    @Test
    public void aDisabledSettingIsReportedOnlyWhenNothingFailed() {
        String status = SimdSupport.kernelStatus(false, "scalar", true, null, false);

        assertTrue(status, status.contains("performance.simdKernels=false"));
    }

    @Test
    public void anAbsentVectorModuleIsReportedWithTheJvmFlagThatFixesIt() {
        String status = SimdSupport.kernelStatus(false, "scalar", false, null, true);

        assertTrue(status, status.contains("--add-modules jdk.incubator.vector"));
        assertFalse(status, status.contains("performance.simdKernels=false"));
    }

    @Test
    public void anEnabledVectorKernelReportsItsShapeAndNoRemedy() {
        String status = SimdSupport.kernelStatus(true, "4x64-bit lanes, S_256_BIT", true, null, true);

        assertTrue(status, status.contains("4x64-bit lanes, S_256_BIT"));
        assertFalse(status, status.contains("scalar kernels active"));
    }

    @Test
    public void aCpuWithoutAUsableVectorShapeIsReportedAsSuchRatherThanDisabled() {
        String status = SimdSupport.kernelStatus(false, "scalar", true, null, true);

        assertTrue(status, status.contains("no usable vector shape"));
        assertFalse(status, status.contains("performance.simdKernels=false"));
    }

    @Test
    public void installStatesTheLiveKernelSelection() {
        SimdSupport.install();

        assertTrue(emitted.toString(), emitted.size() >= 1);
        assertTrue(emitted.toString(), emitted.contains("SIMD: " + SimdSupport.kernelStatus()));
    }

    @Test
    public void theLiveStatusNeverBlamesTheSettingWhileSimdKernelsAreEnabled() {
        if (!IrisSettings.get().getPerformance().isSimdKernels()) {
            return;
        }

        assertFalse(SimdSupport.kernelStatus(), SimdSupport.kernelStatus().contains("performance.simdKernels=false"));
    }
}
