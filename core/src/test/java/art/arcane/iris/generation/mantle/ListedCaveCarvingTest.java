package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.cave.IrisCave;
import art.arcane.iris.generation.cave.TestSettings;
import art.arcane.iris.generation.cave.field.CngNoiseFactory;
import art.arcane.iris.generation.mantle.CaveListing.ListedColumns;
import art.arcane.iris.generation.mantle.CaveTerrain.CaveBand;
import art.arcane.iris.generation.mantle.CaveTerrain.CavePlace;
import art.arcane.iris.generation.mantle.ListedCaveCarving.ChunkSurface;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ListedCaveCarvingTest {
    private static final int WORLD_HEIGHT = 256;
    private static final int SURFACE_Y = 200;
    private static final long CARVE_SEED = 4_711L;
    private static final int FLUID_HEIGHT = 63;
    private static final int LAVA_HEIGHT = 10;
    private static final int POCKET_SEARCH_CHUNKS = 12;
    private static final byte LIQUID_FLUID = 1;
    private static final byte LIQUID_LAVA = 2;
    private static final byte LIQUID_FORCED_AIR = 3;
    private static final String HALLS = "{\"cavern\": {\"size\": 40, \"height\": 24, \"coverage\": 0.45}, \"surfaceClearance\": 0, \"allowFluid\": false}";
    private static final String DEEP_HALLS = "{\"cavern\": {\"size\": 40, \"height\": 24, \"coverage\": 0.45}, \"depth\": {\"min\": 80, \"max\": 140},"
            + " \"fade\": 0, \"surfaceClearance\": 0, \"allowFluid\": false}";
    private static final CavePlace NOWHERE_SPECIAL = new CavePlace(new IrisRegion(), new IrisBiome(), new IrisBiome());

    private final Map<String, String> caveFiles = new HashMap<>();
    private final Set<String> biomes = new HashSet<>();
    private final List<String> missingCaves = new ArrayList<>();
    private final List<String> missingBiomes = new ArrayList<>();

    @Test
    public void listsOfTheDimensionRegionSurfaceBiomeAndCaveBiomeAddUp() {
        files("dimension", HALLS, "region", HALLS, "surface", HALLS, "cave", HALLS);
        IrisRegion region = new IrisRegion().setCaves(new KList<>("region"));
        IrisBiome surface = new IrisBiome().setCaves(new KList<>("surface"));
        IrisBiome cave = new IrisBiome().setCaves(new KList<>("cave"));

        CaveListing listing = carving(List.of("dimension"), false).list(terrainEverywhere(new CavePlace(region, surface, cave)), 0, 0);

        assertEquals(List.of("dimension", "region", "surface", "cave"), keysOf(listing));
        for (ListedColumns listed : listing.caves()) {
            assertTrue(Arrays.stream(listed.weight()).allMatch(weight -> weight == 1D));
        }
    }

    @Test
    public void aCaveListedTwiceCarvesOnce() {
        files("shared", HALLS);
        IrisRegion region = new IrisRegion().setCaves(new KList<>("shared"));

        CaveListing listing = carving(List.of("shared"), false).list(terrainEverywhere(new CavePlace(region, null, null)), 0, 0);

        assertEquals(List.of("shared"), keysOf(listing));
    }

    @Test
    public void aPackThatOnlyListsCavesInTheDimensionNeverReadsTheTerrain() {
        files("dimension", HALLS);
        int[] reads = new int[1];
        CaveTerrain counting = new CaveTerrain() {
            @Override
            public CavePlace placeAt(int worldX, int worldZ) {
                reads[0]++;
                return NOWHERE_SPECIAL;
            }

            @Override
            public List<CaveBand> bandsOf(int chunkX, int chunkZ) {
                return List.of();
            }
        };

        CaveListing listing = carving(List.of("dimension"), true).list(counting, 5, -3);

        assertEquals(List.of("dimension"), keysOf(listing));
        assertEquals(0, reads[0]);
    }

    @Test
    public void aCaveBiomeListsItsCavesOnlyWhereTheCaveBiomeIsAndTapersOffAcrossItsBorder() {
        files("deep", HALLS);
        IrisBiome withCaves = new IrisBiome().setCaves(new KList<>("deep"));
        IrisBiome plain = new IrisBiome();
        BiFunction<Integer, Integer, CavePlace> placeAt = (x, z) -> new CavePlace(null, null, x < -8 ? withCaves : plain);

        CaveListing listing = carving(List.of(), false).list(terrain(placeAt, List.of()), -1, 0);

        assertEquals(List.of("deep"), keysOf(listing));
        double[] weight = listing.caves().getFirst().weight();
        assertEquals(1D, weight[PowerOfTwoCoordinates.packLocal16(0, 8)], 0D);
        assertEquals(0D, weight[PowerOfTwoCoordinates.packLocal16(15, 8)], 0D);
        for (int localX = 1; localX < 16; localX++) {
            assertTrue("weight falls towards the east", weight[PowerOfTwoCoordinates.packLocal16(localX, 8)]
                    <= weight[PowerOfTwoCoordinates.packLocal16(localX - 1, 8)]);
        }
        assertTrue(weight[PowerOfTwoCoordinates.packLocal16(8, 8)] > 0D && weight[PowerOfTwoCoordinates.packLocal16(8, 8)] < 1D);
    }

    @Test
    public void aCaveIsMissingFromTheListingAndReportedOnce() {
        files("halls", HALLS);
        ListedCaveCarving carving = carving(List.of("halls", "ghost"), true);

        CaveListing first = carving.list(terrainEverywhere(NOWHERE_SPECIAL), 0, 0);
        CaveListing second = carving.list(terrainEverywhere(NOWHERE_SPECIAL), 1, 0);

        assertEquals(List.of("halls"), keysOf(first));
        assertEquals(List.of("halls"), keysOf(second));
        assertEquals(List.of("ghost"), missingCaves);
    }

    @Test
    public void aCaveBiomeTheCavePacksDoNotHaveIsReportedAndLeftOut() {
        files("grotto", "{\"cavern\": {\"size\": 40, \"height\": 24, \"coverage\": 0.45}, \"surfaceClearance\": 0, \"allowFluid\": false, \"biome\": \"nowhere\"}");
        ListedCaveCarving carving = carving(List.of("grotto"), true);

        CaveListing listing = carving.list(terrainEverywhere(NOWHERE_SPECIAL), 0, 0);
        Map<Long, MatterCavern> cells = carve(carving, listing, 0, 0, new ChunkSurface(surfaceEverywhere(), null, null));

        assertEquals(List.of("grotto:nowhere"), missingBiomes);
        assertFalse(cells.isEmpty());
        assertTrue(cells.values().stream().allMatch(cell -> cell.getCustomBiome().isEmpty()));
    }

    @Test
    public void aCaveCarvesOnlyInsideItsDepthRange() {
        files("deep", DEEP_HALLS);
        ListedCaveCarving carving = carving(List.of("deep"), true);

        Map<Long, MatterCavern> cells = carve(carving, carving.list(terrainEverywhere(NOWHERE_SPECIAL), 3, 4), 3, 4,
                new ChunkSurface(surfaceEverywhere(), null, null));

        assertFalse(cells.isEmpty());
        assertTrue("carved between 80 and 140", cells.keySet().stream().allMatch(key -> heightOf(key) >= 80 && heightOf(key) <= 140));
    }

    @Test
    public void aCarvingBandLimitsItsBiomesCavesToItsHeights() {
        files("halls", HALLS);
        IrisBiome bandBiome = new IrisBiome().setCaves(new KList<>("halls"));
        IrisBiome[] everywhere = new IrisBiome[256];
        Arrays.fill(everywhere, bandBiome);
        CaveBand band = new CaveBand(100, 120, everywhere);
        ListedCaveCarving carving = carving(List.of(), true);

        CaveListing listing = carving.list(terrain((x, z) -> NOWHERE_SPECIAL, List.of(band)), 2, 2);
        Map<Long, MatterCavern> cells = carve(carving, listing, 2, 2, new ChunkSurface(surfaceEverywhere(), null, null));

        assertEquals(List.of("halls"), keysOf(listing));
        assertFalse(cells.isEmpty());
        assertTrue("carved between 100 and 120", cells.keySet().stream().allMatch(key -> heightOf(key) >= 100 && heightOf(key) <= 120));
    }

    @Test
    public void aBandIsMeasuredFromTheDimensionsBottom() {
        files("halls", HALLS);
        IrisBiome[] everywhere = new IrisBiome[256];
        Arrays.fill(everywhere, new IrisBiome().setCaves(new KList<>("halls")));
        CaveWorld below = new CaveWorld(-64, WORLD_HEIGHT, CARVE_SEED, FLUID_HEIGHT, LAVA_HEIGHT);
        ListedCaveCarving carving = carving(List.of(), true, below);

        CaveListing listing = carving.list(terrain((x, z) -> NOWHERE_SPECIAL, List.of(new CaveBand(0, 20, everywhere))), 2, 2);
        Map<Long, MatterCavern> cells = carve(carving, listing, 2, 2, new ChunkSurface(surfaceEverywhere(), null, null));

        assertFalse(cells.isEmpty());
        assertTrue("world 0..20 is height 64..84", cells.keySet().stream().allMatch(key -> heightOf(key) >= 64 && heightOf(key) <= 84));
    }

    @Test
    public void aColumnCeilingKeepsEveryCaveUnderIt() {
        files("halls", HALLS);
        int ceilingY = 90;
        int[] ceilings = new int[256];
        Arrays.fill(ceilings, ceilingY);
        ListedCaveCarving carving = carving(List.of("halls"), true);
        CaveListing listing = carving.list(terrainEverywhere(NOWHERE_SPECIAL), 2, 2);

        Map<Long, MatterCavern> free = carve(carving, listing, 2, 2, new ChunkSurface(surfaceEverywhere(), null, null));
        Map<Long, MatterCavern> capped = carve(carving, listing, 2, 2, new ChunkSurface(surfaceEverywhere(), null, ceilings));

        assertTrue("without a ceiling the cave rises above " + ceilingY, free.keySet().stream().anyMatch(key -> heightOf(key) > ceilingY));
        assertFalse(capped.isEmpty());
        assertTrue("under the ceiling", capped.keySet().stream().allMatch(key -> heightOf(key) <= ceilingY));
    }

    @Test
    public void aSurfaceFluidBoundaryKeepsCavesOutOfItsHeights() {
        files("halls", HALLS);
        long[] boundaries = new long[256];
        Arrays.fill(boundaries, SurfaceFluidBoundaryPlan.boundary(60, 90));
        ListedCaveCarving carving = carving(List.of("halls"), true);
        CaveListing listing = carving.list(terrainEverywhere(NOWHERE_SPECIAL), 2, 2);

        Map<Long, MatterCavern> free = carve(carving, listing, 2, 2, new ChunkSurface(surfaceEverywhere(), null, null));
        Map<Long, MatterCavern> protectedCells = carve(carving, listing, 2, 2, new ChunkSurface(surfaceEverywhere(), boundaries, null));

        assertTrue(free.keySet().stream().anyMatch(key -> heightOf(key) >= 60 && heightOf(key) <= 90));
        assertFalse(protectedCells.isEmpty());
        assertTrue(protectedCells.keySet().stream().noneMatch(key -> heightOf(key) >= 60 && heightOf(key) <= 90));
    }

    @Test
    public void aBlockSeveralCavesCarveBelongsToTheFirstListed() {
        files("first", "{\"cavern\": {\"size\": 40, \"height\": 24, \"coverage\": 0.5}, \"surfaceClearance\": 0, \"allowFluid\": false, \"biome\": \"one\"}",
                "second", "{\"cavern\": {\"size\": 40, \"height\": 24, \"coverage\": 0.5}, \"surfaceClearance\": 0, \"allowFluid\": false, \"biome\": \"two\"}");
        biomes.addAll(List.of("one", "two"));
        ListedCaveCarving carving = carving(List.of("first", "second"), true);
        ChunkSurface surface = new ChunkSurface(surfaceEverywhere(), null, null);
        CaveListing both = carving.list(terrainEverywhere(NOWHERE_SPECIAL), 2, 2);
        CaveListing onlyFirst = new CaveListing(both.caves().subList(0, 1), false);

        Map<Long, MatterCavern> bothCarved = carve(carving, both, 2, 2, surface);
        Map<Long, MatterCavern> firstCarved = carve(carving, onlyFirst, 2, 2, surface);

        assertTrue(bothCarved.keySet().containsAll(firstCarved.keySet()));
        assertTrue("some blocks only the second carves", bothCarved.size() > firstCarved.size());
        for (Long key : firstCarved.keySet()) {
            assertEquals("one", bothCarved.get(key).getCustomBiome());
        }
        assertTrue(bothCarved.entrySet().stream().filter(entry -> !firstCarved.containsKey(entry.getKey()))
                .allMatch(entry -> entry.getValue().getCustomBiome().equals("two")));
    }

    @Test
    public void cavesBelowTheLavaHeightHoldLavaOrForcedAirAsTheyAllow() {
        files("lava", "{\"cavern\": {\"size\": 40, \"height\": 24, \"coverage\": 0.5}, \"depth\": {\"min\": 0, \"max\": 9}, \"fade\": 0, \"surfaceClearance\": 0, \"allowFluid\": false}",
                "dry", "{\"cavern\": {\"size\": 40, \"height\": 24, \"coverage\": 0.5}, \"depth\": {\"min\": 0, \"max\": 9}, \"fade\": 0, \"surfaceClearance\": 0, \"allowFluid\": false, \"allowLava\": false}");

        for (String cave : List.of("lava", "dry")) {
            ListedCaveCarving carving = carving(List.of(cave), true);
            Map<Long, MatterCavern> cells = carve(carving, carving.list(terrainEverywhere(NOWHERE_SPECIAL), 2, 2), 2, 2,
                    new ChunkSurface(surfaceEverywhere(), null, null));

            assertFalse(cave, cells.isEmpty());
            byte expected = cave.equals("lava") ? LIQUID_LAVA : LIQUID_FORCED_AIR;
            assertTrue(cave, cells.values().stream().allMatch(cell -> cell.getLiquid() == expected));
        }
    }

    @Test
    public void aCaveHoldsPocketsOfFluidBelowTheFluidHeightOnlyDeepEnoughBelowTheSurface() {
        files("wet", "{\"cavern\": {\"size\": 60, \"height\": 30, \"coverage\": 0.6}, \"depth\": {\"min\": 20, \"max\": 60}, \"fade\": 0, \"surfaceClearance\": 0,"
                + " \"allowLava\": false, \"fluidRequiresFloor\": false}");
        ListedCaveCarving carving = carving(List.of("wet"), true);
        int[] shallow = new int[256];
        Arrays.fill(shallow, 70);

        List<Integer> fluidUnderDeepSurface = new ArrayList<>();
        List<Integer> fluidUnderShallowSurface = new ArrayList<>();
        for (int chunkX = 0; chunkX < POCKET_SEARCH_CHUNKS; chunkX++) {
            for (int chunkZ = 0; chunkZ < POCKET_SEARCH_CHUNKS; chunkZ++) {
                CaveListing listing = carving.list(terrainEverywhere(NOWHERE_SPECIAL), chunkX, chunkZ);
                addFluidHeights(carve(carving, listing, chunkX, chunkZ, new ChunkSurface(surfaceEverywhere(), null, null)), fluidUnderDeepSurface);
                addFluidHeights(carve(carving, listing, chunkX, chunkZ, new ChunkSurface(shallow, null, null)), fluidUnderShallowSurface);
            }
        }

        assertFalse("fluid pockets deep under the surface", fluidUnderDeepSurface.isEmpty());
        assertTrue("never above the fluid height", fluidUnderDeepSurface.stream().allMatch(y -> y <= FLUID_HEIGHT));
        assertTrue("under a surface at 70 a cave may hold fluid up to 58", fluidUnderShallowSurface.stream().allMatch(y -> y <= 70 - 12));
    }

    private static void addFluidHeights(Map<Long, MatterCavern> cells, List<Integer> heights) {
        cells.forEach((key, cell) -> {
            if (cell.getLiquid() == LIQUID_FLUID) {
                heights.add(heightOf(key));
            }
        });
    }

    @Test
    public void theUpperDimensionPassCarvesTheWholeDimensionBeneathItsTerrainUnderTheTopOfTheWorld() {
        files("deep", DEEP_HALLS);
        ListedCaveCarving carving = carving(List.of("deep"), true);
        CaveListing listing = carving.list(terrainEverywhere(NOWHERE_SPECIAL), 2, 2);
        Map<Long, MatterCavern> cells = new HashMap<>();

        carving.carveBeneathUpperTerrain(listing, 2, 2, 200, (x, y, z, cell) -> cells.put(key(x, y, z), cell), new CaveFluidSupportPlan());

        assertFalse(cells.isEmpty());
        assertTrue("only inside the upper terrain, whatever the cave's own depth", cells.keySet().stream().allMatch(key -> heightOf(key) >= 200));
        assertTrue("closed under the top of the world", cells.keySet().stream().allMatch(key -> heightOf(key) < WORLD_HEIGHT - 1));
        assertNotEquals("a carve the cave's own depth would have kept out", 0, cells.size());
    }

    @Test
    public void sameSettingsAndSeedCarveTheSameCaveAndAnotherSeedAnother() {
        files("halls", HALLS);
        ListedCaveCarving first = carving(List.of("halls"), true);
        ListedCaveCarving again = carving(List.of("halls"), true);
        ListedCaveCarving other = carving(List.of("halls"), true, new CaveWorld(0, WORLD_HEIGHT, CARVE_SEED + 1, FLUID_HEIGHT, LAVA_HEIGHT));
        ChunkSurface surface = new ChunkSurface(surfaceEverywhere(), null, null);

        Map<Long, MatterCavern> one = carve(first, first.list(terrainEverywhere(NOWHERE_SPECIAL), 2, 2), 2, 2, surface);
        Map<Long, MatterCavern> same = carve(again, again.list(terrainEverywhere(NOWHERE_SPECIAL), 2, 2), 2, 2, surface);
        Map<Long, MatterCavern> different = carve(other, other.list(terrainEverywhere(NOWHERE_SPECIAL), 2, 2), 2, 2, surface);

        assertEquals(one.keySet(), same.keySet());
        assertNotEquals(one.keySet(), different.keySet());
    }

    private void files(String... keysAndFiles) {
        for (int index = 0; index < keysAndFiles.length; index += 2) {
            caveFiles.put(keysAndFiles[index], keysAndFiles[index + 1]);
        }
    }

    private ListedCaveCarving carving(List<String> dimensionCaves, boolean onlyDimensionLists) {
        return carving(dimensionCaves, onlyDimensionLists, new CaveWorld(0, WORLD_HEIGHT, CARVE_SEED, FLUID_HEIGHT, LAVA_HEIGHT));
    }

    private ListedCaveCarving carving(List<String> dimensionCaves, boolean onlyDimensionLists, CaveWorld world) {
        return new ListedCaveCarving(dimensionCaves, onlyDimensionLists, world, CngNoiseFactory.standalone(),
                key -> caveFiles.containsKey(key) ? TestSettings.of(IrisCave.class, caveFiles.get(key)) : null,
                biomes::contains,
                new ListedCaveCarving.Problems() {
                    @Override
                    public void missingCave(String caveKey) {
                        missingCaves.add(caveKey);
                    }

                    @Override
                    public void missingCaveBiome(String caveKey, String biomeKey) {
                        missingBiomes.add(caveKey + ":" + biomeKey);
                    }
                });
    }

    private static CaveTerrain terrainEverywhere(CavePlace place) {
        return terrain((x, z) -> place, List.of());
    }

    private static CaveTerrain terrain(BiFunction<Integer, Integer, CavePlace> placeAt, List<CaveBand> bands) {
        return new CaveTerrain() {
            @Override
            public CavePlace placeAt(int worldX, int worldZ) {
                return placeAt.apply(worldX, worldZ);
            }

            @Override
            public List<CaveBand> bandsOf(int chunkX, int chunkZ) {
                return bands;
            }
        };
    }

    private static int[] surfaceEverywhere() {
        int[] surface = new int[256];
        Arrays.fill(surface, SURFACE_Y);
        return surface;
    }

    private static List<String> keysOf(CaveListing listing) {
        return listing.caves().stream().map(listed -> listed.cave().key()).toList();
    }

    private static Map<Long, MatterCavern> carve(ListedCaveCarving carving, CaveListing listing, int chunkX, int chunkZ, ChunkSurface surface) {
        return carve(carving, listing, chunkX, chunkZ, surface, new CaveFluidSupportPlan());
    }

    private static Map<Long, MatterCavern> carve(ListedCaveCarving carving, CaveListing listing, int chunkX, int chunkZ, ChunkSurface surface,
                                                 CaveFluidSupportPlan plan) {
        Map<Long, MatterCavern> cells = new HashMap<>();
        carving.carve(listing, chunkX, chunkZ, surface, (x, y, z, cell) -> cells.put(key(x, y, z), cell), plan);
        return cells;
    }

    private static long key(int localX, int y, int localZ) {
        return ((long) y << 8) | (localX << 4) | localZ;
    }

    private static int heightOf(long key) {
        return (int) (key >> 8);
    }
}
