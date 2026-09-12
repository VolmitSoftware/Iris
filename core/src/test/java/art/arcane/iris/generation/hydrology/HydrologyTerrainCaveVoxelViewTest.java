package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.CavePosition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxel;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologyTerrainCaveVoxelViewTest {
    @Test
    public void reusesOneTerrainSampleAcrossVoxelQueriesInTheSameColumn() {
        AtomicInteger samples = new AtomicInteger();
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            samples.incrementAndGet();
            return HydrologyTerrainSample.openLand(64, 0D, "land");
        };
        HydrologyTerrainCaveVoxelView view = new HydrologyTerrainCaveVoxelView(
                sampler,
                63,
                -64,
                320
        );

        assertEquals(CaveVoxel.SOLID, view.voxelAt(new CavePosition(5, 60, 9)));
        assertEquals(CaveVoxel.CAVE_AIR, view.voxelAt(new CavePosition(5, 70, 9)));
        assertFalse(view.isAboveTerrainSurface(new CavePosition(5, 64, 9)));
        assertTrue(view.isOpenToSurface(new CavePosition(5, 65, 9)));
        assertFalse(view.hasAboveTerrainSurface(5, 9, 50, 64));
        assertTrue(view.hasAboveTerrainSurface(5, 9, 50, 65));
        assertEquals(1, samples.get());

        assertEquals(CaveVoxel.SOLID, view.voxelAt(new CavePosition(6, 60, 9)));
        assertEquals(2, samples.get());
    }

    @Test
    public void sharedPlanningCacheReusesTerrainSamplesAcrossCaveViews() {
        AtomicInteger samples = new AtomicInteger();
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            samples.incrementAndGet();
            return HydrologyTerrainSample.openLand(64, 0D, "land");
        };
        Long2ObjectOpenHashMap<HydrologyTerrainSample> shared = new Long2ObjectOpenHashMap<>();
        HydrologyTerrainCaveVoxelView first = new HydrologyTerrainCaveVoxelView(
                sampler,
                63,
                -64,
                320
        ).withSampleCache(shared);
        HydrologyTerrainCaveVoxelView second = new HydrologyTerrainCaveVoxelView(
                sampler,
                63,
                -64,
                320
        ).withSampleCache(shared);

        assertTrue(first.isAboveTerrainSurface(new CavePosition(5, 65, 9)));
        assertEquals(CaveVoxel.SOLID, second.voxelAt(new CavePosition(5, 60, 9)));
        assertEquals(1, samples.get());
    }
}
