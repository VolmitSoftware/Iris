package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.cave.IrisCave;
import art.arcane.iris.generation.cave.TestSettings;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.runtime.UpperDimensionContext;
import art.arcane.iris.generation.subterrain.IrisSubterrainFamily;
import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class MantleCarvingComponentWaterMaskTest {
    private static final int WORLD_HEIGHT = 128;
    private static final int MINIMUM_HEIGHT = -64;
    private static final int SURFACE_Y = 70;
    private static final int UPPER_SURFACE_Y = 100;
    private static final String HALLS = """
            {"cavern": {"size": 40, "height": 24, "coverage": 0.4}, "depth": {"min": -50, "max": 20},
             "surfaceClearance": 4, "allowFluid": false, "allowLava": false}
            """;

    private Engine engine;
    private MantleCarvingComponent component;
    private IrisDimension dimension;
    private IrisComplex complex;
    private HydrologyRuntime hydrology;
    private ChunkContext context;
    private MantleWriter writer;
    private Matter[] sections;

    @Before
    @SuppressWarnings("unchecked")
    public void prepare() {
        IrisMatterSupport.ensureRegistered();
        engine = mock(Engine.class);
        EngineMantle mantle = mock(EngineMantle.class);
        when(mantle.getEngine()).thenReturn(engine);
        component = new MantleCarvingComponent(mantle);
        dimension = new IrisDimension().setCaves(new KList<>("halls"));
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(MINIMUM_HEIGHT).maxHeight(MINIMUM_HEIGHT + WORLD_HEIGHT).build());
        when(engine.getMetrics()).thenReturn(new EngineMetrics(16));
        when(engine.getHeight()).thenReturn(WORLD_HEIGHT);
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getCarve()).thenReturn(4_711L);
        when(engine.getSeedManager()).thenReturn(seeds);
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisCave> caves = mock(ResourceLoader.class);
        when(caves.load("halls")).thenReturn(TestSettings.of(IrisCave.class, HALLS));
        when(data.getCaveLoader()).thenReturn(caves);
        when(engine.getData()).thenReturn(data);
        hydrology = mock(HydrologyRuntime.class);
        when(hydrology.raster(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.EMPTY);
        complex = mock(IrisComplex.class);
        when(complex.allowsMantleChunkWrite(anyInt(), anyInt())).thenReturn(true);
        when(complex.getHydrologyRuntime()).thenReturn(hydrology);
        ProceduralStream<Double> heights = mock(ProceduralStream.class);
        when(heights.getDouble(anyDouble(), anyDouble())).thenReturn((double) SURFACE_Y);
        when(complex.getNaturalHeightStream()).thenReturn(heights);
        ProceduralStream<IrisRegion> regions = mock(ProceduralStream.class);
        ProceduralStream<IrisBiome> biomes = mock(ProceduralStream.class);
        ProceduralStream<NativeBlockState> fluids = mock(ProceduralStream.class);
        when(regions.get(anyDouble(), anyDouble())).thenReturn(new IrisRegion());
        when(biomes.get(anyDouble(), anyDouble())).thenReturn(new IrisBiome());
        when(complex.getRegionStream()).thenReturn(regions);
        when(complex.getNaturalTrueBiomeStream()).thenReturn(biomes);
        when(complex.getCaveBiomeStream()).thenReturn(biomes);
        when(complex.getFluidStream()).thenReturn(fluids);
        context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(complex);
        writer = mock(MantleWriter.class);
        Mantle<Matter> storage = mock(Mantle.class);
        when(storage.getWorldHeight()).thenReturn(WORLD_HEIGHT);
        when(writer.getMantle()).thenReturn(storage);
        sections = new Matter[WORLD_HEIGHT >> 4];
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(chunk.sectionCount()).thenReturn(sections.length);
        when(chunk.get(anyInt())).thenAnswer(call -> sections[call.<Integer>getArgument(0)]);
        when(chunk.getOrCreate(anyInt())).thenAnswer(call -> {
            int index = call.getArgument(0);
            if (sections[index] == null) {
                sections[index] = new IrisMatter(16, 16, 16);
            }
            return sections[index];
        });
        when(writer.acquireChunk(anyInt(), anyInt())).thenReturn(chunk);
    }

    @Test
    public void theListedCavesCarveTheChunk() {
        component.generateLayer(writer, 2, 2, context);

        List<Integer> carved = carvedHeights();
        assertTrue("carved cells " + carved.size(), carved.size() > 500);
        int lowest = carved.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int highest = carved.stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertTrue("lowest " + lowest, lowest >= -50 - MINIMUM_HEIGHT - 12);
        assertTrue("highest " + highest, highest <= 20 - MINIMUM_HEIGHT + 12);
    }

    @Test
    public void waterMasksTheCavePassOfTheMainTerrain() {
        int ceilingY = 40;
        List<Integer> unmasked = carvedHeightsOfNewChunk(2, 2);
        maskEverywhere(ceilingY + CaveCeilingMask.ROOF);

        List<Integer> masked = carvedHeightsOfNewChunk(2, 2);

        assertTrue("an unmasked chunk carves above " + ceilingY, unmasked.stream().anyMatch(y -> y > ceilingY));
        assertFalse(masked.isEmpty());
        assertTrue("masked chunk carved above its ceiling", masked.stream().allMatch(y -> y <= ceilingY));
    }

    @Test
    public void theUpperDimensionPassCarvesBeneathItsTerrainAndIgnoresMainTerrainWater() {
        maskEverywhere(40 + CaveCeilingMask.ROOF);
        UpperDimensionContext upper = mock(UpperDimensionContext.class);
        when(upper.getEffectiveSurfaceY(anyInt(), anyInt())).thenReturn(UPPER_SURFACE_Y);
        when(engine.getUpperContext()).thenReturn(upper);
        dimension.setUpperDimensionCarving(true);

        List<Integer> carved = carvedHeightsOfNewChunk(2, 2);

        assertTrue("the upper pass carves where its terrain hangs", carved.stream().anyMatch(y -> y >= UPPER_SURFACE_Y));
        assertTrue("the upper pass stays inside its terrain", carved.stream().noneMatch(y -> y >= UPPER_SURFACE_Y - 1 && y < UPPER_SURFACE_Y));
    }

    @Test
    public void subterrainFeaturesNeverOpenACellAboveTheCaveCeiling() {
        dimension.setCaves(new KList<>());
        IrisSubterrainFeature feature = new IrisSubterrainFeature().setId("room").setFamily(IrisSubterrainFamily.CENOTE)
                .setProbability(1).setLength(64).setRadius(12).setHeight(24).setPillarSpacing(0).setFormationFraction(0)
                .setWorldYRange(new IrisRange(-48, 60));
        SubterrainPlanner planner = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 83L, -64, 128, (x, z) -> "region"));
        SubterrainPlan plan = planner.plansForBounds(-512, -512, 512, 512).getFirst();
        int chunkX = plan.centerX() >> 4;
        int chunkZ = plan.centerZ() >> 4;
        when(complex.getSubterrainPlanner()).thenReturn(planner);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState(anyString())).thenReturn(mock(NativeBlockState.class));
            List<Integer> open = openedHeights(chunkX, chunkZ);
            assertTrue("open subterrain cells " + open.size(), open.size() > 20);
            int threshold = open.stream().mapToInt(Integer::intValue).sorted().toArray()[open.size() / 2];
            assertTrue(open.stream().anyMatch(y -> y > threshold));

            maskEverywhere(threshold + CaveCeilingMask.ROOF);
            List<Integer> masked = openedHeights(chunkX, chunkZ);
            assertTrue(masked.size() > 0);
            assertTrue("subterrain opened above the ceiling " + threshold, masked.stream().allMatch(y -> y <= threshold));
        }
    }

    private List<Integer> carvedHeightsOfNewChunk(int chunkX, int chunkZ) {
        java.util.Arrays.fill(sections, null);
        component.generateLayer(writer, chunkX, chunkZ, context);
        return carvedHeights();
    }

    private List<Integer> carvedHeights() {
        List<Integer> carved = new ArrayList<>();
        for (int y = 0; y < WORLD_HEIGHT; y++) {
            Matter section = sections[y >> 4];
            if (section == null) {
                continue;
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (section.<MatterCavern>slice(MatterCavern.class).get(x, y & 15, z) != null) {
                        carved.add(y);
                    }
                }
            }
        }
        return carved;
    }

    private List<Integer> openedHeights(int chunkX, int chunkZ) {
        List<Integer> opened = new ArrayList<>();
        Mantle<Matter> storage = writer.getMantle();
        MantleWriter capture = mock(MantleWriter.class);
        when(capture.getMantle()).thenReturn(storage);
        doAnswer(call -> {
            if (call.getArgument(3) instanceof MatterCavern) {
                opened.add(call.getArgument(1));
            }
            return null;
        }).when(capture).setData(anyInt(), anyInt(), anyInt(), any());
        component.generateLayer(capture, chunkX, chunkZ, context);
        return opened;
    }

    private void maskEverywhere(int ceiling) {
        ChunkWaterRaster raster = mock(ChunkWaterRaster.class);
        when(raster.caveCeiling(anyInt(), anyInt())).thenReturn(ceiling);
        when(hydrology.raster(anyInt(), anyInt())).thenReturn(raster);
    }
}
