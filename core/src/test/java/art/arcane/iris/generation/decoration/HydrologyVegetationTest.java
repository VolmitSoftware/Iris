package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlantBlockState;
import art.arcane.iris.testsupport.PlantPlatform;
import art.arcane.iris.testsupport.WaterScene;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class HydrologyVegetationTest {
    private static final int HEIGHT = 128;
    private static final int SEA = 20;
    private static final Set<String> KNOWN = Set.of("minecraft:air", "minecraft:stone", "minecraft:dirt", "minecraft:water", "minecraft:lava",
            "minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:kelp", "minecraft:kelp_plant", "minecraft:lily_pad",
            "minecraft:sugar_cane", "minecraft:fern", "minecraft:tall_grass", "minecraft:sea_pickle", "minecraft:tube_coral");
    private static final Set<String> OPEN = Set.of("minecraft:air", "minecraft:fern", "minecraft:tall_grass", "minecraft:short_grass");
    private static final Set<String> WATER_PLANTS = Set.of("minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:kelp", "minecraft:kelp_plant");
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final IrisData data = mock(IrisData.class);
    private PlantPlatform platform;

    @Before
    public void bindBlocks() {
        platform = PlantPlatform.bind(KNOWN);
    }

    @After
    public void restoreBlocks() {
        platform.close();
    }

    @Test
    public void floorStacksStopOneBlockBelowTheSurface() {
        World world = new World();
        for (int lx = 0; lx < 16; lx++) {
            int depth = lx % 9;
            for (int lz = 0; lz < 16; lz++) {
                world.wet(lx, lz, WaterRole.POND, 60 - depth, 60);
            }
        }
        world.everywhere(decorators(floor("minecraft:kelp_plant", "minecraft:kelp", 40)));

        Hunk<NativeBlockState> chunk = world.decorate(new HydrologyVegetation(7L, null), 0, 0);

        int planted = 0;
        for (int lx = 0; lx < 16; lx++) {
            int depth = lx % 9;
            for (int lz = 0; lz < 16; lz++) {
                assertEquals("surface cell of depth " + depth, depth == 0 ? "minecraft:dirt" : "minecraft:water", block(chunk, lx, 60, lz));
                for (int y = 61 - depth; y < 60; y++) {
                    String expected = depth < 2 ? "minecraft:water" : y == 59 ? "minecraft:kelp" : "minecraft:kelp_plant";
                    assertEquals("depth " + depth + " at y " + y, expected, block(chunk, lx, y, lz));
                }
                planted += depth >= 2 ? depth - 1 : 0;
            }
        }
        assertTrue(planted > 0);
    }

    @Test
    public void twoBlockFloorPlantsNeedRoomForBothHalvesBelowTheSurface() {
        World world = new World();
        for (int lz = 0; lz < 16; lz++) {
            world.wet(0, lz, WaterRole.CHANNEL, 58, 60);
            world.wet(1, lz, WaterRole.CHANNEL, 57, 60);
        }
        IrisDecorator tall = decorator(IrisDecorationPart.SEA_FLOOR, 1D, 1, 1, "minecraft:tall_seagrass[half=lower]");
        world.everywhere(decorators(tall));

        Hunk<NativeBlockState> chunk = world.decorate(new HydrologyVegetation(7L, null), 0, 0);

        for (int lz = 0; lz < 16; lz++) {
            assertEquals("minecraft:water", block(chunk, 0, 59, lz));
            assertEquals("minecraft:tall_seagrass", block(chunk, 1, 58, lz));
            assertEquals("lower", half(chunk, 1, 58, lz));
            assertEquals("minecraft:tall_seagrass", block(chunk, 1, 59, lz));
            assertEquals("upper", half(chunk, 1, 59, lz));
            assertEquals("minecraft:water", block(chunk, 1, 60, lz));
        }
    }

    @Test
    public void lilyPadsGrowOnlyOnStillWaterAwayFromFallsAndFlow() {
        World world = new World();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 4; lz <= 6; lz++) {
                world.wet(lx, lz, WaterRole.CHANNEL, 55, lx < 8 ? 62 : 60);
            }
            for (int lz = 10; lz <= 12; lz++) {
                world.wet(lx, lz, lx == 5 ? WaterRole.CASCADE : lx == 9 ? WaterRole.WATERFALL : WaterRole.POND, 55, 61);
            }
        }
        world.flowing(12, 11);
        world.everywhere(decorators(decorator(IrisDecorationPart.SEA_SURFACE, 1D, 1, 1, "minecraft:lily_pad")));

        Hunk<NativeBlockState> chunk = world.decorate(new HydrologyVegetation(7L, null), 0, 0);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 4; lz <= 6; lz++) {
                int head = lx < 8 ? 62 : 60;
                boolean step = lx == 7 || lx == 8;
                assertEquals(lx + "," + lz, step ? 0 : 1, lilyPads(chunk, lx, lz));
                if (!step) {
                    assertEquals(lx + "," + lz, "minecraft:lily_pad", block(chunk, lx, head + 1, lz));
                }
            }
            for (int lz = 10; lz <= 12; lz++) {
                boolean fall = lx == 5 || lx == 9;
                boolean flowing = lx == 12 && lz == 11;
                assertEquals(lx + "," + lz, fall || flowing ? 0 : 1, lilyPads(chunk, lx, lz));
                if (!fall && !flowing) {
                    assertEquals(lx + "," + lz, "minecraft:lily_pad", block(chunk, lx, 62, lz));
                }
            }
        }
        assertEquals("minecraft:water[level=8]", chunk.get(8, 61, 5).key());
        assertEquals("minecraft:water[level=8]", chunk.get(8, 62, 5).key());
    }

    @Test
    public void sugarCaneGrowsOnlyWhereItsGroundTouchesWater() {
        World world = new World();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int ring = Math.max(Math.abs(lx - 8), Math.abs(lz - 8));
                if (ring <= 3) {
                    world.wet(lx, lz, WaterRole.POND, 56, 60);
                } else if (ring == 4) {
                    world.dry(lx, lz, WaterRole.POND_SHORE, 60);
                } else if (ring <= 6) {
                    world.dry(lx, lz, WaterRole.BANK, 61);
                } else {
                    world.dry(lx, lz, WaterRole.SHORE, 61);
                }
            }
        }
        IrisDecorator cane = decorator(IrisDecorationPart.SHORE_LINE, 1D, 2, 2, "minecraft:sugar_cane");
        world.everywhere(decorators(cane));

        Hunk<NativeBlockState> chunk = world.decorate(new HydrologyVegetation(7L, null), 0, 0);

        int canes = 0;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int ground = world.ground(lx, lz);
                boolean planted = "minecraft:sugar_cane".equals(block(chunk, lx, ground + 1, lz));
                boolean touchesWater = false;
                for (int[] side : SIDES) {
                    int nx = lx + side[0];
                    int nz = lz + side[1];
                    touchesWater |= nx >= 0 && nx < 16 && nz >= 0 && nz < 16 && chunk.get(nx, ground, nz).isWater();
                }
                if (planted) {
                    canes++;
                    assertTrue(lx + "," + lz + " grows cane without water beside its ground", touchesWater);
                    assertEquals("minecraft:sugar_cane", block(chunk, lx, ground + 2, lz));
                }
                if (world.role(lx, lz) == WaterRole.POND_SHORE && touchesWater) {
                    assertTrue(lx + "," + lz + " shore beside water", planted);
                }
            }
        }
        assertEquals(28, canes);
    }

    @Test
    public void surfaceAndShorePlantsNeverPlaceBlocksThatHoldWater() {
        for (boolean swapped : new boolean[]{false, true}) {
            World world = new World();
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int ring = Math.max(Math.abs(lx - 8), Math.abs(lz - 8));
                    if (ring <= 4) {
                        world.wet(lx, lz, WaterRole.POND, 55, 60);
                    } else {
                        world.dry(lx, lz, WaterRole.BANK, 61);
                    }
                }
            }
            IrisDecorator surface = decorator(IrisDecorationPart.SEA_SURFACE, 1D, 1, 1, "minecraft:lily_pad");
            IrisDecorator shore = decorator(IrisDecorationPart.SHORE_LINE, 1D, 1, 1, "minecraft:fern");
            world.everywhere(decorators(surface, shore));
            if (swapped) {
                surface.setPalette(new KList<>(IrisBlockData.from("minecraft:seagrass")));
                shore.setPalette(new KList<>(IrisBlockData.from("minecraft:sea_pickle[waterlogged=true]")));
            }

            Hunk<NativeBlockState> chunk = world.decorate(new HydrologyVegetation(7L, null), 0, 0);

            Map<String, Integer> grown = new HashMap<>();
            countPlants(chunk, grown);
            if (swapped) {
                assertEquals(Map.of(), grown);
            } else {
                assertEquals(81, (int) grown.get("minecraft:lily_pad"));
                assertEquals(256 - 81, (int) grown.get("minecraft:fern"));
            }
        }
    }

    @Test
    public void decoratingKeepsNoVegetationViewOrChunkReachableAfterward() throws Exception {
        List<WeakReference<Object>> retired = decorateAndRetire();

        for (int attempt = 0; attempt < 100 && retired.stream().anyMatch(reference -> reference.get() != null); attempt++) {
            System.gc();
            Thread.sleep(10L);
        }

        for (WeakReference<Object> reference : retired) {
            assertNull(String.valueOf(reference.get()), reference.get());
        }
    }

    private List<WeakReference<Object>> decorateAndRetire() {
        World world = new World();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                world.wet(lx, lz, WaterRole.POND, 55, 60);
            }
        }
        world.everywhere(decorators(decorator(IrisDecorationPart.SEA_SURFACE, 1D, 1, 1, "minecraft:lily_pad")));
        HydrologyVegetation vegetation = new HydrologyVegetation(7L, null);
        Hunk<NativeBlockState> chunk = world.decorate(vegetation, 0, 0);
        assertEquals("minecraft:lily_pad", block(chunk, 4, 61, 4));
        return List.of(new WeakReference<>(vegetation), new WeakReference<>(world), new WeakReference<>(chunk));
    }

    @Test
    public void lavaGrowsNoFloorOrSurfacePlants() {
        HydrologyVegetation.Decorators plants = decorators(floor("minecraft:seagrass", null, 1),
                decorator(IrisDecorationPart.SEA_SURFACE, 1D, 1, 1, "minecraft:lily_pad"));
        for (String fluid : List.of("minecraft:lava", "minecraft:water")) {
            World world = new World();
            world.fluid = fluid;
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    world.wet(lx, lz, WaterRole.POND, 55, 60);
                }
            }
            world.everywhere(plants);

            Hunk<NativeBlockState> chunk = world.decorate(new HydrologyVegetation(7L, null), 0, 0);

            int seagrass = 0;
            int lilies = 0;
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    seagrass += block(chunk, lx, 56, lz).equals("minecraft:seagrass") ? 1 : 0;
                    lilies += block(chunk, lx, 61, lz).equals("minecraft:lily_pad") ? 1 : 0;
                    assertEquals(fluid, block(chunk, lx, 60, lz));
                }
            }
            boolean lava = fluid.equals("minecraft:lava");
            assertEquals(fluid, lava ? 0 : 256, seagrass);
            assertEquals(fluid, lava ? 0 : 256, lilies);
        }
    }

    @Test
    public void everyChunkOrderGrowsTheSamePlants() {
        Supplier<World> scene = () -> {
            World world = new World();
            for (int x = -16; x < 16; x++) {
                for (int lz = 0; lz < 16; lz++) {
                    int distance = (int) Math.round(Math.hypot(x + 0.5D, lz - 7.5D));
                    if (distance <= 6) {
                        world.wet(x, lz, WaterRole.POND, 60 - (7 - distance), 60);
                    } else if (distance == 7) {
                        world.dry(x, lz, WaterRole.POND_SHORE, 60);
                    } else {
                        world.dry(x, lz, WaterRole.BANK, 61 + (x & 1));
                    }
                }
            }
            world.everywhere(decorators(floor("minecraft:kelp_plant", "minecraft:kelp", 9),
                    decorator(IrisDecorationPart.SEA_FLOOR, 0.6D, 1, 1, "minecraft:seagrass", "minecraft:sea_pickle"),
                    decorator(IrisDecorationPart.SEA_SURFACE, 0.5D, 1, 1, "minecraft:lily_pad"),
                    decorator(IrisDecorationPart.SHORE_LINE, 0.7D, 1, 3, "minecraft:sugar_cane"),
                    decorator(IrisDecorationPart.SHORE_LINE, 0.5D, 1, 1, "minecraft:fern", "minecraft:tall_grass[half=lower]")));
            return world;
        };
        World forwardWorld = scene.get();
        HydrologyVegetation forwardVegetation = new HydrologyVegetation(99L, null);
        Hunk<NativeBlockState> forwardWest = forwardWorld.decorate(forwardVegetation, -1, 0);
        Hunk<NativeBlockState> forwardEast = forwardWorld.decorate(forwardVegetation, 0, 0);
        World backwardWorld = scene.get();
        HydrologyVegetation backwardVegetation = new HydrologyVegetation(99L, null);
        Hunk<NativeBlockState> backwardEast = backwardWorld.decorate(backwardVegetation, 0, 0);
        Hunk<NativeBlockState> backwardWest = backwardWorld.decorate(backwardVegetation, -1, 0);

        assertSameBlocks(forwardWest, backwardWest);
        assertSameBlocks(forwardEast, backwardEast);
        Map<String, Integer> grown = new HashMap<>();
        for (Hunk<NativeBlockState> chunk : List.of(forwardWest, forwardEast)) {
            countPlants(chunk, grown);
        }
        for (String plant : List.of("minecraft:kelp", "minecraft:seagrass", "minecraft:sea_pickle", "minecraft:lily_pad", "minecraft:sugar_cane",
                "minecraft:fern", "minecraft:tall_grass")) {
            assertTrue(plant + " in " + grown, grown.getOrDefault(plant, 0) > 0);
        }
        Hunk<NativeBlockState> reseeded = forwardWorld.decorate(new HydrologyVegetation(100L, null), 0, 0);
        assertFalse(sameBlocks(forwardEast, reseeded));
    }

    @Test
    public void plantsNeverLeaveWaterBesideOpenGround() {
        HydrologyVegetation.Decorators plants = decorators(floor("minecraft:kelp_plant", "minecraft:kelp", 12),
                decorator(IrisDecorationPart.SEA_FLOOR, 1D, 1, 1, "minecraft:tall_seagrass[half=lower]", "minecraft:seagrass"),
                decorator(IrisDecorationPart.SEA_SURFACE, 1D, 1, 1, "minecraft:lily_pad"),
                decorator(IrisDecorationPart.SHORE_LINE, 1D, 1, 3, "minecraft:sugar_cane"),
                decorator(IrisDecorationPart.SHORE_LINE, 1D, 1, 1, "minecraft:fern", "minecraft:tall_grass[half=lower]"));
        SceneWorld scene = new SceneWorld(containmentScene(), plants, data);
        Map<Long, Hunk<NativeBlockState>> before = new HashMap<>();
        Map<Long, Hunk<NativeBlockState>> after = new HashMap<>();
        HydrologyVegetation vegetation = new HydrologyVegetation(5L, null);
        for (int chunkX = -1; chunkX <= 3; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                before.put(key(chunkX, chunkZ), scene.paint(chunkX, chunkZ));
                Hunk<NativeBlockState> decorated = scene.paint(chunkX, chunkZ);
                scene.decorate(vegetation, decorated, chunkX, chunkZ);
                after.put(key(chunkX, chunkZ), decorated);
            }
        }
        World shelf = shelfWorld(plants);
        Map<Long, Hunk<NativeBlockState>> shelfBefore = Map.of(key(0, 0), shelf.paint(0, 0));
        Map<Long, Hunk<NativeBlockState>> shelfAfter = Map.of(key(0, 0), shelf.decorate(vegetation, 0, 0));

        assertEquals(List.of(), leaks(before, 0, 48));
        assertEquals(List.of(), leaks(shelfBefore, 0, 16));
        assertEquals(List.of(), leaks(after, 0, 48));
        assertEquals(List.of(), leaks(shelfAfter, 0, 16));
        Map<String, Integer> grown = new HashMap<>();
        for (Hunk<NativeBlockState> chunk : after.values()) {
            countPlants(chunk, grown);
        }
        countPlants(shelfAfter.get(key(0, 0)), grown);
        for (String plant : List.of("minecraft:kelp", "minecraft:tall_seagrass", "minecraft:lily_pad", "minecraft:fern", "minecraft:sugar_cane")) {
            assertTrue(plant + " in " + grown, grown.getOrDefault(plant, 0) > 0);
        }
    }

    private World shelfWorld(HydrologyVegetation.Decorators plants) {
        World world = new World();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                boolean rim = lx == 0 || lx == 15 || lz == 8 || lz == 15;
                if (lz < 4) {
                    world.dry(lx, lz, WaterRole.NONE, SEA - 8);
                } else if (lz < 6) {
                    world.dry(lx, lz, WaterRole.BANK, SEA - 4);
                } else if (lz < 8) {
                    world.dry(lx, lz, WaterRole.BANK, SEA + 1);
                } else if (rim) {
                    world.dry(lx, lz, WaterRole.POND_SHORE, 40);
                } else {
                    world.wet(lx, lz, WaterRole.POND, 33, 40);
                }
            }
        }
        world.everywhere(plants);
        return world;
    }

    @Test
    public void compileRejectsUnknownBlocksFluidsAndDryFloorPlants() {
        assertRejected(decorator(IrisDecorationPart.SEA_SURFACE, 1D, 1, 1, "minecraft:lily_padd"), "test[0].palette[0]", "does not exist");
        IrisDecorator unknownTop = decorator(IrisDecorationPart.SEA_FLOOR, 1D, 2, 4, "minecraft:kelp_plant");
        unknownTop.setTopPalette(new KList<>(new IrisBlockData("minecraft:kelpp")));
        assertRejected(unknownTop, "test[0].topPalette[0]", "does not exist");
        IrisDecorator unknownWhitelist = decorator(IrisDecorationPart.SHORE_LINE, 1D, 1, 1, "minecraft:fern");
        unknownWhitelist.setWhitelist(new KList<>(new IrisBlockData("minecraft:grass_blok")));
        assertRejected(unknownWhitelist, "test[0].whitelist[0]", "does not exist");
        assertRejected(decorator(IrisDecorationPart.SEA_SURFACE, 1D, 1, 1, "minecraft:water"), "test[0].palette[0]", "never place fluids");
        assertRejected(decorator(IrisDecorationPart.SEA_FLOOR, 1D, 1, 1, "minecraft:fern"), "test[0].palette[0]", "cannot stand in water");
        assertRejected(decorator(IrisDecorationPart.NONE, 1D, 1, 1, "minecraft:fern"), "test[0].partOf", "SEA_FLOOR");
        for (String wet : List.of("minecraft:seagrass", "minecraft:kelp", "minecraft:sea_pickle", "minecraft:tube_coral", "minecraft:fern[waterlogged=true]")) {
            assertRejected(decorator(IrisDecorationPart.SEA_SURFACE, 1D, 1, 1, wet), "test[0].palette[0]", "holds water");
            assertRejected(decorator(IrisDecorationPart.SHORE_LINE, 1D, 1, 1, wet), "test[0].palette[0]", "holds water");
        }
        IrisDecorator wetTop = decorator(IrisDecorationPart.SHORE_LINE, 1D, 2, 3, "minecraft:sugar_cane");
        wetTop.setTopPalette(new KList<>(new IrisBlockData("minecraft:kelp")));
        assertRejected(wetTop, "test[0].topPalette[0]", "holds water");

        assertSame(HydrologyVegetation.Decorators.EMPTY, HydrologyVegetation.Decorators.compile(null, data, "test"));
        assertSame(HydrologyVegetation.Decorators.EMPTY, HydrologyVegetation.Decorators.compile(List.of(), data, "test"));
        assertFalse(decorators(decorator(IrisDecorationPart.SEA_FLOOR, 1D, 1, 1, "minecraft:tube_coral")).empty());
    }

    private void assertRejected(IrisDecorator decorator, String field, String reason) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> HydrologyVegetation.Decorators.compile(List.of(decorator), data, "test"));
        assertTrue(failure.getMessage(), failure.getMessage().contains(field) && failure.getMessage().contains(reason));
    }

    private HydrologyVegetation.Decorators decorators(IrisDecorator... decorators) {
        return HydrologyVegetation.Decorators.compile(List.of(decorators), data, "test");
    }

    private static IrisDecorator floor(String body, String top, int stack) {
        IrisDecorator decorator = decorator(IrisDecorationPart.SEA_FLOOR, 1D, stack, stack, body);
        if (top != null) {
            decorator.setTopPalette(new KList<>(new IrisBlockData(top)));
        }
        return decorator;
    }

    private static IrisDecorator decorator(IrisDecorationPart part, double chance, int stackMin, int stackMax, String... blocks) {
        KList<IrisBlockData> palette = new KList<>();
        for (String block : blocks) {
            palette.add(IrisBlockData.from(block));
        }
        return new IrisDecorator().setPartOf(part).setChance(chance).setStackMin(stackMin).setStackMax(stackMax).setPalette(palette);
    }

    private static String block(Hunk<NativeBlockState> chunk, int x, int y, int z) {
        return ((PlantBlockState) chunk.get(x, y, z)).block();
    }

    private static int lilyPads(Hunk<NativeBlockState> chunk, int x, int z) {
        int pads = 0;
        for (int y = 0; y < HEIGHT; y++) {
            pads += block(chunk, x, y, z).equals("minecraft:lily_pad") ? 1 : 0;
        }
        return pads;
    }

    private static String half(Hunk<NativeBlockState> chunk, int x, int y, int z) {
        return IrisProceduralBlocks.propertyValue(chunk.get(x, y, z), "half");
    }

    private static void assertSameBlocks(Hunk<NativeBlockState> expected, Hunk<NativeBlockState> actual) {
        assertTrue(sameBlocks(expected, actual));
    }

    private static boolean sameBlocks(Hunk<NativeBlockState> expected, Hunk<NativeBlockState> actual) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (expected.get(x, y, z) != actual.get(x, y, z)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void countPlants(Hunk<NativeBlockState> chunk, Map<String, Integer> grown) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    String block = ((PlantBlockState) chunk.get(x, y, z)).block();
                    if (!Set.of("minecraft:air", "minecraft:stone", "minecraft:dirt", "minecraft:water", "minecraft:lava").contains(block)) {
                        grown.merge(block, 1, Integer::sum);
                    }
                }
            }
        }
    }

    private static List<String> leaks(Map<Long, Hunk<NativeBlockState>> chunks, int minX, int maxX) {
        List<String> leaks = new ArrayList<>();
        for (int x = minX; x < maxX; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 1; y < HEIGHT; y++) {
                    NativeBlockState state = at(chunks, x, y, z);
                    if (!holdsWater(state)) {
                        continue;
                    }
                    boolean source = !state.isFluid() || IrisProceduralBlocks.propertyValue(state, "level") == null;
                    boolean openBelow = open(at(chunks, x, y - 1, z));
                    boolean openSide = false;
                    for (int[] side : SIDES) {
                        openSide |= open(at(chunks, x + side[0], y, z + side[1]));
                    }
                    if (openBelow || source && openSide) {
                        leaks.add(state.key() + " at " + x + "," + y + "," + z);
                    }
                }
            }
        }
        return leaks;
    }

    private static boolean holdsWater(NativeBlockState state) {
        return state.isWater() || state.isWaterLogged() || WATER_PLANTS.contains(((PlantBlockState) state).block());
    }

    private static boolean open(NativeBlockState state) {
        return state != null && OPEN.contains(((PlantBlockState) state).block());
    }

    private static NativeBlockState at(Map<Long, Hunk<NativeBlockState>> chunks, int x, int y, int z) {
        Hunk<NativeBlockState> chunk = chunks.get(key(x >> 4, z >> 4));
        return chunk == null ? null : chunk.get(x & 15, y, z & 15);
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static WaterScene containmentScene() {
        WaterScene scene = new WaterScene(new IrisHydrology(), (x, z) -> 70 - Math.floorDiv(Math.max(0, x), 12) * 3 + Math.abs(z - 8) / 2);
        scene.reach(WaterScene.line(11L, WaterScene.tier("river", 3f, 1.2f), -10, 8, 36, 8,
                (x, z) -> x < 12 ? 68 : x < 24 ? 65 : x < 30 ? 62 : 55));
        scene.pond(WaterScene.pond(12L, 66, 6, 3, 5));
        return scene;
    }

    private final class World implements HydrologyVegetation.Water {
        private final Map<Long, Integer> grounds = new HashMap<>();
        private final Map<Long, Integer> heads = new HashMap<>();
        private final Map<Long, WaterRole> roles = new HashMap<>();
        private final Map<Long, Boolean> flows = new HashMap<>();
        private HydrologyVegetation.Decorators plants = HydrologyVegetation.Decorators.EMPTY;
        private String fluid = "minecraft:water";

        private void wet(int x, int z, WaterRole role, int ground, int head) {
            grounds.put(key(x, z), ground);
            heads.put(key(x, z), head);
            roles.put(key(x, z), role);
        }

        private void dry(int x, int z, WaterRole role, int ground) {
            grounds.put(key(x, z), ground);
            heads.remove(key(x, z));
            roles.put(key(x, z), role);
        }

        private void flowing(int x, int z) {
            flows.put(key(x, z), true);
        }

        private void everywhere(HydrologyVegetation.Decorators decorators) {
            plants = decorators;
        }

        private WaterRole role(int x, int z) {
            return roles.getOrDefault(key(x, z), WaterRole.NONE);
        }

        @Override
        public int ground(int x, int z) {
            return grounds.getOrDefault(key(x, z), 64);
        }

        @Override
        public int head(int x, int z) {
            return heads.getOrDefault(key(x, z), SEA);
        }

        @Override
        public double slope(int x, int z) {
            return 0D;
        }

        @Override
        public HydrologyVegetation.Decorators decorators(int x, int z) {
            return plants;
        }

        private Hunk<NativeBlockState> decorate(HydrologyVegetation vegetation, int chunkX, int chunkZ) {
            Hunk<NativeBlockState> chunk = paint(chunkX, chunkZ);
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int x = (chunkX << 4) + lx;
                    int z = (chunkZ << 4) + lz;
                    vegetation.decorate(chunk, lx, lz, x, z, role(x, z), this, data);
                }
            }
            return chunk;
        }

        private Hunk<NativeBlockState> paint(int chunkX, int chunkZ) {
            Hunk<NativeBlockState> chunk = Hunk.newArrayHunk(16, HEIGHT, 16);
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int x = (chunkX << 4) + lx;
                    int z = (chunkZ << 4) + lz;
                    int ground = ground(x, z);
                    Integer head = heads.get(key(x, z));
                    int fallTop = head == null ? Integer.MIN_VALUE : fallTop(x, z, head);
                    for (int y = 0; y < HEIGHT; y++) {
                        String block = y < ground ? "minecraft:stone" : y == ground ? "minecraft:dirt"
                                : head != null && y <= head ? fluid : y <= fallTop ? fluid + "[level=8]"
                                : head == null && y <= SEA ? "minecraft:water" : "minecraft:air";
                        if (head != null && y == head && flows.containsKey(key(x, z))) {
                            block = fluid + "[level=3]";
                        }
                        chunk.set(lx, y, lz, PlantBlockState.of(block));
                    }
                }
            }
            return chunk;
        }

        private int fallTop(int x, int z, int head) {
            int top = Integer.MIN_VALUE;
            for (int[] side : SIDES) {
                Integer neighbour = heads.get(key(x + side[0], z + side[1]));
                if (neighbour != null && neighbour > head) {
                    top = Math.max(top, neighbour);
                }
            }
            return top;
        }
    }

    private record SceneWorld(WaterScene scene, HydrologyVegetation.Decorators plants, IrisData data) implements HydrologyVegetation.Water {
        @Override
        public int ground(int x, int z) {
            return scene.terrain(x, z);
        }

        @Override
        public int head(int x, int z) {
            return scene.water(x, z) ? scene.head(x, z) : WaterScene.SEA_LEVEL;
        }

        @Override
        public double slope(int x, int z) {
            return 0D;
        }

        @Override
        public HydrologyVegetation.Decorators decorators(int x, int z) {
            return plants;
        }

        private WaterRole role(int x, int z) {
            ChunkWaterRaster raster = scene.raster(x >> 4, z >> 4);
            return raster.empty() ? WaterRole.NONE : raster.role(x & 15, z & 15);
        }

        private void decorate(HydrologyVegetation vegetation, Hunk<NativeBlockState> chunk, int chunkX, int chunkZ) {
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int x = (chunkX << 4) + lx;
                    int z = (chunkZ << 4) + lz;
                    vegetation.decorate(chunk, lx, lz, x, z, role(x, z), this, data);
                }
            }
        }

        private Hunk<NativeBlockState> paint(int chunkX, int chunkZ) {
            Hunk<NativeBlockState> chunk = Hunk.newArrayHunk(16, HEIGHT, 16);
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int x = (chunkX << 4) + lx;
                    int z = (chunkZ << 4) + lz;
                    int ground = ground(x, z);
                    boolean wet = scene.water(x, z);
                    int head = wet ? scene.head(x, z) : Integer.MIN_VALUE;
                    int fallTop = Integer.MIN_VALUE;
                    for (int[] side : SIDES) {
                        if (wet && scene.water(x + side[0], z + side[1]) && scene.head(x + side[0], z + side[1]) > head) {
                            fallTop = Math.max(fallTop, scene.head(x + side[0], z + side[1]));
                        }
                    }
                    for (int y = 0; y < HEIGHT; y++) {
                        String block = y < ground ? "minecraft:stone" : y == ground ? "minecraft:dirt"
                                : y <= head ? "minecraft:water" : y <= fallTop ? "minecraft:water[level=8]" : "minecraft:air";
                        chunk.set(lx, y, lz, PlantBlockState.of(block));
                    }
                }
            }
            return chunk;
        }
    }
}
