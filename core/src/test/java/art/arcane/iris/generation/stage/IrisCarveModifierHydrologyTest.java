package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.underground.CavePoolFiller;
import art.arcane.iris.generation.hydrology.underground.CaveVoxel;
import art.arcane.iris.generation.hydrology.underground.CarvedTerrain;
import art.arcane.iris.generation.mantle.CarveOwnership;
import art.arcane.iris.generation.mantle.CaveTerrainSnapshot;
import art.arcane.iris.generation.mantle.MantleWriter;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.subterrain.IrisSubterrainFamily;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainRoom;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.LogLevel;
import art.arcane.iris.testsupport.KeyedBlockState;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class IrisCarveModifierHydrologyTest {
    private static final long SEED = 1337L;
    private static final int SECTIONS = 4;
    private static final int POOL_FLOOR = 50;
    private static final int POOL_TOP = 52;
    private static final int PROTECTED_FROM = 40;

    @BeforeClass
    public static void registerMatter() {
        IrisMatterSupport.ensureRegistered();
    }

    @Test
    public void explicitHydrologyCanMaterializeIntoAnUninitializedCell() {
        NativeBlockState air = mock(NativeBlockState.class);
        when(air.isAir()).thenReturn(true);

        assertFalse(IrisCarveModifier.shouldSkipEmptyCarve(null, true));
        assertFalse(IrisCarveModifier.shouldSkipEmptyCarve(air, true));
        assertTrue(IrisCarveModifier.shouldSkipEmptyCarve(null, false));
        assertTrue(IrisCarveModifier.shouldSkipEmptyCarve(air, false));
    }

    @Test
    public void overlayCompositionDoesNotMutateBaselineCavern() {
        MatterCavern baseline = new MatterCavern(true, "baseline", (byte) 2);

        assertSame(baseline, IrisCarveModifier.composeCavern(baseline, null));
        assertNull(IrisCarveModifier.composeCavern(
                baseline, new HydrologyCaveCell(HydrologyCaveAction.SEAL_GUARD, "water")));

        MatterCavern wet = IrisCarveModifier.composeCavern(
                baseline, new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, "deep_lava"));
        MatterCavern dry = IrisCarveModifier.composeCavern(
                baseline, new HydrologyCaveCell(HydrologyCaveAction.DRY_AIR, "water"));

        assertEquals(1, wet.getLiquid());
        assertEquals("", wet.getCustomBiome());
        assertEquals(3, dry.getLiquid());
        assertEquals(2, baseline.getLiquid());
        assertEquals("baseline", baseline.getCustomBiome());
    }

    @Test
    public void hydrologyActionsResolveDistinctFluidStates() {
        NativeBlockState current = mock(NativeBlockState.class);
        NativeBlockState source = mock(NativeBlockState.class);
        NativeBlockState air = mock(NativeBlockState.class);

        assertSame(source, IrisCarveModifier.resolveHydrologyState(
                new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, "water"), current, source, air));
        assertSame(source, IrisCarveModifier.resolveHydrologyState(
                new HydrologyCaveCell(HydrologyCaveAction.POOL_SOURCE, "water"), current, source, air));
        assertSame(source, IrisCarveModifier.resolveHydrologyState(
                new HydrologyCaveCell(HydrologyCaveAction.SPRING_SOURCE, "lava"), current, source, air));
        assertSame(air, IrisCarveModifier.resolveHydrologyState(
                new HydrologyCaveCell(HydrologyCaveAction.DRY_AIR, "water"), current, source, air));
    }

    @Test
    public void waterloggingFollowsOnlyExplicitResultingWater() {
        NativeBlockState waterlogged = mock(NativeBlockState.class);
        NativeBlockState dry = mock(NativeBlockState.class);
        NativeBlockState water = mock(NativeBlockState.class);
        NativeBlockState lava = mock(NativeBlockState.class);
        when(waterlogged.key()).thenReturn("minecraft:seagrass[waterlogged=true]");
        when(waterlogged.withProperty("waterlogged", "false")).thenReturn(dry);
        when(water.isWater()).thenReturn(true);

        assertSame(waterlogged, IrisCarveModifier.normalizeWaterlogging(waterlogged, water));
        assertSame(dry, IrisCarveModifier.normalizeWaterlogging(waterlogged, null));
        assertSame(dry, IrisCarveModifier.normalizeWaterlogging(waterlogged, lava));
        assertSame(dry, IrisCarveModifier.resolveHydrologyState(
                new HydrologyCaveCell(HydrologyCaveAction.SEAL_GUARD, "water"), waterlogged, water, null));
    }

    @Test
    public void noOverlayLeavesBaselineWaterloggingUntouched() {
        NativeBlockState waterlogged = mock(NativeBlockState.class);
        NativeBlockState water = mock(NativeBlockState.class);
        MatterCavern flooded = new MatterCavern(true, "", (byte) 1);

        assertSame(waterlogged, IrisCarveModifier.normalizeHydrologyWaterlogging(
                waterlogged,
                flooded,
                null,
                water
        ));
        verify(waterlogged, never()).withProperty("waterlogged", "false");
    }

    @Test
    public void explicitOverlayUsesComposedFluidIntentForWaterlogging() {
        NativeBlockState waterlogged = mock(NativeBlockState.class);
        NativeBlockState dry = mock(NativeBlockState.class);
        NativeBlockState water = mock(NativeBlockState.class);
        MatterCavern flooded = new MatterCavern(true, "", (byte) 1);
        when(waterlogged.key()).thenReturn("minecraft:seagrass[waterlogged=true]");
        when(waterlogged.withProperty("waterlogged", "false")).thenReturn(dry);
        when(water.isWater()).thenReturn(true);

        assertSame(waterlogged, IrisCarveModifier.normalizeHydrologyWaterlogging(
                waterlogged,
                flooded,
                new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, "water"),
                water
        ));
        assertSame(dry, IrisCarveModifier.normalizeHydrologyWaterlogging(
                waterlogged,
                flooded,
                new HydrologyCaveCell(HydrologyCaveAction.DRY_AIR, "water"),
                water
        ));
    }

    @Test
    public void sealGuardsPlaceHostRockWhereTheBlockCannotHoldWater() {
        NativeBlockState rock = new KeyedBlockState("minecraft:stone");
        NativeBlockState air = mock(NativeBlockState.class);
        when(air.isAir()).thenReturn(true);
        NativeBlockState grass = new KeyedBlockState("minecraft:short_grass");
        NativeBlockState sand = solid("minecraft:sand");
        NativeBlockState gravel = solid("minecraft:gravel");
        NativeBlockState granite = solid("minecraft:granite");
        NativeBlockState water = mock(NativeBlockState.class);
        when(water.isFluid()).thenReturn(true);
        when(water.key()).thenReturn("minecraft:water[level=0]");

        assertSame(rock, IrisCarveModifier.sealGuardState(air, rock));
        assertSame(rock, IrisCarveModifier.sealGuardState(null, rock));
        assertSame(rock, IrisCarveModifier.sealGuardState(grass, rock));
        assertSame(rock, IrisCarveModifier.sealGuardState(sand, rock));
        assertSame(rock, IrisCarveModifier.sealGuardState(gravel, rock));
        assertSame(granite, IrisCarveModifier.sealGuardState(granite, rock));
        assertSame(water, IrisCarveModifier.sealGuardState(water, rock));
    }

    @Test
    public void sealGuardsHoldInsideProtectedUpperAndStackRanges() throws Exception {
        NativeBlockState rock = new KeyedBlockState("minecraft:stone");
        NativeBlockState caveAir = new KeyedBlockState("minecraft:cave_air");
        NativeBlockState air = mock(NativeBlockState.class);
        when(air.isAir()).thenReturn(true);
        NativeBlockState granite = solid("minecraft:granite");
        int[] upperSurface = new int[256];
        Arrays.fill(upperSurface, PROTECTED_FROM);
        DimensionStackLayout layout = mock(DimensionStackLayout.class);
        when(layout.isHostFeatureProtectedY(anyInt())).thenAnswer(call -> call.<Integer>getArgument(0) >= PROTECTED_FROM);
        DimensionStackLayout[] stack = new DimensionStackLayout[256];
        Arrays.fill(stack, layout);
        for (boolean upper : new boolean[]{true, false}) {
            String range = upper ? "upper-dimension range" : "dimension-stack range";
            Hunk<NativeBlockState> output = Hunk.newArrayHunk(16, SECTIONS * 16, 16);
            for (int y : new int[]{PROTECTED_FROM - 2, PROTECTED_FROM + 2}) {
                output.setRaw(3, y, 4, air);
                output.setRaw(3, y + 1, 4, granite);
                output.setRaw(3, y + 2, 4, granite);
            }
            CarveCall apply = resolver(output, rock, caveAir, upper ? upperSurface : null, upper ? null : stack);
            for (int y : new int[]{PROTECTED_FROM - 2, PROTECTED_FROM + 2}) {
                apply.at(3, y, 4, null, new HydrologyCaveCell(HydrologyCaveAction.SEAL_GUARD, "water"));
                apply.at(3, y + 1, 4, null, new HydrologyCaveCell(HydrologyCaveAction.DRY_AIR, "water"));
                apply.at(3, y + 2, 4, new MatterCavern(true, "", (byte) 0), null);
            }
            assertSame("a seal guard below the " + range + " seals", rock, output.getRaw(3, PROTECTED_FROM - 2, 4));
            assertSame("inlet air below the " + range + " opens", caveAir, output.getRaw(3, PROTECTED_FROM - 1, 4));
            assertSame("a seal guard inside the " + range + " still seals", rock, output.getRaw(3, PROTECTED_FROM + 2, 4));
            assertSame("inlet air does not open inside the " + range, granite, output.getRaw(3, PROTECTED_FROM + 3, 4));
            assertSame("caves do not open inside the " + range, granite, output.getRaw(3, PROTECTED_FROM + 4, 4));
        }
    }

    @FunctionalInterface
    private interface CarveCall {
        void at(int x, int y, int z, MatterCavern baseline, HydrologyCaveCell hydrology) throws Exception;
    }

    @SuppressWarnings("unchecked")
    private static CarveCall resolver(Hunk<NativeBlockState> output, NativeBlockState rock, NativeBlockState air, int[] upperSurface,
                                      DimensionStackLayout[] stack) throws Exception {
        ChunkContext chunkContext = mock(ChunkContext.class);
        ChunkedDataCache<NativeBlockState> rocks = mock(ChunkedDataCache.class);
        when(rocks.get(anyInt(), anyInt())).thenReturn(rock);
        when(chunkContext.getRock()).thenReturn(rocks);
        IrisCarveScratch scratch = new IrisCarveScratch();
        Map<String, Object> values = new HashMap<>();
        values.put("output", output);
        values.put("chunkContext", chunkContext);
        values.put("scratch", scratch);
        values.put("columnMasks", scratch.columnMasks);
        values.put("upperSurfaceHeights", upperSurface);
        values.put("stackLayouts", stack);
        values.put("worldHeightSpan", SECTIONS * 16);
        Class<?> contextType = Class.forName(IrisCarveModifier.class.getName() + "$CarveResolutionContext");
        RecordComponent[] components = contextType.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            types[index] = components[index].getType();
            arguments[index] = values.containsKey(components[index].getName()) ? values.get(components[index].getName())
                    : types[index] == int.class ? 0 : null;
        }
        Constructor<?> contextConstructor = contextType.getDeclaredConstructor(types);
        contextConstructor.setAccessible(true);
        Object context = contextConstructor.newInstance(arguments);
        IrisCarveModifier modifier = mock(IrisCarveModifier.class, CALLS_REAL_METHODS);
        Field airField = IrisCarveModifier.class.getDeclaredField("AIR");
        airField.setAccessible(true);
        airField.set(modifier, air);
        Class<?> resolverType = Class.forName(IrisCarveModifier.class.getName() + "$CarveResolver");
        Constructor<?> resolverConstructor = resolverType.getDeclaredConstructor(IrisCarveModifier.class, contextType);
        resolverConstructor.setAccessible(true);
        Object resolver = resolverConstructor.newInstance(modifier, context);
        Method apply = resolverType.getDeclaredMethod("apply", int.class, int.class, int.class, MatterCavern.class, HydrologyCaveCell.class);
        apply.setAccessible(true);
        return (x, y, z, baseline, hydrology) -> apply.invoke(resolver, x, y, z, baseline, hydrology);
    }

    @Test
    public void subterrainRoomsNeverTakeAnyInletCell() {
        SubterrainRoom room = new SubterrainRoom("room", "", IrisSubterrainFamily.CENOTE, 0, 0, 0, 0, 0, 0, -10, 10, 1D, 0, false, false,
                SubterrainCell.Kind.AIR);
        SubterrainCell open = new SubterrainCell(SubterrainCell.Kind.AIR, "", room);

        assertEquals(CarveOwnership.Owner.SUBTERRAIN, IrisCarveModifier.carveOwner(null, open, null, 0));
        assertEquals(CarveOwnership.Owner.HYDROLOGY,
                IrisCarveModifier.carveOwner(new HydrologyCaveCell(HydrologyCaveAction.DRY_AIR, "water"), open, null, 0));
        assertEquals(CarveOwnership.Owner.HYDROLOGY,
                IrisCarveModifier.carveOwner(new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, "water"), open, null, 0));
        assertEquals(CarveOwnership.Owner.HYDROLOGY,
                IrisCarveModifier.carveOwner(new HydrologyCaveCell(HydrologyCaveAction.SEAL_GUARD, "water"), open, null, 0));
        assertEquals(CarveOwnership.Owner.CAVERN, IrisCarveModifier.carveOwner(null, SubterrainCell.OUTSIDE, null, 0));
        for (SubterrainCell.Kind kind : new SubterrainCell.Kind[]{SubterrainCell.Kind.WATER, SubterrainCell.Kind.LAVA}) {
            SubterrainCell fluid = new SubterrainCell(kind, "", room);
            assertEquals(CarveOwnership.Owner.SUBTERRAIN, IrisCarveModifier.carveOwner(null, fluid, null, 0));
            assertEquals("subterrain " + kind + " yields the inlet seal", CarveOwnership.Owner.HYDROLOGY,
                    IrisCarveModifier.carveOwner(new HydrologyCaveCell(HydrologyCaveAction.SEAL_GUARD, "water"), fluid, null, 0));
        }
    }

    @Test
    public void poolWallsResolveNoUnknownFluid() throws Exception {
        IrisHydrology config = new IrisHydrology().setEnabled(true);
        config.getUnderground().getCavePools().setDensity(1D).setFreeboard(new KList<>(0, 0));
        Map<Long, HydrologyCaveCell> written = new HashMap<>();
        new CavePoolFiller(HydrologySettings.compile(config, SEED, 63, 0, 384), 0)
                .fill(0, 0, SealedRoom::new, recordingWriter(written), SEED);
        Matter[] sections = new Matter[SECTIONS];
        Set<String> wallFluids = new HashSet<>();
        for (Map.Entry<Long, HydrologyCaveCell> entry : written.entrySet()) {
            HydrologyCaveCell cell = entry.getValue();
            if (cell.action() == HydrologyCaveAction.SEAL_GUARD) {
                wallFluids.add(cell.fluidProfileKey());
            }
            int x = x(entry.getKey());
            int y = y(entry.getKey());
            int z = z(entry.getKey());
            if (x >> 4 == 0 && z >> 4 == 0) {
                section(sections, y).slice(HydrologyCaveCell.class).set(x, y & 15, z, cell);
            }
        }
        section(sections, POOL_TOP + 1).slice(HydrologyCaveCell.class)
                .set(0, (POOL_TOP + 1) & 15, 0, new HydrologyCaveCell(HydrologyCaveAction.SEAL_GUARD, "water"));
        CaveTerrainSnapshot terrain = CaveTerrainSnapshot.capture(chunk(sections), 0, 0);
        IrisCarveModifier.CaveZone zone = new IrisCarveModifier.CaveZone();
        zone.setFloor(POOL_FLOOR);
        zone.setCeiling(POOL_TOP);
        IrisCarveModifier modifier = mock(IrisCarveModifier.class, CALLS_REAL_METHODS);
        doReturn(complexWithWater()).when(modifier).getComplex();
        Method normalize = IrisCarveModifier.class.getDeclaredMethod("normalizeCaveZoneWaterlogging", Hunk.class,
                CaveTerrainSnapshot.class, IrisCarveModifier.CaveZone.class, int.class, int.class, int.class, int.class);
        normalize.setAccessible(true);
        Hunk<NativeBlockState> output = Hunk.newArrayHunk(16, SECTIONS * 16, 16);

        List<String> logged = new ArrayList<>();
        IrisPlatform platform = mock(IrisPlatform.class);
        doAnswer(invocation -> {
            logged.add(invocation.getArgument(0) + " " + invocation.getArgument(1));
            return null;
        }).when(platform).log(any(LogLevel.class), anyString());
        IrisPlatforms.bind(platform);
        try {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    normalize.invoke(modifier, output, terrain, zone, x, z, x, z);
                }
            }
        } finally {
            IrisPlatforms.unbind();
        }

        assertEquals(List.of(), logged.stream().filter(line -> line.contains("unknown fluid profile")).distinct().toList());
        assertEquals("pool walls carry the pool's fluid", Set.of("water"), wallFluids);
    }

    private static IrisComplex complexWithWater() throws Exception {
        IrisComplex complex = mock(IrisComplex.class, CALLS_REAL_METHODS);
        setField(complex, "waterFluidStreams", Map.of("water", mock(ProceduralStream.class)));
        setField(complex, "fluidStream", mock(ProceduralStream.class));
        return complex;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = IrisComplex.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static MantleWriter recordingWriter(Map<Long, HydrologyCaveCell> written) {
        MantleWriter writer = mock(MantleWriter.class);
        doAnswer(invocation -> written.put(key(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)),
                invocation.getArgument(3))).when(writer).setData(anyInt(), anyInt(), anyInt(), any());
        when(writer.getDataIfPresent(anyInt(), anyInt(), anyInt(), eq(HydrologyCaveCell.class))).thenAnswer(invocation ->
                written.get(key(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return null;
        }).when(writer).withChunkFence(anyInt(), anyInt(), any(Runnable.class));
        return writer;
    }

    private static Matter section(Matter[] sections, int y) {
        if (sections[y >> 4] == null) {
            sections[y >> 4] = new IrisMatter(16, 16, 16);
        }
        return sections[y >> 4];
    }

    @SuppressWarnings("unchecked")
    private static MantleChunk<Matter> chunk(Matter[] sections) {
        MantleChunk<Matter> chunk = mock(MantleChunk.class, withSettings().stubOnly());
        when(chunk.sectionCount()).thenReturn(sections.length);
        when(chunk.exists(anyInt())).thenAnswer(call -> sections[call.<Integer>getArgument(0)] != null);
        when(chunk.get(anyInt())).thenAnswer(call -> sections[call.<Integer>getArgument(0)]);
        return chunk;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 4096) << 40) | ((long) y << 20) | (z + 4096);
    }

    private static int x(long key) {
        return (int) (key >>> 40) - 4096;
    }

    private static int z(long key) {
        return (int) (key & 0xFFFFF) - 4096;
    }

    private static int y(long key) {
        return (int) ((key >>> 20) & 0xFFFFF);
    }

    private static NativeBlockState solid(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.isSolid()).thenReturn(true);
        when(state.key()).thenReturn(key);
        return state;
    }

    private static final class SealedRoom implements CarvedTerrain {
        private static final int SURFACE = 120;

        @Override
        public CaveVoxel voxel(int x, int y, int z) {
            if (y <= 0 || y > SURFACE) {
                return CaveVoxel.FOREIGN;
            }
            boolean inside = x >= 0 && x < 16 && z >= 0 && z < 16 && y >= POOL_FLOOR && y <= POOL_TOP;
            return inside ? CaveVoxel.AIR : CaveVoxel.SOLID;
        }

        @Override
        public int surface(int x, int z) {
            return SURFACE;
        }

        @Override
        public String caveFluid(int x, int y, int z) {
            return null;
        }

        @Override
        public boolean structureNear(int x, int y, int z) {
            return false;
        }
    }
}
