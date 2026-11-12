package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.core.modules.CarveTestTerrain;
import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.core.modules.InMemoryMantle;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.modules.carve.Cave;
import art.arcane.iris.modules.carve.CaveList;
import art.arcane.volmlib.util.matter.MatterCavern;
import com.volmit.ModuleLog;
import com.volmit.config.Level;
import com.volmit.resource.ResourceId;
import com.volmit.world.BiomeSample;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CaveCarvingTest {
    private static final int HEIGHT = 128;
    private static final int SURFACE = 100;
    private static final int FLUID_HEIGHT = 30;
    private static final int LAVA_HEIGHT = 8;
    private static final int ROCK_KEPT_UNDER_SURFACE = 8;
    private static final ResourceId REGION = CarveTestWorld.id("region");
    private static final ResourceId PLAINS = CarveTestWorld.id("plains");
    private static final ResourceId LUSH = CarveTestWorld.id("cave/lush");

    private InMemoryMantle mantle;
    private CarveTestWorld world;
    private ModuleLog log;

    @Before
    public void setUp() {
        world = new CarveTestWorld().biome("cave/lush").biome("plains").cave("rooms", rooms(0, 127)).cave("low", rooms(-50, -30));
        log = mock(ModuleLog.class);
    }

    @Test
    public void nothingIsCarvedWhereNoLevelListsACave() {
        assertEquals(0, carvedBlocks(sample -> PLAINS).size());
    }

    @Test
    public void aCaveTheDimensionListsCarvesEveryColumn() {
        world.dimension(new CaveList(List.of("rooms")));

        assertTrue(carvedBlocks(sample -> PLAINS).size() > 0);
    }

    @Test
    public void theListsOfADimensionAndARegionAddUp() {
        world.dimension(new CaveList(List.of("rooms")));
        Set<Long> dimensionOnly = carvedBlocks(sample -> PLAINS);
        world.declare(Level.REGION, "region", new CaveList(List.of("low")));

        Set<Long> both = carvedBlocks(sample -> PLAINS);

        assertTrue("the region's cave carves on top", both.size() > dimensionOnly.size());
        assertTrue("the dimension's cave still carves all it did", both.containsAll(dimensionOnly));
    }

    @Test
    public void theCaveBiomeOfAColumnListsCavesToo() {
        world.declare(Level.BIOME, "cave/lush", new CaveList(List.of("rooms")));

        assertEquals("the surface biome lists none", 0, carvedBlocks(sample -> PLAINS).size());
        assertTrue(carvedBlocks(sample -> LUSH).size() > 0);
    }

    @Test
    public void aCaveStaysInItsDepthRangeAndUnderTheRockItKeeps() {
        world.dimension(new CaveList(List.of("low")));

        Set<Long> carved = carvedBlocks(sample -> PLAINS);

        assertFalse(carved.isEmpty());
        for (long block : carved) {
            int y = (int) (block & 0xFFF);
            assertTrue("y=" + y, y >= 14 && y <= 34);
        }
        world = new CarveTestWorld().biome("plains").cave("rooms", rooms(0, 127)).dimension(new CaveList(List.of("rooms")));
        for (long block : carvedBlocks(sample -> PLAINS)) {
            int y = (int) (block & 0xFFF);
            assertTrue("y=" + y, y <= SURFACE - ROCK_KEPT_UNDER_SURFACE);
        }
    }

    @Test
    public void aListedCaveThePackDoesNotHaveIsReportedAndCarvesNothing() {
        world.dimension(new CaveList(List.of("missing")));

        assertEquals(0, carvedBlocks(sample -> PLAINS).size());
        verify(log).warnOnce(anyString(), anyString());
    }

    /** Carves chunk 0, 0 with the cave biome of every block {@code caveBiomeOf} says, and answers the blocks of the chunk that became cave. */
    private Set<Long> carvedBlocks(Function<BiomeSample, ResourceId> caveBiomeOf) {
        mantle = new InMemoryMantle(HEIGHT / 16);
        CarveTestTerrain terrain = new CarveTestTerrain(0, 0, HEIGHT, FLUID_HEIGHT, (x, z) -> SURFACE).biomes((x, y, z) -> {
            BiomeSample sample = new BiomeSample(REGION, PLAINS, Optional.of(LUSH));
            return new BiomeSample(sample.region(), sample.surfaceBiome(), Optional.of(caveBiomeOf.apply(sample)));
        });
        CarveWorld carveWorld = new CarveWorld(world.resources().noise(), 1337L, HEIGHT, FLUID_HEIGHT, LAVA_HEIGHT, CarveTestWorld.MIN_HEIGHT);

        new CaveCarving(world, carveWorld, (x, z) -> false, log).carve(mantle.carved(), terrain, 0, 0);

        Set<Long> carved = new HashSet<>();
        mantle.chunk(0, 0).iterate(MatterCavern.class, (x, y, z, cavern) -> carved.add(((long) x << 24) | ((long) z << 12) | y));
        return carved;
    }

    /** Caverns that take up most of the rock between two world heights, so a chunk is sure to hold some. */
    private static Cave rooms(int minWorldY, int maxWorldY) {
        return TestSettings.of(Cave.class, "{\"cavern\": {\"size\": 24, \"height\": 12, \"coverage\": 0.5},"
                + " \"depth\": {\"min\": " + minWorldY + ", \"max\": " + maxWorldY + "}, \"fade\": 0, \"surfaceClearance\": " + ROCK_KEPT_UNDER_SURFACE + "}");
    }
}
