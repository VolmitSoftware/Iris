package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.UpperDimensionContext;
import art.arcane.iris.generation.subterrain.IrisSubterrainFamily;
import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
    private Engine engine;
    private MantleCarvingComponent component;
    private IrisDimension dimension;
    private IrisComplex complex;
    private HydrologyRuntime hydrology;
    private ChunkContext context;
    private MantleWriter writer;

    @Before
    @SuppressWarnings("unchecked")
    public void prepare() {
        engine = mock(Engine.class);
        EngineMantle mantle = mock(EngineMantle.class);
        when(mantle.getEngine()).thenReturn(engine);
        component = new MantleCarvingComponent(mantle);
        dimension = new IrisDimension();
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(-64).maxHeight(64).build());
        when(engine.getMetrics()).thenReturn(new EngineMetrics(16));
        when(engine.getHeight()).thenReturn(128);
        hydrology = mock(HydrologyRuntime.class);
        when(hydrology.raster(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.EMPTY);
        complex = mock(IrisComplex.class);
        when(complex.allowsMantleChunkWrite(anyInt(), anyInt())).thenReturn(true);
        when(complex.getHydrologyRuntime()).thenReturn(hydrology);
        ProceduralStream<Double> heights = mock(ProceduralStream.class);
        when(heights.getDouble(anyDouble(), anyDouble())).thenReturn(70D);
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
        when(storage.getWorldHeight()).thenReturn(128);
        when(writer.getMantle()).thenReturn(storage);
    }

    @Test
    public void mainTerrainWaterMasksTheProfilePassButNotTheUpperDimensionPass() throws Exception {
        maskEverywhere(40);
        IrisCaveCarver3D carver = installProfile();
        UpperDimensionContext upper = mock(UpperDimensionContext.class);
        when(upper.getEffectiveSurfaceY(anyInt(), anyInt())).thenReturn(100);
        when(engine.getUpperContext()).thenReturn(upper);
        dimension.setUpperDimensionCarving(true);
        List<Object[]> calls = new ArrayList<>();
        doAnswer(call -> {
            calls.add(new Object[]{call.getArgument(9), call.getArgument(10)});
            return 0;
        }).when(carver).carve(any(), anyInt(), anyInt(), any(), anyDouble(), anyDouble(), any(), any(), any(), any(), any(), any());

        component.generateLayer(writer, 2, 2, context);

        assertEquals(2, calls.size());
        int[] profileCeilings = (int[]) calls.get(0)[0];
        assertNotNull(profileCeilings);
        assertEquals(40 - CaveCeilingMask.ROOF, profileCeilings[0]);
        assertNull(calls.get(0)[1]);
        assertNull("the upper-dimension pass ignores main-terrain water", calls.get(1)[0]);
        assertNotNull(calls.get(1)[1]);
    }

    @Test
    public void subterrainFeaturesNeverOpenACellAboveTheCaveCeiling() {
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

    private IrisCaveCarver3D installProfile() throws Exception {
        IrisCaveProfile profile = new IrisCaveProfile().setEnabled(true);
        dimension.setCaveProfile(profile);
        IrisCaveCarver3D carver = mock(IrisCaveCarver3D.class);
        Map<IrisCaveProfile, IrisCaveCarver3D> carvers = new IdentityHashMap<>();
        carvers.put(profile, carver);
        Field cache = MantleCarvingComponent.class.getDeclaredField("profileCarvers");
        cache.setAccessible(true);
        cache.set(component, carvers);
        return carver;
    }
}
