package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EngineMaintenanceTest {
    @Test
    public void normalMaintenanceUsesIdleAgeWithoutAResidentPlateLimit() {
        EngineMaintenance.Plan plan = EngineMaintenance.plan(30, 0.50D, false);

        assertEquals(30_000L, plan.idleDurationMillis());
        assertFalse(plan.multicoreUnload());
        assertFalse(plan.heapPressure());
    }

    @Test
    public void negativeKeepAliveIsClampedToImmediateEligibility() {
        EngineMaintenance.Plan plan = EngineMaintenance.plan(-1, 0.50D, false);

        assertEquals(0L, plan.idleDurationMillis());
        assertFalse(plan.multicoreUnload());
        assertFalse(plan.heapPressure());
    }

    @Test
    public void risingHeapUsageGraduallyShortensRetention() {
        EngineMaintenance.Plan plan = EngineMaintenance.plan(30, 0.87D, false);

        assertEquals(15_000L, plan.idleDurationMillis());
        assertFalse(plan.multicoreUnload());
        assertFalse(plan.heapPressure());
    }

    @Test
    public void heapPressureRequestsImmediateParallelReclamation() {
        EngineMaintenance.Plan plan = EngineMaintenance.plan(30, 0.92D, false);

        assertEquals(0L, plan.idleDurationMillis());
        assertTrue(plan.multicoreUnload());
        assertTrue(plan.heapPressure());
    }

    @Test
    public void forcedMulticoreWriteDoesNotShortenNormalRetention() {
        EngineMaintenance.Plan plan = EngineMaintenance.plan(30, 0.50D, true);

        assertEquals(30_000L, plan.idleDurationMillis());
        assertTrue(plan.multicoreUnload());
        assertFalse(plan.heapPressure());
    }

    @Test
    public void studioDisablesOnlyRoutineMaintenance() {
        assertFalse(EngineMaintenance.shouldRunStudioMaintenance(true, false, false));
        assertTrue(EngineMaintenance.shouldRunStudioMaintenance(true, false, true));
        assertTrue(EngineMaintenance.shouldRunStudioMaintenance(true, true, false));
        assertTrue(EngineMaintenance.shouldRunStudioMaintenance(false, false, false));
    }

    @Test
    public void nestedMantleClosedFailureIsRecognized() {
        IllegalStateException cause = new IllegalStateException("Mantle is closed");
        RuntimeException failure = new RuntimeException("maintenance failed", cause);

        assertTrue(EngineMaintenance.isMantleClosed(failure));
        assertFalse(EngineMaintenance.isMantleClosed(new IllegalStateException("unrelated")));
    }

    @Test
    public void configuredParallelismOverridesHardwareSizing() {
        IrisSettings.IrisSettingsEngineSVC settings = new IrisSettings.IrisSettingsEngineSVC();
        settings.parallelism = 1;

        assertEquals(1, settings.getParallelism());
    }

    @Test
    public void configuredParallelismIsBoundedByHardware() {
        IrisSettings.IrisSettingsEngineSVC settings = new IrisSettings.IrisSettingsEngineSVC();
        settings.parallelism = Integer.MAX_VALUE;
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        int maximumParallelism = processors > Integer.MAX_VALUE / 2
                ? Integer.MAX_VALUE
                : processors * 2;

        assertEquals(maximumParallelism, settings.getParallelism());
    }

    @Test
    public void automaticParallelismScalesWithAvailableProcessors() {
        IrisSettings.IrisSettingsEngineSVC settings = new IrisSettings.IrisSettingsEngineSVC();
        settings.parallelism = 0;
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());

        assertEquals(Math.max(1, (int) Math.ceil(Math.sqrt(processors))), settings.getParallelism());
    }
}
