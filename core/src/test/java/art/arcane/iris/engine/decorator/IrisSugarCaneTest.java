package art.arcane.iris.engine.decorator;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.hydrology.HydrologyColumnLayer;
import art.arcane.iris.engine.hydrology.HydrologyColumnSample;
import art.arcane.iris.engine.hydrology.HydrologyFeatureRef;
import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDecorator;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.iris.util.project.stream.interpolation.Interpolated;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IrisSugarCaneTest {
    @Test
    public void localCaneRequiresSuitableSubstrateAndWaterAtItsBase() {
        Fixture fixture = new Fixture(3);
        fixture.output.set(1, 3, 1, fixture.soil);
        assertFalse(fixture.place(1, 1));
        fixture.output.set(0, 4, 1, fixture.water);
        assertFalse(fixture.place(1, 1));
        fixture.output.set(0, 3, 1, fixture.water);
        assertTrue(fixture.place(1, 1));
        when(fixture.cane.canPlaceOnto(fixture.soil)).thenReturn(false);
        assertFalse(fixture.place(1, 1));
        verifyNoInteractions(fixture.engine);
    }

    @Test
    public void lavaAndOrdinaryIceDoNotHydrateCane() {
        Fixture fixture = new Fixture(3);
        fixture.output.set(1, 3, 1, fixture.soil);
        for (String key : new String[]{"minecraft:lava", "minecraft:ice", "minecraft:stone"}) {
            fixture.output.set(0, 3, 1, state(key));
            assertFalse(key, fixture.place(1, 1));
        }
        fixture.output.set(0, 3, 1, state("minecraft:frosted_ice[age=0]"));
        assertTrue(fixture.place(1, 1));
        PlatformBlockState waterlogged = state("minecraft:oak_stairs[waterlogged=true]");
        when(waterlogged.isWaterLogged()).thenReturn(true);
        fixture.output.set(0, 3, 1, waterlogged);
        assertTrue(fixture.place(1, 1));
    }

    @Test
    public void caneStackKeepsItsValidBaseAndCannotReplaceWater() {
        Fixture fixture = new Fixture(3);
        when(fixture.cane.canPlaceOnto(fixture.cane)).thenReturn(true);
        fixture.output.set(1, 3, 1, fixture.cane);
        assertFalse(fixture.place(1, 1));
        fixture.output.set(1, 2, 1, fixture.soil);
        fixture.output.set(0, 2, 1, fixture.water);
        assertTrue(fixture.place(1, 1));
        fixture.output.set(1, 4, 1, fixture.water);
        assertFalse(fixture.place(1, 1));
    }

    @Test
    public void crossChunkWaterUsesAcceptedHydrologyAtTheCorrectElevation() {
        Fixture fixture = new Fixture(1);
        fixture.plannedTerrain();
        fixture.output.set(0, 3, 0, fixture.soil);
        HydrologyColumnLayer layer = wetLayer(1, 3);
        when(fixture.complex.sampleHydrologyColumn(-17, -16)).thenReturn(new HydrologyColumnSample(
                -17, -16, 6, 0, false, "parent", List.of(layer)));
        when(fixture.complex.resolveHydrologyFluid("water", -17, -16)).thenReturn(fixture.water);

        assertTrue(fixture.place(0, 0));
        PlatformBlockState lava = state("minecraft:lava");
        when(fixture.complex.resolveHydrologyFluid("water", -17, -16)).thenReturn(lava);
        assertFalse(fixture.place(0, 0));
        when(fixture.complex.sampleHydrologyColumn(-17, -16)).thenReturn(new HydrologyColumnSample(
                -17, -16, 6, 0, false, "parent", List.of(wetLayer(1, 2))));
        assertFalse(fixture.place(0, 0));
    }

    @Test
    public void localBlocksOverridePlannedWaterWithoutReadingAnotherChunk() {
        Fixture fixture = new Fixture(3);
        fixture.output.set(1, 3, 1, fixture.soil);
        fixture.output.set(0, 3, 1, state("minecraft:stone"));
        assertFalse(fixture.place(1, 1));
        verifyNoInteractions(fixture.engine);
    }

    @Test
    public void forcedSurfaceDecorationStillRejectsCaneOnStone() {
        Fixture fixture = new Fixture(3);
        IrisDecorator decorator = mock(IrisDecorator.class);
        PlatformBlockState stone = state("minecraft:stone");
        PlatformBlockState air = state("minecraft:air");
        when(air.isAir()).thenReturn(true);
        when(decorator.isForcePlace()).thenReturn(true);
        when(decorator.pickBlockData(any(), any(), anyDouble(), anyDouble())).thenReturn(fixture.cane);
        fixture.output.set(1, 3, 1, stone);
        fixture.output.set(1, 4, 1, air);
        fixture.output.set(0, 3, 1, fixture.water);

        DecoratorCore.placeSurfaceSingle(decorator, 1, 1, -15, 3, -15,
                fixture.output, new RNG(3L), null, false, false, null);

        assertSame(air, fixture.output.get(1, 4, 1));
    }

    @Test
    public void naturalCrossChunkWaterHonorsAuthoredSeaLayers() {
        Fixture fixture = new Fixture(1);
        fixture.plannedTerrain();
        fixture.output.set(0, 3, 0, fixture.soil);
        when(fixture.complex.getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 1D));
        when(fixture.complex.getRiverWaterSurfaceStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 3D));
        IrisBiome biome = mock(IrisBiome.class);
        when(fixture.complex.getTrueBiomeStream()).thenReturn(
                ProceduralStream.of((x, z) -> biome, Interpolated.of(value -> 0D, value -> biome)));
        when(fixture.engine.getSeedManager()).thenReturn(mock(SeedManager.class));
        PlatformBlockState ice = state("minecraft:ice");
        when(biome.generateSeaLayers(anyDouble(), anyDouble(), any(), anyInt(), any()))
                .thenReturn(new KList<>(List.of(ice)));
        assertFalse(fixture.place(0, 0));
        when(biome.generateSeaLayers(anyDouble(), anyDouble(), any(), anyInt(), any()))
                .thenReturn(new KList<>(List.of(fixture.water)));
        assertTrue(fixture.place(0, 0));
    }

    private static HydrologyColumnLayer wetLayer(int bed, int head) {
        HydrologyFeatureRef feature = new HydrologyFeatureRef(
                11L, HydrologyFeatureType.RIFFLE, 12L, 13L, -17, head, -16, 1, 0, false);
        return new HydrologyColumnLayer(feature, bed, head, head,
                true, false, false, true, false, false, true, true, false,
                "water", "river", "mouth", "shore", "bank", "cave");
    }

    private static PlatformBlockState state(String key) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        return state;
    }

    private static final class Fixture {
        private final Engine engine = mock(Engine.class);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final PlatformBlockState cane = state("minecraft:sugar_cane[age=0]");
        private final PlatformBlockState soil = state("minecraft:dirt");
        private final PlatformBlockState water = state("minecraft:water");
        private final Hunk<PlatformBlockState> output;

        private Fixture(int size) {
            output = Hunk.newArrayHunk(size, 6, size);
            when(cane.canPlaceOnto(soil)).thenReturn(true);
            when(water.isWater()).thenReturn(true);
        }

        private void plannedTerrain() {
            when(engine.getComplex()).thenReturn(complex);
            when(complex.getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 3D));
            when(complex.getRiverWaterSurfaceStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 0D));
        }

        private boolean place(int x, int z) {
            return IrisSugarCane.canPlace(cane, output, x, 4, z, -16 + x, -16 + z, engine);
        }
    }
}
