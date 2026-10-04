package art.arcane.iris.generation.locator;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.subterrain.IrisSubterrainFamily;
import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.pack.value.IrisRange;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SubterrainLocatorTest {
    @Test
    public void wideDistanceSearchReturnsOwnedAbsolutePositionWithoutGenerating() {
        SubterrainPlanner planner = planner();
        Engine engine = engine(planner);
        SubterrainLocator.Result result = SubterrainLocator.nearest(engine,
                new SubterrainLocator.Query("basin", null, "wet-cave"), 100_000, -24, -100_000, 8192).orElseThrow();
        SubterrainCell sampled = planner.sample(result.x(), result.y(), result.z());
        assertTrue(sampled.occupied());
        assertEquals(result.featureId(), sampled.room().featureId());
        assertEquals("wet-cave", result.biome());
        assertTrue(result.y() >= -48 && result.y() <= 48);
        verify(engine).getComplex();
        verifyNoMoreInteractions(engine);
    }

    @Test
    public void filtersAndDistanceBoundsRejectUnavailableOwnership() {
        Engine engine = engine(planner());
        assertFalse(SubterrainLocator.nearest(engine,
                new SubterrainLocator.Query("missing", null, ""), 0, 0, 0, 1024).isPresent());
        assertFalse(SubterrainLocator.nearest(engine,
                new SubterrainLocator.Query("", IrisSubterrainFamily.LAVA_TUBE, ""), 0, 0, 0, 1024).isPresent());
        assertFalse(SubterrainLocator.nearest(engine,
                new SubterrainLocator.Query("", null, "missing"), 0, 0, 0, 1024).isPresent());
        assertFalse(SubterrainLocator.nearest(engine,
                new SubterrainLocator.Query("", null, ""), 0, 10_000, 0, 1024).isPresent());
        assertFalse(SubterrainLocator.nearest(engine,
                new SubterrainLocator.Query("", null, ""), 0, 0, 0, 1024, ignored -> false).isPresent());
    }

    @Test
    public void repeatedSearchAndLargeNegativeCoordinatesAreStable() {
        Engine engine = engine(planner());
        SubterrainLocator.Query query = new SubterrainLocator.Query("", null, "wet-cave");
        assertEquals(SubterrainLocator.nearest(engine, query, -1_500_000, -10, -2_000_000, 4096),
                SubterrainLocator.nearest(engine, query, -1_500_000, -10, -2_000_000, 4096));
    }

    private static Engine engine(SubterrainPlanner planner) {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getSubterrainPlanner()).thenReturn(planner);
        return engine;
    }

    private static SubterrainPlanner planner() {
        IrisSubterrainFeature feature = new IrisSubterrainFeature().setId("basin").setBiome("wet-cave")
                .setProbability(1).setWorldYRange(new IrisRange(-48, 48));
        return new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 74119L, -64, 320));
    }
}
