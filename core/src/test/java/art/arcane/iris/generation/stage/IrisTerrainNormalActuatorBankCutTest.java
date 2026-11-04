package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
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

public class IrisTerrainNormalActuatorBankCutTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Test
    public void aBankCutPaintsTheBiomeLayersFromItsNewSurface() {
        Fixture fixture = new Fixture(WaterRole.BANK);

        Hunk<NativeBlockState> output = fixture.actuate();

        assertBlock(fixture.grass, output.get(0, 40, 0));
        assertBlock(fixture.dirt, output.get(0, 39, 0));
        assertBlock(fixture.rock, output.get(0, 38, 0));
    }

    @Test
    public void uncutColumnsKeepTheirExactPaletteDepths() {
        Fixture fixture = new Fixture(WaterRole.BANK);

        Hunk<NativeBlockState> output = fixture.actuate();

        assertBlock(fixture.grass, output.get(0, 60, 0));
        assertBlock(fixture.dirt, output.get(0, 59, 0));
        assertBlock(fixture.rock, output.get(0, 58, 0));
    }

    @Test
    public void everyRoleKeepsItsPaletteAtTheCut() {
        for (WaterRole role : List.of(WaterRole.NONE, WaterRole.CHANNEL, WaterRole.POND,
                WaterRole.BANK, WaterRole.SHORE, WaterRole.POND_SHORE, WaterRole.DAM)) {
            Fixture fixture = new Fixture(role);

            Hunk<NativeBlockState> output = fixture.actuate();

            assertBlock(fixture.grass, output.get(0, 40, 0));
            assertBlock(fixture.dirt, output.get(0, 39, 0));
            assertBlock(fixture.rock, output.get(0, 38, 0));
        }
    }

    private static void assertBlock(NativeBlockState expected, NativeBlockState actual) {
        assertEquals(expected.key(), actual == null ? null : actual.key());
        assertSame(expected, actual);
    }

    /**
     * A column whose upper span is untouched (cut 0) and whose lower span lost four blocks to
     * erosion, over a two block soil palette.
     */
    private static final class Fixture {
        private final NativeBlockState rock = state("minecraft:stone");
        private final NativeBlockState grass = state("minecraft:grass_block");
        private final NativeBlockState dirt = state("minecraft:dirt");
        private final Engine engine = mock(Engine.class);
        private final ChunkContext context = mock(ChunkContext.class);
        private final IrisComplex complex = mock(IrisComplex.class);

        private Fixture(WaterRole role) {
            IrisData data = mock(IrisData.class);
            IrisBiome biome = mock(IrisBiome.class);
            IrisRegion region = new IrisRegion();
            IrisDimension dimension = new IrisDimension()
                    .setBedrock(false)
                    .setHideOresForHiddenOre(true);
            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getData()).thenReturn(data);
            when(engine.getSeedManager()).thenReturn(mock(SeedManager.class));
            when(complex.getImageMapRuntime()).thenReturn(mock(IrisImageMapRuntime.class));
            when(complex.getWaterHeadStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 0D));
            when(complex.getWaterRoleStream()).thenReturn(ProceduralStream.of((x, z) -> role, Interpolated.of(value -> 0D, value -> role)));
            when(complex.resolveWaterMaterials(11, -4)).thenReturn(new IrisHydrologyMaterials());

            Terrain3DColumn carved = mock(Terrain3DColumn.class);
            when(carved.spanCount()).thenReturn(2);
            when(carved.floor(1)).thenReturn(60);
            when(carved.ceiling(1)).thenReturn(50);
            when(carved.floor(0)).thenReturn(40);
            when(carved.ceiling(0)).thenReturn(0);
            Terrain3DColumn natural = mock(Terrain3DColumn.class);
            when(natural.surfaceY(60)).thenReturn(60);
            when(natural.surfaceY(40)).thenReturn(44);
            when(complex.terrainColumn(11, -4)).thenReturn(carved);
            when(complex.naturalTerrainColumn(11, -4)).thenReturn(natural);

            when(context.getRoundedHeight(0, 0)).thenReturn(60);
            when(context.getBiome()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> biome, Interpolated.of(value -> 0D, value -> biome)), 11, -4, false));
            when(context.getRegion()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> region, Interpolated.of(value -> 0D, value -> region)), 11, -4, false));
            when(context.getRock()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> rock, Interpolated.of(value -> 0D, value -> rock)), 11, -4, false));
            when(context.getFluid()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> null, Interpolated.of(value -> 0D, value -> null)), 11, -4, false));
            when(biome.generateLayers(eq(dimension), anyDouble(), anyDouble(), any(RNG.class),
                    anyInt(), anyInt(), eq(data), eq(complex))).thenReturn(new KList<>(List.of(grass, dirt)));
            when(biome.generateCeilingLayers(eq(dimension), anyDouble(), anyDouble(), any(RNG.class),
                    anyInt(), anyInt(), eq(data), eq(complex))).thenReturn(new KList<>());
        }

        private Hunk<NativeBlockState> actuate() {
            Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 64, 1);
            new IrisTerrainNormalActuator(engine).paint(11, -4, output, context);
            return output;
        }
    }

    private static NativeBlockState state(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        return state;
    }
}
