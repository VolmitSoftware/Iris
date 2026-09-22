package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalCacheTest {
    @Test
    public void clearingTilesRefreshesChangedRegionalTerrain() throws Exception {
        AtomicInteger height = new AtomicInteger(70);
        AtomicInteger reads = new AtomicInteger();
        HydrologyTerrainSampler terrain = (x, z) -> {
            reads.incrementAndGet();
            return HydrologyTerrainSample.openLand(height.get(), 0D, "land");
        };
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        HydrologyRegionalTerrain refiner = refiner(planner);
        try (HydrologyTileCache cache = new HydrologyTileCache(planner)) {
            assertEquals(70, refiner.sample(0, 0).naturalHeight());
            height.set(85);
            cache.clear();

            assertEquals(85, refiner.sample(0, 0).naturalHeight());
            assertEquals(2, reads.get());
        }
    }

    @Test
    public void clearingTilesRetriesPreviouslyUnavailableRegionalTerrain() throws Exception {
        AtomicBoolean available = new AtomicBoolean();
        AtomicInteger reads = new AtomicInteger();
        HydrologyTerrainSampler terrain = (x, z) -> {
            reads.incrementAndGet();
            return available.get() ? HydrologyTerrainSample.openLand(70, 0D, "land") : null;
        };
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        HydrologyRegionalTerrain refiner = refiner(planner);
        try (HydrologyTileCache cache = new HydrologyTileCache(planner)) {
            assertNull(refiner.sample(0, 0));
            available.set(true);
            cache.clear();

            assertNotNull(refiner.sample(0, 0));
            assertEquals(2, reads.get());
        }
    }

    private static HydrologyRegionalTerrain refiner(HydrologyPlanner planner) throws Exception {
        Field routes = HydrologyRegionalPlanner.class.getDeclaredField("routes");
        routes.setAccessible(true);
        Field refiner = HydrologyRegionalRoute.class.getDeclaredField("terrain");
        refiner.setAccessible(true);
        return (HydrologyRegionalTerrain) refiner.get(routes.get(planner.regional));
    }
}
