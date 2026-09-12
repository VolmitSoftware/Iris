package art.arcane.iris.generation.stage;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.decoration.IrisDecorationStep;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.hydrology.IrisHydrology;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.generation.hydrology.IrisRiverMaterialConfig;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisTerrainNormalActuatorHydrologyTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();


    @Test
    public void hydrologyOwnedFluidBypassesBiomeSeaLayers() {
        PlatformBlockState water = mock(PlatformBlockState.class);
        PlatformBlockState ice = mock(PlatformBlockState.class);
        KList<PlatformBlockState> seaLayers = new KList<>();
        seaLayers.add(ice);

        assertSame(water, HydrologyFluidLayerSelector.select(seaLayers, 0, water, true));
        assertSame(ice, HydrologyFluidLayerSelector.select(seaLayers, 0, water, false));
        assertSame(water, HydrologyFluidLayerSelector.select(seaLayers, 1, water, false));
    }

    @Test
    public void frozenTerrainPublishesSourceWaterBeforeStandardTopLayerFreezing() {
        PlatformBlockState water = mock(PlatformBlockState.class);
        PlatformBlockState ice = mock(PlatformBlockState.class);
        when(water.key()).thenReturn("minecraft:water");
        when(water.isWater()).thenReturn(true);
        when(ice.key()).thenReturn("minecraft:ice");
        KList<PlatformBlockState> frozenSeaLayers = new KList<>();
        frozenSeaLayers.add(ice);
        HydrologyFeatureRef feature = new HydrologyFeatureRef(
                11L,
                HydrologyFeatureType.SURFACE_POOL,
                12L,
                13L,
                4,
                70,
                6,
                1,
                0,
                true
        );
        HydrologyColumnLayer acceptedLayer = new HydrologyColumnLayer(
                feature,
                67,
                70,
                70,
                true,
                false,
                false,
                true,
                false,
                false,
                true,
                true,
                false,
                "default",
                "frozen_river",
                "frozen_mouth",
                "frozen_shore",
                "frozen_dry",
                "frozen_cave"
        );
        HydrologyColumnSample acceptedColumn = new HydrologyColumnSample(
                4,
                6,
                76,
                63,
                false,
                "frozen_parent",
                List.of(acceptedLayer)
        );
        boolean hydrologyOwned = acceptedColumn.primarySurfaceFluidLayer().isPresent();
        ArrayList<String> stages = new ArrayList<>();

        PlatformBlockState state = HydrologyFluidLayerSelector.select(
                frozenSeaLayers,
                0,
                water,
                hydrologyOwned
        );
        stages.add("hydrology");
        assertTrue(hydrologyOwned);
        assertSame(water, state);
        assertEquals("minecraft:water", state.key());
        assertTrue(state.isWater());
        assertFalse(acceptedLayer.fallingFluid());

        for (IrisDecorationStep step : IrisDecorationStep.values()) {
            if (step == IrisDecorationStep.TOP_LAYER_MODIFICATION) {
                assertSame(water, state);
                state = ice;
                stages.add(step.getSerializedName());
            }
        }

        assertEquals(List.of("hydrology", "top_layer_modification"), stages);
        assertSame(ice, state);
        assertEquals("minecraft:ice", state.key());
        assertFalse(state.isWater());
    }

    @Test
    public void bedMaterialPaintsTheChannelBedToItsDepth() {
        Fixture fixture = new Fixture();
        IrisRiverMaterialConfig bedMaterial = fixture.enabled(2);
        IrisRiverMaterialConfig shoreMaterial = fixture.disabled();
        IrisRiverMaterialConfig bankMaterial = fixture.disabled();
        HydrologyColumnLayer channel = channelLayer();

        IrisRiverMaterialConfig role = IrisTerrainNormalActuator.hydrologyRoleMaterial(
                channel, bedMaterial, shoreMaterial, bankMaterial);

        assertSame(bedMaterial, role);
        assertSame(fixture.painted, fixture.paint(role, 0));
        assertSame(fixture.painted, fixture.paint(role, 1));
        assertSame(fixture.biomeLayer, fixture.paint(role, 2));
        assertNull(IrisTerrainNormalActuator.hydrologyRoleMaterial(
                apronLayer(), bedMaterial, shoreMaterial, bankMaterial));
        assertNull(IrisTerrainNormalActuator.hydrologyRoleMaterial(
                null, bedMaterial, shoreMaterial, bankMaterial));
    }

    @Test
    public void shoreMaterialPaintsOnlyShoreColumns() {
        Fixture fixture = new Fixture();
        IrisRiverMaterialConfig bedMaterial = fixture.disabled();
        IrisRiverMaterialConfig shoreMaterial = fixture.enabled(1);
        IrisRiverMaterialConfig bankMaterial = fixture.disabled();

        assertSame(shoreMaterial, IrisTerrainNormalActuator.hydrologyRoleMaterial(
                shoreLayer(), bedMaterial, shoreMaterial, bankMaterial));
        assertNull(IrisTerrainNormalActuator.hydrologyRoleMaterial(
                channelLayer(), bedMaterial, shoreMaterial, bankMaterial));
        assertNull(IrisTerrainNormalActuator.hydrologyRoleMaterial(
                bankLayer(), bedMaterial, shoreMaterial, bankMaterial));
        assertSame(fixture.painted, fixture.paint(shoreMaterial, 0));
        assertSame(fixture.biomeLayer, fixture.paint(shoreMaterial, 1));
    }

    @Test
    public void bankMaterialPaintsOnlyBankColumns() {
        Fixture fixture = new Fixture();
        IrisRiverMaterialConfig bedMaterial = fixture.disabled();
        IrisRiverMaterialConfig shoreMaterial = fixture.disabled();
        IrisRiverMaterialConfig bankMaterial = fixture.enabled(3);

        assertSame(bankMaterial, IrisTerrainNormalActuator.hydrologyRoleMaterial(
                bankLayer(), bedMaterial, shoreMaterial, bankMaterial));
        assertNull(IrisTerrainNormalActuator.hydrologyRoleMaterial(
                shoreLayer(), bedMaterial, shoreMaterial, bankMaterial));
        assertNull(IrisTerrainNormalActuator.hydrologyRoleMaterial(
                channelLayer(), bedMaterial, shoreMaterial, bankMaterial));
        assertSame(fixture.painted, fixture.paint(bankMaterial, 2));
        assertSame(fixture.biomeLayer, fixture.paint(bankMaterial, 3));
    }

    @Test
    public void disabledMaterialsKeepTheBiomeLayers() {
        Fixture fixture = new Fixture();
        IrisRiverMaterialConfig bedMaterial = fixture.disabled();
        IrisRiverMaterialConfig shoreMaterial = fixture.disabled();
        IrisRiverMaterialConfig bankMaterial = fixture.disabled();

        for (HydrologyColumnLayer layer : List.of(channelLayer(), shoreLayer(), bankLayer())) {
            IrisRiverMaterialConfig disabledRole = IrisTerrainNormalActuator.hydrologyRoleMaterial(
                    layer, bedMaterial, shoreMaterial, bankMaterial);
            IrisRiverMaterialConfig absentRole = IrisTerrainNormalActuator.hydrologyRoleMaterial(
                    layer, null, null, null);
            assertNull(disabledRole);
            assertNull(absentRole);
            for (int depth = 0; depth < 4; depth++) {
                assertSame(fixture.biomeLayer, fixture.paint(disabledRole, depth));
                assertSame(fixture.biomeLayer, fixture.paint(absentRole, depth));
            }
        }

        verify(fixture.palette, never()).get(any(RNG.class), anyDouble(), anyDouble(), anyDouble(), any());
    }

    @Test
    public void deepBankAndShoreCutsStillReceiveTheirSurfaceMaterial() {
        for (HydrologyColumnLayer layer : List.of(shoreLayer(), bankLayer())) {
            Engine engine = mock(Engine.class);
            IrisComplex complex = mock(IrisComplex.class);
            IrisData data = mock(IrisData.class);
            IrisBiome biome = mock(IrisBiome.class);
            IrisRegion region = new IrisRegion();
            ChunkContext context = mock(ChunkContext.class);
            PlatformBlockState rock = mock(PlatformBlockState.class);
            PlatformBlockState grass = mock(PlatformBlockState.class);
            PlatformBlockState painted = mock(PlatformBlockState.class);
            when(rock.key()).thenReturn("minecraft:stone");
            when(grass.key()).thenReturn("minecraft:grass_block");
            when(painted.key()).thenReturn("minecraft:dirt");
            IrisMaterialPalette palette = mock(IrisMaterialPalette.class);
            when(palette.get(any(RNG.class), anyDouble(), anyDouble(), anyDouble(), eq(data))).thenReturn(painted);
            IrisRiverMaterialConfig material = new IrisRiverMaterialConfig().setEnabled(true).setDepth(2).setPalette(palette);
            IrisHydrology hydrology = new IrisHydrology();
            hydrology.getRivers().getSurface().getBanks().setShoreMaterial(material).setBankMaterial(material);
            IrisDimension dimension = new IrisDimension().setHydrology(hydrology).setBedrock(false).setHideOresForHiddenOre(true);
            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getData()).thenReturn(data);
            when(engine.getSeedManager()).thenReturn(mock(SeedManager.class));
            when(complex.getImageMapRuntime()).thenReturn(mock(IrisImageMapRuntime.class));
            when(complex.getRiverWaterSurfaceStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 0D));
            when(complex.sampleHydrologyColumn(11, -4)).thenReturn(new HydrologyColumnSample(
                    11, -4, 80, 0, false, "parent", List.of(layer)));
            when(context.getRoundedHeight(0, 0)).thenReturn(60);
            when(context.getBiome()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> biome, Interpolated.of(value -> 0D, value -> biome)), 11, -4, false));
            when(context.getRegion()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> region, Interpolated.of(value -> 0D, value -> region)), 11, -4, false));
            when(context.getRock()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> rock, Interpolated.of(value -> 0D, value -> rock)), 11, -4, false));
            when(biome.generateLayers(eq(dimension), anyDouble(), anyDouble(), any(RNG.class),
                    anyInt(), anyInt(), eq(data), eq(complex))).thenReturn(new KList<>(List.of(grass)));
            Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 64, 1);

            new IrisTerrainNormalActuator(engine).terrainSliver(11, -4, 0, output, context);

            assertSame(painted, output.get(0, 60, 0));
            assertSame(painted, output.get(0, 59, 0));
            assertSame(rock, output.get(0, 58, 0));
            material.setEnabled(false);
            new IrisTerrainNormalActuator(engine).terrainSliver(11, -4, 0, output, context);
            assertSame(rock, output.get(0, 60, 0));

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
            when(biome.generateCeilingLayers(eq(dimension), anyDouble(), anyDouble(), any(RNG.class),
                    anyInt(), anyInt(), eq(data), eq(complex))).thenReturn(new KList<>());
            new IrisTerrainNormalActuator(engine).terrainSliver(11, -4, 0, output, context);
            assertSame(grass, output.get(0, 60, 0));
            assertSame(rock, output.get(0, 40, 0));
            material.setEnabled(true);
            new IrisTerrainNormalActuator(engine).terrainSliver(11, -4, 0, output, context);
            assertSame(painted, output.get(0, 60, 0));
            assertSame(painted, output.get(0, 40, 0));
            assertSame(rock, output.get(0, 38, 0));
        }
    }

    private static HydrologyColumnLayer channelLayer() {
        return surfaceLayer(true, false);
    }

    private static HydrologyColumnLayer shoreLayer() {
        return surfaceLayer(false, true);
    }

    private static HydrologyColumnLayer bankLayer() {
        return surfaceLayer(false, false);
    }

    private static HydrologyColumnLayer surfaceLayer(boolean channel, boolean shore) {
        return new HydrologyColumnLayer(
                surfaceFeature(),
                60,
                63,
                63,
                channel,
                shore,
                !channel,
                channel,
                false,
                false,
                true,
                channel,
                false,
                "default",
                "river",
                "mouth",
                "shore",
                "bank",
                "cave"
        );
    }

    private static HydrologyColumnLayer apronLayer() {
        return new HydrologyColumnLayer(
                surfaceFeature(),
                63,
                63,
                63,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                "default",
                "river",
                "mouth",
                "shore",
                "bank",
                "cave"
        );
    }

    private static HydrologyFeatureRef surfaceFeature() {
        return new HydrologyFeatureRef(21L, HydrologyFeatureType.RIFFLE, 22L, 23L, 11, 63, -4, 1, 0, false);
    }

    private static final class Fixture {
        private final PlatformBlockState biomeLayer = mock(PlatformBlockState.class);
        private final PlatformBlockState painted = mock(PlatformBlockState.class);
        private final IrisMaterialPalette palette = mock(IrisMaterialPalette.class);
        private final IrisData data = mock(IrisData.class);
        private final RNG rng = new RNG(7L);

        private Fixture() {
            when(palette.get(same(rng), eq(11.0), eq(63.0), eq(-4.0), same(data))).thenReturn(painted);
        }

        private IrisRiverMaterialConfig enabled(int depth) {
            return new IrisRiverMaterialConfig().setEnabled(true).setPalette(palette).setDepth(depth);
        }

        private IrisRiverMaterialConfig disabled() {
            return new IrisRiverMaterialConfig().setPalette(palette).setDepth(4);
        }

        private PlatformBlockState paint(IrisRiverMaterialConfig material, int depth) {
            return IrisTerrainNormalActuator.paintHydrologyMaterial(
                    biomeLayer, material, depth, rng, 11, 63, -4, data);
        }
    }
}
