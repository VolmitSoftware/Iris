package art.arcane.iris.world.pregen;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.iris.generation.stream.CachedDoubleStream2D;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PregenPerformanceProfileTest {
    private IrisSettings previousSettings;
    private Engine engine;
    private IrisComplex complex;
    private CachedDoubleStream2D natural;
    private CachedDoubleStream2D raw;

    @Before
    public void setUp() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisSettings.get().getPerformance().setNoiseCacheSize(1_024);
        engine = mock(Engine.class);
        complex = mock(IrisComplex.class);
        natural = mock(CachedDoubleStream2D.class);
        raw = mock(CachedDoubleStream2D.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getNaturalHeightStream()).thenReturn(natural);
        when(complex.getRawHeightStream()).thenReturn(raw);
        when(complex.getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 47D));
        doThrow(new IllegalStateException("Immutable history cannot rebuild its runtime"))
                .when(engine).hotloadComplex();
    }

    @After
    public void tearDown() {
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void directEngineProfileResizesActualTerrainCachesWithoutRebuildingHistory() {
        PregenPerformanceProfile.apply(engine);

        assertEquals(4_096, IrisSettings.get().getPerformance().getNoiseCacheSize());
        verify(natural).setMaximumChunks(4_096);
        verify(raw).setMaximumChunks(4_096);
        verify(complex, never()).getHeightStream();
        verify(engine, never()).hotloadComplex();
    }

    @Test
    public void platformProfileDoesNotHotloadOrPublishAStudioActivation() {
        PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        when(generator.getEngine()).thenReturn(engine);

        PregenPerformanceProfile.applyToGenerator(generator);

        verify(natural).setMaximumChunks(4_096);
        verify(raw).setMaximumChunks(4_096);
        verify(generator, never()).hotloadComplexAsync(30L, TimeUnit.SECONDS);
        verify(engine, never()).hotloadComplex();
    }

    @Test
    public void largerConfiguredCachesRemainLarger() {
        IrisSettings.get().getPerformance().setNoiseCacheSize(8_192);

        PregenPerformanceProfile.apply(engine);

        assertEquals(8_192, IrisSettings.get().getPerformance().getNoiseCacheSize());
        verify(natural).setMaximumChunks(8_192);
        verify(raw).setMaximumChunks(8_192);
    }

    @Test
    public void profileBeforeWorldCreationStillAppliesGlobalTuning() {
        PregenPerformanceProfile.applyToGenerator(null);

        assertEquals(4_096, IrisSettings.get().getPerformance().getNoiseCacheSize());
    }

    @Test
    public void pregenerationWantsTwoBurstWorkersPerCore() {
        assertEquals(32, PregenPerformanceProfile.pregenBurstParallelism(16));
        assertEquals(4, PregenPerformanceProfile.pregenBurstParallelism(1));
    }

    @Test
    public void raisingBurstParallelismOnlyGrowsThePool() {
        int before = MultiBurst.burst.parallelism();
        assertFalse(MultiBurst.burst.raiseParallelism(before));
        assertTrue(MultiBurst.burst.raiseParallelism(before + 1));
        assertEquals(before + 1, MultiBurst.burst.parallelism());
        assertFalse(MultiBurst.burst.raiseParallelism(before));
    }

    @Test
    public void restoringBurstParallelismReturnsThePoolToItsPrePregenerationSize() {
        int before = MultiBurst.ioBurst.parallelism();
        assertTrue(MultiBurst.ioBurst.raiseParallelism(before + 3));
        assertEquals(before + 3, MultiBurst.ioBurst.parallelism());

        assertTrue(MultiBurst.ioBurst.restoreParallelism());
        assertEquals(before, MultiBurst.ioBurst.parallelism());
        assertFalse(MultiBurst.ioBurst.restoreParallelism());
        assertEquals(before, MultiBurst.ioBurst.parallelism());
    }

    @Test
    public void aSecondRaiseKeepsTheOriginalBaselineSoOneRestoreIsEnough() {
        int before = MultiBurst.ioBurst.parallelism();
        assertTrue(MultiBurst.ioBurst.raiseParallelism(before + 2));
        assertTrue(MultiBurst.ioBurst.raiseParallelism(before + 5));

        assertTrue(MultiBurst.ioBurst.restoreParallelism());
        assertEquals(before, MultiBurst.ioBurst.parallelism());
    }

    @Test
    public void theBurstParallelismHoldIsReleasedByAMatchingRestore() {
        while (PregenPerformanceProfile.parallelismHolders() > 0) {
            PregenPerformanceProfile.restore();
        }
        assertFalse(PregenPerformanceProfile.restore());
        assertEquals(0, PregenPerformanceProfile.parallelismHolders());

        PregenPerformanceProfile.apply();
        int holders = PregenPerformanceProfile.parallelismHolders();
        PregenPerformanceProfile.restore();

        assertEquals(Math.max(0, holders - 1), PregenPerformanceProfile.parallelismHolders());
    }
}
