package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisOreBands;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.stage.IrisTerrainNormalActuator;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStrata;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisStructureStrataTest {
    private static final int MIN_HEIGHT = -64;

    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    private final NativeBlockState rock = state("minecraft:stone");
    private final NativeBlockState grass = state("minecraft:grass_block");
    private final NativeBlockState dirt = state("minecraft:dirt");
    private final Engine engine = mock(Engine.class);
    private final IrisComplex complex = mock(IrisComplex.class);
    private final IrisBiome biome = mock(IrisBiome.class);
    private final IrisImageMapRuntime imageMap = mock(IrisImageMapRuntime.class);
    private final IrisData data = mock(IrisData.class);
    private final IrisDimension dimension = new IrisDimension().setBedrock(true).setHideOresForHiddenOre(true);

    @Before
    public void wireEngine() {
        IrisRegion region = new IrisRegion();
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getData()).thenReturn(data);
        when(engine.getSeedManager()).thenReturn(mock(SeedManager.class));
        when(engine.getMinHeight()).thenReturn(MIN_HEIGHT);
        when(complex.getTrueBiomeStream()).thenReturn(ProceduralStream.of((x, z) -> biome, Interpolated.of(value -> 0D, value -> biome)));
        when(complex.getRegionStream()).thenReturn(ProceduralStream.of((x, z) -> region, Interpolated.of(value -> 0D, value -> region)));
        when(complex.getRockStream()).thenReturn(ProceduralStream.of((x, z) -> rock, Interpolated.of(value -> 0D, value -> rock)));
        when(complex.getImageMapRuntime()).thenReturn(imageMap);
        when(biome.generateLayersWithSlope(eq(dimension), anyDouble(), anyDouble(), any(RNG.class),
                anyInt(), anyInt(), eq(data), any())).thenReturn(new KList<>(List.of(grass, dirt, dirt)));
    }

    @Test
    public void aColumnPaintsTheBiomeSurfaceThenSoilThenRockDownFromItsSurface() {
        StructureStrata.Column column = IrisStructureStrata.world(engine).column(3, 5, 90, false);

        assertSame(grass, column.block(90));
        assertSame(dirt, column.block(89));
        assertSame(dirt, column.block(88));
        assertSame(rock, column.block(87));
        assertSame(rock, column.block(40));
        assertEquals(3, column.soilDepth());
    }

    @Test
    public void layersComeFromTheInternalSurfaceHeight() {
        IrisStructureStrata.world(engine).column(3, 5, 90, false);

        verify(biome).generateLayersWithSlope(eq(dimension), eq(3D), eq(5D), any(RNG.class),
                eq(90 - MIN_HEIGHT), eq(90 - MIN_HEIGHT), eq(data), any());
    }

    @Test
    public void aMappedSurfaceReplacesOnlyTheTopBlock() {
        NativeBlockState mapped = state("minecraft:podzol");
        when(imageMap.sampleSurfaceBlock(3, 5)).thenReturn(mapped);

        StructureStrata.Column column = IrisStructureStrata.world(engine).column(3, 5, 90, false);

        assertSame(mapped, column.block(90));
        assertSame(dirt, column.block(89));
    }

    @Test
    public void aSubmergedSurfaceTakesSoilInsteadOfLivingGrass() {
        StructureStrata.Column column = IrisStructureStrata.world(engine).column(3, 5, 50, true);

        assertSame(IrisTerrainNormalActuator.submergedSoil(grass), column.block(50));
    }

    @Test
    public void oreBandsAreSampledAtEachHeightSoAFillNeverStretchesOneOreBlock() {
        dimension.setHideOresForHiddenOre(false);
        NativeBlockState coal = state("minecraft:coal_ore");
        IrisOreBands bands = mock(IrisOreBands.class);
        when(biome.getSurfaceOreBands()).thenReturn(IrisOreBands.EMPTY);
        when(biome.getUndergroundOreBands()).thenReturn(bands);
        when(bands.contains(anyInt())).thenReturn(true);
        when(bands.generate(eq(3), eq(40 - MIN_HEIGHT), eq(5), any(RNG.class), eq(data), any())).thenReturn(coal);

        StructureStrata.Column column = IrisStructureStrata.world(engine).column(3, 5, 90, false);

        assertSame(coal, column.block(40));
        assertSame(rock, column.block(41));
    }

    private static NativeBlockState state(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        return state;
    }
}
