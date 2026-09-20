package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.IrisHydrology;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
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
import static org.mockito.Mockito.when;

public class IrisTerrainNormalActuatorStrataCutTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Test
    public void exposedBankCutDeeperThanThePaletteShowsTheDeepestAuthoredLayer() {
        Fixture fixture = new Fixture();

        Hunk<NativeBlockState> output = fixture.actuate();

        assertBlock(fixture.dirt, output.get(0, 40, 0));
        assertBlock(fixture.dirt, output.get(0, 39, 0));
    }

    @Test
    public void exposedBankBelowTheAuthoredSoilStillFallsThroughToRock() {
        Fixture fixture = new Fixture();

        Hunk<NativeBlockState> output = fixture.actuate();

        assertBlock(fixture.rock, output.get(0, 38, 0));
    }

    @Test
    public void uncutColumnsKeepTheirExactPaletteDepths() {
        Fixture fixture = new Fixture();

        Hunk<NativeBlockState> output = fixture.actuate();

        assertBlock(fixture.grass, output.get(0, 60, 0));
        assertBlock(fixture.dirt, output.get(0, 59, 0));
        assertBlock(fixture.rock, output.get(0, 58, 0));
    }

    @Test
    public void aCutClampsToTheDeepestAuthoredLayerAndNoFurther() {
        assertEquals(3, IrisTerrainNormalActuator.strataIndex(0, 6, 4));
        assertEquals(3, IrisTerrainNormalActuator.strataIndex(1, 6, 4));
        assertEquals(3, IrisTerrainNormalActuator.strataIndex(2, 6, 4));
        assertEquals(3, IrisTerrainNormalActuator.strataIndex(3, 6, 4));
        assertEquals(4, IrisTerrainNormalActuator.strataIndex(4, 6, 4));
    }

    @Test
    public void aModestCutStillShiftsTheStrataInsteadOfClamping() {
        assertEquals(1, IrisTerrainNormalActuator.strataIndex(0, 1, 4));
        assertEquals(2, IrisTerrainNormalActuator.strataIndex(1, 1, 4));
        assertEquals(3, IrisTerrainNormalActuator.strataIndex(2, 1, 4));
        assertEquals(3, IrisTerrainNormalActuator.strataIndex(3, 1, 4));
        assertEquals(4, IrisTerrainNormalActuator.strataIndex(4, 1, 4));
    }

    @Test
    public void aZeroCutIndexesTheBiomePaletteByDepthAlone() {
        for (int paletteSize = 0; paletteSize <= 16; paletteSize++) {
            for (int depth = 0; depth <= 32; depth++) {
                assertEquals(depth, IrisTerrainNormalActuator.strataIndex(depth, 0, paletteSize));
            }
        }
    }

    private static void assertBlock(NativeBlockState expected, NativeBlockState actual) {
        assertEquals(expected.key(), actual == null ? null : actual.key());
        assertSame(expected, actual);
    }

    /**
     * A bank column whose upper span is untouched (cut 0) and whose lower span lost four blocks to
     * erosion, over a two block soil palette.
     */
    private static final class Fixture {
        private final NativeBlockState rock = mock(NativeBlockState.class);
        private final NativeBlockState grass = mock(NativeBlockState.class);
        private final NativeBlockState dirt = mock(NativeBlockState.class);
        private final Engine engine = mock(Engine.class);
        private final ChunkContext context = mock(ChunkContext.class);

        private Fixture() {
            when(rock.key()).thenReturn("minecraft:stone");
            when(grass.key()).thenReturn("minecraft:grass_block");
            when(dirt.key()).thenReturn("minecraft:dirt");

            IrisComplex complex = mock(IrisComplex.class);
            IrisData data = mock(IrisData.class);
            IrisBiome biome = mock(IrisBiome.class);
            IrisRegion region = new IrisRegion();
            IrisDimension dimension = new IrisDimension()
                    .setHydrology(new IrisHydrology())
                    .setBedrock(false)
                    .setHideOresForHiddenOre(true);
            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getData()).thenReturn(data);
            when(engine.getSeedManager()).thenReturn(mock(SeedManager.class));
            when(complex.getImageMapRuntime()).thenReturn(mock(IrisImageMapRuntime.class));
            when(complex.getRiverWaterSurfaceStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 0D));
            when(complex.sampleHydrologyColumn(11, -4)).thenReturn(new HydrologyColumnSample(
                    11, -4, 80, 0, false, "parent", List.of(bankLayer())));

            Terrain3DColumn carved = mock(Terrain3DColumn.class);
            when(carved.spanCount()).thenReturn(2);
            when(carved.floor(1)).thenReturn(60);
            when(carved.ceiling(1)).thenReturn(50);
            when(carved.floor(0)).thenReturn(40);
            when(carved.ceiling(0)).thenReturn(0);
            Terrain3DColumn natural = mock(Terrain3DColumn.class);
            when(natural.surfaceY(60)).thenReturn(60);
            when(natural.surfaceY(40)).thenReturn(44);
            when(complex.terrainColumn(eq(11), eq(-4), any(HydrologyColumnSample.class))).thenReturn(carved);
            when(complex.naturalTerrainColumn(11, -4)).thenReturn(natural);

            when(context.getRoundedHeight(0, 0)).thenReturn(60);
            when(context.getBiome()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> biome, Interpolated.of(value -> 0D, value -> biome)), 11, -4, false));
            when(context.getRegion()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> region, Interpolated.of(value -> 0D, value -> region)), 11, -4, false));
            when(context.getRock()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> rock, Interpolated.of(value -> 0D, value -> rock)), 11, -4, false));
            when(biome.generateLayers(eq(dimension), anyDouble(), anyDouble(), any(RNG.class),
                    anyInt(), anyInt(), eq(data), eq(complex))).thenReturn(new KList<>(List.of(grass, dirt)));
            when(biome.generateCeilingLayers(eq(dimension), anyDouble(), anyDouble(), any(RNG.class),
                    anyInt(), anyInt(), eq(data), eq(complex))).thenReturn(new KList<>());
        }

        private Hunk<NativeBlockState> actuate() {
            Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 64, 1);
            new IrisTerrainNormalActuator(engine).terrainSliver(11, -4, 0, output, context);
            return output;
        }
    }

    private static HydrologyColumnLayer bankLayer() {
        return new HydrologyColumnLayer(
                new HydrologyFeatureRef(21L, HydrologyFeatureType.RIFFLE, 22L, 23L, 11, 63, -4, 1, 0, false),
                60,
                63,
                63,
                false,
                false,
                true,
                false,
                false,
                false,
                true,
                false,
                false,
                "default",
                "river",
                "mouth",
                "shore",
                "bank",
                "cave"
        );
    }
}
