package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.iris.generation.decoration.IrisDecorationStep;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisTerrainNormalActuatorHydrologyTest {
    private static final List<WaterRole> WET = List.of(WaterRole.CHANNEL, WaterRole.POND, WaterRole.MOUTH, WaterRole.CASCADE,
            WaterRole.WATERFALL);
    private static final int GROUND = 60;
    private static final int HEAD = 64;

    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Test
    public void hydrologyOwnedFluidBypassesBiomeSeaLayers() {
        NativeBlockState water = mock(NativeBlockState.class);
        NativeBlockState ice = mock(NativeBlockState.class);
        KList<NativeBlockState> seaLayers = new KList<>();
        seaLayers.add(ice);

        assertSame(water, HydrologyFluidLayerSelector.select(seaLayers, 0, water, true));
        assertSame(ice, HydrologyFluidLayerSelector.select(seaLayers, 0, water, false));
        assertSame(water, HydrologyFluidLayerSelector.select(seaLayers, 1, water, false));
    }

    @Test
    public void frozenTerrainPublishesSourceWaterBeforeStandardTopLayerFreezing() {
        NativeBlockState water = state("minecraft:water");
        NativeBlockState ice = state("minecraft:ice");
        when(water.isWater()).thenReturn(true);
        KList<NativeBlockState> frozenSeaLayers = new KList<>();
        frozenSeaLayers.add(ice);
        boolean hydrologyOwned = WaterRole.POND.wet();
        ArrayList<String> stages = new ArrayList<>();

        NativeBlockState state = HydrologyFluidLayerSelector.select(frozenSeaLayers, 0, water, hydrologyOwned);
        stages.add("hydrology");
        assertTrue(hydrologyOwned);
        assertSame(water, state);
        assertTrue(state.isWater());

        for (IrisDecorationStep step : IrisDecorationStep.values()) {
            if (step == IrisDecorationStep.TOP_LAYER_MODIFICATION) {
                assertSame(water, state);
                state = ice;
                stages.add(step.getSerializedName());
            }
        }

        assertEquals(List.of("hydrology", "top_layer_modification"), stages);
        assertSame(ice, state);
        assertFalse(state.isWater());
    }

    @Test
    public void wetColumnsFillFromAboveTheGroundToTheHeadWithTheirOwnFluid() {
        for (WaterRole role : WET) {
            Painter painter = new Painter(role, HEAD);

            Hunk<NativeBlockState> output = painter.paint();

            assertSame(role.name(), painter.grass, output.get(0, GROUND, 0));
            for (int y = GROUND + 1; y <= HEAD; y++) {
                assertSame(role + " y " + y, painter.channelFluid, output.get(0, y, 0));
            }
            assertNull(role.name(), output.get(0, HEAD + 1, 0));
            verify(painter.biome, never()).generateSeaLayers(anyDouble(), anyDouble(), any(RNG.class), anyInt(), any());
        }
    }

    @Test
    public void openInletSlotColumnsKeepTheSeaFloorAndTheSeaLayers() {
        Painter ocean = new Painter(WaterRole.INLET, HEAD);
        ocean.materials(true);
        when(ocean.complex.naturalTrueHeight(11, -4)).thenReturn(GROUND);

        Hunk<NativeBlockState> sea = ocean.paint();

        assertSame("the sea floor keeps the biome's own top", ocean.grass, sea.get(0, GROUND, 0));
        assertSame(ocean.dirt, sea.get(0, GROUND - 1, 0));
        assertSame("the slot takes the sea layers", ocean.ice, sea.get(0, HEAD, 0));
        for (int y = GROUND + 1; y < HEAD; y++) {
            assertSame("y " + y, ocean.channelFluid, sea.get(0, y, 0));
        }
        verify(ocean.complex, never()).resolveWaterMaterials(anyInt(), anyInt());

        Painter cliff = new Painter(WaterRole.INLET, HEAD);
        when(cliff.complex.naturalTrueHeight(11, -4)).thenReturn(GROUND + 20);
        assertSame("a slot cut through the cliff shows the strata below the old surface", cliff.dirt, cliff.paint().get(0, GROUND, 0));
    }

    @Test
    public void openInletSlotsKeepLiquidWaterUnderOnlyTheTopSeaLayer() {
        Painter slot = new Painter(WaterRole.INLET, HEAD);
        when(slot.complex.naturalTrueHeight(11, -4)).thenReturn(GROUND);
        slot.thickSeaLayers();

        Hunk<NativeBlockState> frozen = slot.paint();

        assertSame("the frozen sea still caps the slot", slot.ice, frozen.get(0, HEAD, 0));
        for (int y = GROUND + 1; y < HEAD; y++) {
            assertSame("the slot stays open water under its ice at y " + y, slot.channelFluid, frozen.get(0, y, 0));
        }

        Painter sea = new Painter(WaterRole.NONE, HEAD);
        sea.thickSeaLayers();
        Hunk<NativeBlockState> ocean = sea.paint();
        for (int y = GROUND + 1; y <= HEAD; y++) {
            assertSame("the open sea keeps its own thick ice at y " + y, sea.ice, ocean.get(0, y, 0));
        }
    }

    @Test
    public void dryColumnsBelowTheSeaKeepTheSeaFillAndItsLayers() {
        for (WaterRole role : List.of(WaterRole.NONE, WaterRole.BANK, WaterRole.SHORE)) {
            Painter painter = new Painter(role, HEAD);

            Hunk<NativeBlockState> output = painter.paint();

            assertSame(role.name(), painter.ice, output.get(0, HEAD, 0));
            for (int y = GROUND + 1; y < HEAD; y++) {
                assertSame(role + " y " + y, painter.sea, output.get(0, y, 0));
            }
            assertNull(role.name(), output.get(0, HEAD + 1, 0));
            verify(painter.complex, never()).resolveWaterFluid(anyInt(), anyInt());
        }
    }

    @Test
    public void bedShoreAndBankPalettesPaintTheirRoleLayers() {
        for (WaterRole role : WaterRole.values()) {
            Painter painter = new Painter(role, role.wet() ? HEAD : 40);
            IrisHydrologyMaterials materials = painter.materials(true);

            Hunk<NativeBlockState> output = painter.paint();

            NativeBlockState expectedTop = switch (role) {
                case NONE -> painter.grass;
                case CHANNEL, POND, MOUTH, CASCADE, WATERFALL -> painter.bed;
                case INLET -> painter.grass;
                case SHORE, POND_SHORE, DAM -> painter.shore;
                case BANK -> painter.bank;
            };
            NativeBlockState expectedSecond = role == WaterRole.SHORE || role == WaterRole.POND_SHORE || role == WaterRole.DAM
                    ? painter.shore : painter.dirt;
            assertSame(role.name(), expectedTop, output.get(0, GROUND, 0));
            assertSame(role.name(), expectedSecond, output.get(0, GROUND - 1, 0));
            assertSame(role.name(), painter.rock, output.get(0, GROUND - 2, 0));
            if (role == WaterRole.NONE || role == WaterRole.INLET) {
                verify(painter.complex, never()).resolveWaterMaterials(anyInt(), anyInt());
            }
            materials.setBank(null).setShore(null).setBed(null);
            output = painter.paint();
            assertSame(role.name(), painter.grass, output.get(0, GROUND, 0));
            assertSame(role.name(), painter.dirt, output.get(0, GROUND - 1, 0));
        }
    }

    @Test
    public void sandSurvivesUnlessTheMaterialsKeepFallingBlocksAwayFromTheWater() {
        for (WaterRole role : List.of(WaterRole.CHANNEL, WaterRole.POND, WaterRole.SHORE, WaterRole.BANK)) {
            Painter painter = new Painter(role, role.wet() ? HEAD : 40);
            NativeBlockState sand = state("minecraft:sand");
            painter.layers(sand, sand);
            IrisHydrologyMaterials materials = painter.materials(false);
            assertTrue(materials.isAllowGravityBlocks());

            Hunk<NativeBlockState> output = painter.paint();
            assertSame(role.name(), sand, output.get(0, GROUND, 0));
            assertSame(role.name(), sand, output.get(0, GROUND - 1, 0));

            materials.setAllowGravityBlocks(false);
            output = painter.paint();
            assertSame(role.name(), painter.rock, output.get(0, GROUND, 0));
            assertSame(role.name(), painter.rock, output.get(0, GROUND - 1, 0));
        }
        Painter natural = new Painter(WaterRole.NONE, 40);
        NativeBlockState sand = state("minecraft:sand");
        natural.layers(sand, sand);
        natural.materials(false).setAllowGravityBlocks(false);
        assertSame(sand, natural.paint().get(0, GROUND, 0));
    }

    @Test
    public void everySpanOfATerrain3DColumnTakesItsRoleMaterial() {
        for (WaterRole role : List.of(WaterRole.SHORE, WaterRole.BANK)) {
            Painter painter = new Painter(role, 40);
            IrisHydrologyMaterials materials = painter.materials(true);
            painter.carved();
            NativeBlockState painted = role == WaterRole.SHORE ? painter.shore : painter.bank;
            NativeBlockState second = role == WaterRole.SHORE ? painter.shore : painter.dirt;

            Hunk<NativeBlockState> output = painter.paint();

            assertSame(role.name(), painted, output.get(0, 60, 0));
            assertSame(role.name(), second, output.get(0, 59, 0));
            assertSame(role.name(), painter.rock, output.get(0, 58, 0));
            assertNotSame(role.name(), painted, output.get(0, 45, 0));
            assertNotSame(role.name(), painter.rock, output.get(0, 45, 0));
            assertSame(role.name(), painted, output.get(0, 40, 0));
            assertSame(role.name(), second, output.get(0, 39, 0));
            assertSame(role.name(), painter.rock, output.get(0, 38, 0));

            materials.setShore(null).setBank(null);
            output = painter.paint();
            assertSame(role.name(), painter.grass, output.get(0, 60, 0));
            assertSame(role.name(), painter.dirt, output.get(0, 40, 0));
            assertSame(role.name(), painter.rock, output.get(0, 38, 0));
        }
    }

    private static NativeBlockState state(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        return state;
    }

    private static final class Painter {
        private final Engine engine = mock(Engine.class);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final IrisData data = mock(IrisData.class);
        private final IrisBiome biome = mock(IrisBiome.class);
        private final IrisRegion region = new IrisRegion();
        private final ChunkContext context = mock(ChunkContext.class);
        private final IrisDimension dimension = new IrisDimension().setBedrock(false).setHideOresForHiddenOre(true);
        private final NativeBlockState rock = state("minecraft:stone");
        private final NativeBlockState grass = state("minecraft:grass_block");
        private final NativeBlockState dirt = state("minecraft:dirt");
        private final NativeBlockState sea = state("minecraft:water");
        private final NativeBlockState ice = state("minecraft:ice");
        private final NativeBlockState channelFluid = state("minecraft:water[level=0]");
        private final NativeBlockState bed = state("minecraft:gravel_bed");
        private final NativeBlockState shore = state("minecraft:clay");
        private final NativeBlockState bank = state("minecraft:coarse_dirt");

        private Painter(WaterRole role, int head) {
            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getData()).thenReturn(data);
            when(engine.getSeedManager()).thenReturn(mock(SeedManager.class));
            when(complex.getImageMapRuntime()).thenReturn(mock(IrisImageMapRuntime.class));
            when(complex.getWaterHeadStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> (double) head));
            when(complex.getWaterRoleStream()).thenReturn(ProceduralStream.of((x, z) -> role, Interpolated.of(value -> 0D, value -> role)));
            when(complex.resolveWaterFluid(11, -4)).thenReturn(channelFluid);
            when(context.getRoundedHeight(0, 0)).thenReturn(GROUND);
            when(context.getBiome()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> biome, Interpolated.of(value -> 0D, value -> biome)), 11, -4, false));
            when(context.getRegion()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> region, Interpolated.of(value -> 0D, value -> region)), 11, -4, false));
            when(context.getRock()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> rock, Interpolated.of(value -> 0D, value -> rock)), 11, -4, false));
            when(context.getFluid()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> sea, Interpolated.of(value -> 0D, value -> sea)), 11, -4, false));
            when(biome.generateSeaLayers(anyDouble(), anyDouble(), any(RNG.class), anyInt(), eq(data)))
                    .thenAnswer(invocation -> new KList<>(List.of(ice)));
            layers(grass, dirt);
        }

        private void thickSeaLayers() {
            when(biome.generateSeaLayers(anyDouble(), anyDouble(), any(RNG.class), anyInt(), eq(data))).thenAnswer(invocation -> {
                KList<NativeBlockState> layers = new KList<>();
                for (int layer = 0; layer < (int) invocation.getArgument(3); layer++) {
                    layers.add(ice);
                }
                return layers;
            });
        }

        private void carved() {
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
            when(biome.generateCeilingLayers(eq(dimension), anyDouble(), anyDouble(), any(RNG.class),
                    anyInt(), anyInt(), eq(data), eq(complex))).thenReturn(new KList<>());
        }

        private void layers(NativeBlockState top, NativeBlockState second) {
            when(biome.generateLayers(eq(dimension), anyDouble(), anyDouble(), any(RNG.class),
                    anyInt(), anyInt(), eq(data), eq(complex))).thenAnswer(invocation -> new KList<>(List.of(top, second)));
        }

        private IrisHydrologyMaterials materials(boolean palettes) {
            IrisHydrologyMaterials materials = new IrisHydrologyMaterials().setShoreDepth(2);
            if (palettes) {
                materials.setBed(palette(bed)).setShore(palette(shore)).setBank(palette(bank));
            }
            when(complex.resolveWaterMaterials(11, -4)).thenReturn(materials);
            return materials;
        }

        private IrisMaterialPalette palette(NativeBlockState block) {
            IrisMaterialPalette palette = mock(IrisMaterialPalette.class);
            when(palette.get(any(RNG.class), anyDouble(), anyDouble(), anyDouble(), eq(data))).thenReturn(block);
            return palette;
        }

        private Hunk<NativeBlockState> paint() {
            Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 96, 1);
            new IrisTerrainNormalActuator(engine).paint(11, -4, output, context);
            return output;
        }
    }
}
