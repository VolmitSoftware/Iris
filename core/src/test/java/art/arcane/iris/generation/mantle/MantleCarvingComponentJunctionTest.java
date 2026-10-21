package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MantleCarvingComponentJunctionTest {
    @Test
    public void twoCaveProfilesBlendAcrossBothSidesOfAChunkBoundary() throws Exception {
        Fixture fixture = fixture(false);
        fixture.generate(false);

        assertTrue(fixture.weight(-1, fixture.west(), 15, 12) > 0D);
        assertTrue(fixture.weight(-1, fixture.east(), 15, 12) > 0D);
        assertTrue(fixture.weight(0, fixture.west(), 0, 12) > 0D);
        assertTrue(fixture.weight(0, fixture.east(), 0, 12) > 0D);
        assertEquals(fixture.weight(-1, fixture.west(), 15, 12),
                fixture.weight(0, fixture.east(), 0, 12), 0D);
        assertEquals(fixture.weight(-1, fixture.east(), 15, 12),
                fixture.weight(0, fixture.west(), 0, 12), 0D);
    }

    @Test
    public void thirdProfileOutsideTheBlendRadiusDoesNotChangeBoundaryWeights() throws Exception {
        Fixture control = fixture(false);
        Fixture junction = fixture(true);
        control.generate(false);
        junction.generate(false);

        assertEquals("A third biome outside the local blend radius must not remove the east cave from the west chunk",
                control.weight(-1, control.east(), 15, 12),
                junction.weight(-1, junction.east(), 15, 12), 0D);
        assertEquals("A third biome outside the local blend radius must not remove the west cave from the east chunk",
                control.weight(0, control.west(), 0, 12),
                junction.weight(0, junction.west(), 0, 12), 0D);
    }

    @Test
    public void threeWayJunctionRetainsEachProfileOnlyInsideItsLocalBlend() throws Exception {
        Fixture fixture = fixture(true);
        fixture.generate(false);

        for (int chunkX : new int[]{-1, 0}) {
            int boundaryX = chunkX == -1 ? 15 : 0;
            assertEquals(3, fixture.captured().get(chunkX).size());
            assertTrue(fixture.weight(chunkX, fixture.west(), boundaryX, 6) > 0D);
            assertTrue(fixture.weight(chunkX, fixture.east(), boundaryX, 6) > 0D);
            assertTrue(fixture.weight(chunkX, fixture.north(), boundaryX, 6) > 0D);
            assertEquals(0D, fixture.weight(chunkX, fixture.north(), boundaryX, 12), 0D);
            assertEquals(0D, fixture.weight(chunkX, fixture.west(), boundaryX, 0), 0D);
            assertEquals(0D, fixture.weight(chunkX, fixture.east(), boundaryX, 0), 0D);
            assertEquals(1D, fixture.weight(chunkX, fixture.north(), boundaryX, 0), 0D);
        }
    }

    @Test
    public void junctionWeightsRemainStableAcrossGenerationOrder() throws Exception {
        Fixture forward = fixture(true);
        Fixture reverse = fixture(true);
        forward.generate(false);
        reverse.generate(true);

        for (int chunkX : new int[]{-1, 0}) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    assertEquals(forward.weight(chunkX, forward.west(), x, z),
                            reverse.weight(chunkX, reverse.west(), x, z), 0D);
                    assertEquals(forward.weight(chunkX, forward.east(), x, z),
                            reverse.weight(chunkX, reverse.east(), x, z), 0D);
                    assertEquals(forward.weight(chunkX, forward.north(), x, z),
                            reverse.weight(chunkX, reverse.north(), x, z), 0D);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(boolean thirdProfile) throws ReflectiveOperationException {
        Engine engine = mock(Engine.class);
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getEngine()).thenReturn(engine);
        MantleCarvingComponent component = new MantleCarvingComponent(engineMantle);
        when(engine.getDimension()).thenReturn(new IrisDimension());
        when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(0).maxHeight(128).build());
        when(engine.getMetrics()).thenReturn(new EngineMetrics(16));
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.allowsMantleChunkWrite(anyInt(), anyInt())).thenReturn(true);
        ProceduralStream<Double> heights = mock(ProceduralStream.class);
        when(heights.getDouble(anyDouble(), anyDouble())).thenReturn(100D);
        when(complex.getNaturalHeightStream()).thenReturn(heights);
        ProceduralStream<IrisRegion> regions = mock(ProceduralStream.class);
        ProceduralStream<IrisBiome> surfaces = mock(ProceduralStream.class);
        ProceduralStream<IrisBiome> caves = mock(ProceduralStream.class);
        ProceduralStream<NativeBlockState> fluids = mock(ProceduralStream.class);
        when(complex.getRegionStream()).thenReturn(regions);
        when(complex.getNaturalTrueBiomeStream()).thenReturn(surfaces);
        when(complex.getCaveBiomeStream()).thenReturn(caves);
        when(complex.getFluidStream()).thenReturn(fluids);
        IrisCaveProfile west = new IrisCaveProfile().setEnabled(true);
        IrisCaveProfile east = new IrisCaveProfile().setEnabled(true);
        IrisCaveProfile north = new IrisCaveProfile().setEnabled(true);
        IrisBiome westBiome = new IrisBiome().setCaveProfile(west);
        IrisBiome eastBiome = new IrisBiome().setCaveProfile(east);
        IrisBiome northBiome = new IrisBiome().setCaveProfile(north);
        when(caves.get(anyDouble(), anyDouble())).thenAnswer(call -> {
            double x = call.getArgument(0);
            double z = call.getArgument(1);
            return thirdProfile && z < 7D ? northBiome : x < 0D ? westBiome : eastBiome;
        });
        ChunkContext context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(complex);
        MantleWriter writer = mock(MantleWriter.class);
        Mantle<Matter> storage = mock(Mantle.class);
        when(storage.getWorldHeight()).thenReturn(128);
        when(writer.getMantle()).thenReturn(storage);
        Map<Integer, Map<IrisCaveProfile, double[]>> captured = new HashMap<>();
        Map<IrisCaveProfile, IrisCaveCarver3D> carvers = new IdentityHashMap<>();
        for (IrisCaveProfile profile : new IrisCaveProfile[]{west, east, north}) {
            IrisCaveCarver3D carver = mock(IrisCaveCarver3D.class);
            doAnswer(call -> {
                int chunkX = call.getArgument(1);
                double[] weights = call.getArgument(3);
                captured.computeIfAbsent(chunkX, ignored -> new IdentityHashMap<>()).put(profile, weights.clone());
                return 0;
            }).when(carver).carve(any(), anyInt(), anyInt(), any(), anyDouble(), anyDouble(),
                    any(), any(), any(), any(), any());
            carvers.put(profile, carver);
        }
        Field profileCarvers = MantleCarvingComponent.class.getDeclaredField("profileCarvers");
        profileCarvers.setAccessible(true);
        profileCarvers.set(component, carvers);
        return new Fixture(component, context, writer, west, east, north, captured);
    }

    private record Fixture(MantleCarvingComponent component, ChunkContext context, MantleWriter writer,
                           IrisCaveProfile west, IrisCaveProfile east, IrisCaveProfile north,
                           Map<Integer, Map<IrisCaveProfile, double[]>> captured) {
        private void generate(boolean reverse) {
            component.generateLayer(writer, reverse ? 0 : -1, 0, context);
            component.generateLayer(writer, reverse ? -1 : 0, 0, context);
        }

        private double weight(int chunkX, IrisCaveProfile profile, int x, int z) {
            double[] weights = captured.get(chunkX).get(profile);
            return weights == null ? 0D : weights[(x << 4) | z];
        }
    }
}
