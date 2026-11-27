package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.testsupport.MockPackData;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.IrisOreBands;
import art.arcane.iris.generation.decoration.IrisOreGenerator;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.value.IrisRange;
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

import static org.mockito.ArgumentMatchers.same;
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
    private final IrisData data = MockPackData.create();
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
                anyInt(), anyInt(), MockPackData.runtimeOf(data), any())).thenReturn(new KList<>(List.of(grass, dirt, dirt)));
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
                eq(90 - MIN_HEIGHT), eq(90 - MIN_HEIGHT), MockPackData.runtimeOf(data), any());
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
        IrisOreGenerator coalSeam = new IrisOreGenerator()
                .setPalette(new FixedPalette(coal))
                .setThreshold(1D)
                .setRange(new IrisRange(40 - MIN_HEIGHT, 40 - MIN_HEIGHT));
        when(biome.getSurfaceOreBands()).thenReturn(IrisOreBands.EMPTY);
        when(biome.getUndergroundOreBands()).thenReturn(IrisOreBands.of(new KList<>(List.of(coalSeam))));

        StructureStrata.Column column = IrisStructureStrata.world(engine).column(3, 5, 90, false);

        assertSame(coal, column.block(40));
        assertSame(rock, column.block(41));
    }

    private static final class FixedPalette extends IrisMaterialPalette {
        private final NativeBlockState state;

        private FixedPalette(NativeBlockState state) {
            this.state = state;
            setPalette(new KList<>(new IrisBlockData()));
        }

        @Override
        public NativeBlockState get(RNG rng, double x, double y, double z, PackRuntime runtime) {
            return state;
        }
    }

    private static NativeBlockState state(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        return state;
    }
}
