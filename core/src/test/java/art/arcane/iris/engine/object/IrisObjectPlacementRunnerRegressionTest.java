package art.arcane.iris.engine.object;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.iris.engine.framework.PlacedObject;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisObjectPlacementRunnerRegressionTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final int ANCHOR_Y = 80;

    private IrisData data;
    private Engine engine;
    private PlatformBlockState solid;
    private PlatformBlockState schematicAir;

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        Map<String, PlatformBlockState> registryStates = new HashMap<>();
        when(registries.block(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return registryStates.computeIfAbsent(key, value -> state(value.toLowerCase(), !value.toLowerCase().contains("air")));
        });
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);

        engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(256);
        data = mock(IrisData.class);
        when(data.getEngine()).thenReturn(engine);
        solid = state("minecraft:stone", true);
        schematicAir = state("minecraft:air", false);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void unsupportedRotatedOrientationOmitsBlockTileAndListener() {
        PlatformBlockState torch = state("minecraft:wall_torch", false);
        Directional directional = mock(Directional.class);
        Material material = mock(Material.class);
        when(torch.nativeHandle()).thenReturn(directional);
        when(directional.clone()).thenReturn(directional);
        when(directional.getFacing()).thenReturn(BlockFace.NORTH);
        when(directional.getFaces()).thenReturn(Set.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST));
        when(directional.getMaterial()).thenReturn(material);
        IrisObjectPlacement placement = placement();
        placement.setRotation(IrisObjectRotation.of(90, 0, 0));
        assertNull(placement.getRotation().rotate(torch, 0, 0, 0));
        IrisObject object = lineObject(3);
        object.setUnsigned(1, 0, 0, torch);
        object.setUnsignedTile(1, 0, 0, new TileData("minecraft:wall_torch", new KMap<>()));
        RecordingPlacer placer = new RecordingPlacer(null);
        List<PlatformBlockState> placed = new ArrayList<>();

        int result = object.place(0, ANCHOR_Y, 0, placer, placement, new RNG(2L),
                (position, state) -> placed.add(state), null, data);

        assertEquals(ANCHOR_Y, result);
        assertEquals(2, placer.writes().size());
        assertEquals(List.of(solid, solid), placed);
        assertNull(placer.get(0, ANCHOR_Y, 0));
        assertNull(placer.getData(0, ANCHOR_Y, 0, TileData.class));
        assertEquals(1, object.getStates().size());
    }

    @Test
    public void placementFailurePropagatesToTheCaller() {
        RecordingPlacer placer = new RecordingPlacer(null);
        placer.failAfterWrites(1);
        IrisObject object = lineObject(3);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> object.place(0, ANCHOR_Y, 0, placer, placement(), new RNG(2L), data));

        assertTrue(error.getMessage().contains("write failed"));
        assertEquals(1, placer.writes().size());
    }

    @Test
    public void boreUsesRotatedTranslationAndRandomizedAnchor() {
        RecordingPlacer placer = new RecordingPlacer(null);
        IrisObjectPlacement placement = placement();
        placement.setBore(true);
        placement.setRotation(IrisObjectRotation.of(0, 90, 0));
        placement.setTranslate(new IrisObjectTranslate().setX(4).setYRandom(3));

        int resultY = lineObject(3).place(0, ANCHOR_Y, 0, placer, placement, new RNG(2L), data);

        List<BlockWrite> airWrites = placer.writesOf(IrisObject.States.air());
        assertFalse(airWrites.isEmpty());
        assertTrue(airWrites.stream().allMatch(write -> write.y() >= resultY && write.y() <= resultY));
        assertTrue(airWrites.stream().allMatch(write -> write.x() == 0));
        assertTrue(airWrites.stream().allMatch(write -> write.z() <= -3 && write.z() >= -5));
    }

    @Test
    public void collisionCheckUsesTheTransformedBounds() {
        PlacedObject forbidden = new PlacedObject(null, object("forbidden"), 1, 0, 0);
        RecordingPlacer placer = new RecordingPlacer(engine);
        placer.setData(0, ANCHOR_Y, -4, "forbidden@1");
        when(engine.resolveObjectPlacementMarker(0, -4, "forbidden@1")).thenReturn(forbidden);
        IrisObjectPlacement placement = placement();
        placement.setRotation(IrisObjectRotation.of(0, 90, 0));
        placement.setTranslate(new IrisObjectTranslate().setX(4));
        placement.getForbiddenCollisions().add("forbidden");

        int result = lineObject(3).place(0, ANCHOR_Y, 0, placer, placement, new RNG(2L), data);

        assertEquals(-1, result);
        assertTrue(placer.writes().isEmpty());
        verify(engine).resolveObjectPlacementMarker(0, -4, "forbidden@1");
        verify(engine, never()).getObjectPlacement(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void allowedCollisionOverridesTheForbiddenKey() {
        PlacedObject allowed = new PlacedObject(null, object("allowed"), 1, 0, 0);
        RecordingPlacer placer = new RecordingPlacer(engine);
        placer.setData(0, ANCHOR_Y, 0, "allowed@1");
        when(engine.resolveObjectPlacementMarker(0, 0, "allowed@1")).thenReturn(allowed);
        IrisObjectPlacement placement = placement();
        placement.getForbiddenCollisions().add("allowed");
        placement.getAllowedCollisions().add("allowed");

        int result = lineObject(1).place(0, ANCHOR_Y, 0, placer, placement, new RNG(2L), data);

        assertEquals(ANCHOR_Y, result);
        assertFalse(placer.writes().isEmpty());
        verify(engine).resolveObjectPlacementMarker(0, 0, "allowed@1");
    }

    @Test
    public void arbitraryRotationSamplesEveryTransformedFootprintColumn() {
        RecordingPlacer placer = new RecordingPlacer(null);
        IrisObjectPlacement placement = placement();
        placement.setMode(ObjectPlaceMode.MAX_HEIGHT);
        placement.setRotation(IrisObjectRotation.of(0, 45, 0));

        boxObject(5, 1, 3).place(0, -1, 0, placer, placement, new RNG(2L), data);

        assertTrue(placer.sampledColumns().contains("-2:-2"));
        assertTrue(placer.sampledColumns().contains("2:2"));
        assertTrue(placer.sampledColumns().size() >= 25);
    }

    @Test
    public void snowUsesOnlyBlocksThatWereWritten() {
        RecordingPlacer placer = new RecordingPlacer(null);
        IrisObject object = new IrisObject(1, 3, 1);
        object.setUnsigned(0, 0, 0, solid);
        object.setUnsigned(0, 2, 0, schematicAir);
        IrisObjectPlacement placement = placement();
        placement.setSnow(0.1);

        object.place(0, ANCHOR_Y, 0, placer, placement, new RNG(2L), data);

        List<BlockWrite> snowWrites = placer.writesOf(IrisObject.States.snowLayer(0));
        assertEquals(1, snowWrites.size());
        assertEquals(ANCHOR_Y, snowWrites.get(0).y());
    }

    @Test
    public void debugPlacementDoesNotMutateSmartBoreCache() {
        IrisObject object = new IrisObject(1, 1, 1);
        object.setUnsigned(0, 0, 0, IrisObject.States.vair());
        object.setSmartBored(true);
        IrisObjectPlacement placement = placement();
        placement.setSmartBore(true);
        RecordingPlacer debug = new RecordingPlacer(null);
        debug.setDebugSmartBore(true);
        RecordingPlacer normal = new RecordingPlacer(null);

        object.place(0, ANCHOR_Y, 0, debug, placement, new RNG(2L), data);
        object.place(0, ANCHOR_Y, 0, normal, placement, new RNG(2L), data);

        PlatformBlockState cachedState = object.getBlocks().get(object.getSigned(0, 0, 0));
        assertSame(IrisObject.States.vair(), cachedState);
        if (IrisObject.States.vair() != IrisObject.States.vairDebug()) {
            assertTrue(cachedState != IrisObject.States.vairDebug());
        }
    }

    @Test
    public void placementDataIsRequiredAtTheBoundary() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> lineObject(1).place(0, ANCHOR_Y, 0, new RecordingPlacer(null), placement(), new RNG(2L), null));

        assertEquals("Object placement data is required.", error.getMessage());
    }

    @Test
    public void rawPlacementPreservesVineFacesAndReplacesSolidDestination() {
        RecordingPlacer placer = new RecordingPlacer(null);
        placer.set(0, ANCHOR_Y, 0, solid);
        placer.set(1, ANCHOR_Y, 0, solid);
        PlatformBlockState vine = state("minecraft:vine[east=false,north=true]", false);
        when(vine.isVineBlock()).thenReturn(true);
        IrisObject object = new IrisObject(1, 1, 1);
        object.setUnsigned(0, 0, 0, vine);
        IrisObjectPlacement placement = placement();
        placement.setMode(ObjectPlaceMode.STRUCTURE_PIECE);
        placement.setRotation(IrisObjectRotation.of(0, 0, 0));

        object.place(0, ANCHOR_Y, 0, placer, placement, new RNG(2L), data);

        assertSame(vine, placer.get(0, ANCHOR_Y, 0));
        verify(vine, never()).withProperty(anyString(), anyString());
    }

    @Test
    public void changingMaterialWithAnEditClearsOriginalTileData() {
        RecordingPlacer placer = new RecordingPlacer(null);
        PlatformBlockState chest = state("minecraft:chest", true);
        IrisObject object = new IrisObject(1, 1, 1);
        object.setUnsigned(0, 0, 0, chest);
        object.setUnsignedTile(0, 0, 0, new TileData("minecraft:chest", new KMap<>()));
        IrisObjectReplace edit = mock(IrisObjectReplace.class);
        IrisMaterialPalette palette = mock(IrisMaterialPalette.class);
        when(edit.getChance()).thenReturn(1F);
        when(edit.getFind(data)).thenReturn(new KList<>(chest));
        when(edit.getReplace(any(RNG.class), anyDouble(), anyDouble(), anyDouble(), any(IrisData.class)))
                .thenReturn(solid);
        when(edit.getReplace()).thenReturn(palette);
        when(palette.getTile(any(RNG.class), anyDouble(), anyDouble(), anyDouble(), any(IrisData.class)))
                .thenReturn(Optional.empty());
        IrisObjectPlacement placement = placement();
        placement.setMode(ObjectPlaceMode.STRUCTURE_PIECE);
        placement.getEdit().add(edit);

        object.place(0, ANCHOR_Y, 0, placer, placement, new RNG(2L), data);

        assertSame(solid, placer.get(0, ANCHOR_Y, 0));
        assertNull(placer.getData(0, ANCHOR_Y, 0, TileData.class));
        assertEquals(1, object.getStates().size());
    }

    @Test
    public void paintFollowsTheLowerLedgeBeneathADistantRoof() {
        RecordingPlacer placer = new RecordingPlacer(null);
        placer.terrain(1, 0, 0, 80, 150, 160);
        placer.terrain(2, 0, 0, 80, 150, 160);
        IrisObjectPlacement placement = placement().setMode(ObjectPlaceMode.PAINT)
                .setRequireSurfaceSupport(true).setSurfaceSupportBuffer(0);

        assertEquals(80, lineObject(5).place(0, -1, 0, placer, placement, new RNG(2L), data));

        assertEquals(5, placer.writes().size());
        assertTrue(placer.writes().stream().allMatch(write -> write.y() == 80));
    }

    @Test
    public void paintClipsAtACliffWithoutReachingThePatchBeyondIt() {
        RecordingPlacer placer = new RecordingPlacer(null);
        placer.terrain(1, 0, 0, 20);
        IrisObjectPlacement placement = placement().setMode(ObjectPlaceMode.PAINT);

        lineObject(5).place(0, -1, 0, placer, placement, new RNG(2L), data);

        assertEquals(3, placer.writes().size());
        assertTrue(placer.writes().stream().allMatch(write -> write.x() <= 0 && write.y() == 80));
    }

    @Test
    public void paintAccumulatesNormalAscendingAndDescendingSlopeSteps() {
        RecordingPlacer placer = new RecordingPlacer(null);
        for (int x = -6; x <= 6; x++) {
            placer.terrain(x, 0, 0, 80 + x * 3);
        }

        lineObject(13).place(0, -1, 0, placer, placement().setMode(ObjectPlaceMode.PAINT), new RNG(2L), data);

        assertEquals(13, placer.writes().size());
        assertTrue(placer.writes().stream().allMatch(write -> write.y() == 80 + write.x() * 3));
    }

    @Test
    public void paintingAndBoringCannotFeedBackIntoTheSurfaceProjection() {
        RecordingPlacer placer = new RecordingPlacer(null);
        placer.highestFollowsWrites = true;
        IrisObjectPlacement placement = placement().setMode(ObjectPlaceMode.PAINT).setBore(true);

        boxObject(1, 3, 1).place(0, -1, 0, placer, placement, new RNG(2L), data);

        assertEquals(3, placer.writesOf(solid).size());
        assertTrue(placer.writes().stream().allMatch(write -> write.y() >= 80 && write.y() <= 82));
    }

    @Test
    public void paintUsesTranslatedAnchorAndMatchingRandomizedBoreBounds() {
        RecordingPlacer placer = new RecordingPlacer(null);
        placer.terrain(0, -3, 0, 108);
        placer.terrain(0, -4, 0, 110);
        placer.terrain(0, -5, 0, 112);
        IrisObjectPlacement placement = placement().setMode(ObjectPlaceMode.PAINT).setBore(true)
                .setRotation(IrisObjectRotation.of(0, 90, 0))
                .setTranslate(new IrisObjectTranslate().setX(4).setY(5).setYRandom(3));
        RNG rng = mock(RNG.class);
        when(rng.i(0, 3)).thenReturn(3);

        assertEquals(113, lineObject(3).place(0, -1, 0, placer, placement, rng, data));

        assertEquals(3, placer.writesOf(solid).size());
        assertEquals(3, placer.writesOf(IrisObject.States.air()).size());
        assertTrue(placer.writes().stream().allMatch(write -> write.x() == 0
                && write.z() >= -5 && write.z() <= -3 && write.y() == 110 - (write.z() + 4) * 2 + 8));
    }

    @Test
    public void nativeVetoUsesTheSelectedLedgeAndRandomTranslationOffset() {
        RecordingPlacer placer = new RecordingPlacer(engine);
        placer.terrain(1, 0, 0, 80, 150, 160);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getNativeStructureVolumes(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(
                new KList<>(NativeStructureVolume.of("test", 1, 21, 0, 1, 21, 0)));
        IrisObjectPlacement placement = placement().setMode(ObjectPlaceMode.PAINT).setForcePlace(true)
                .setTranslate(new IrisObjectTranslate().setY(2).setYRandom(3));
        RNG rng = mock(RNG.class);
        when(rng.i(0, 3)).thenReturn(3);

        assertEquals(-1, lineObject(3).place(0, -1, 0, placer, placement, rng, data));
        assertTrue(placer.writes().isEmpty());

        when(engine.getNativeStructureVolumes(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(
                new KList<>(NativeStructureVolume.of("test", 1, 101, 0, 1, 101, 0)));
        assertEquals(83, lineObject(3).place(0, -1, 0, placer, placement, rng, data));
        assertTrue(placer.writes().stream().allMatch(write -> write.y() == 85));
    }

    @Test
    public void warpedPaintAndNativeVetoUseTheSameFinalColumns() {
        RecordingPlacer placer = new RecordingPlacer(engine);
        for (int x = 1; x <= 3; x++) {
            placer.terrain(x, 2, 0, 80, 150, 160);
        }
        IrisObjectPlacement placement = spy(placement().setMode(ObjectPlaceMode.PAINT).setForcePlace(true)
                .setWarp(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setMultiplier(4)));
        CNG warp = mock(CNG.class);
        when(warp.fitDouble(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(2D);
        doReturn(warp).when(placement).getSurfaceWarp(any(RNG.class), any());
        when(engine.getNativeStructureVolumes(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(
                new KList<>(NativeStructureVolume.of("test", 3, 80, 2, 3, 80, 2)));

        assertEquals(-1, lineObject(3).place(0, -1, 0, placer, placement, new RNG(2L), data));
        assertTrue(placer.writes().isEmpty());

        when(engine.getNativeStructureVolumes(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(NativeStructureVolume.NONE);
        lineObject(3).place(0, -1, 0, placer, placement, new RNG(2L), data);
        assertEquals(3, placer.writes().size());
        assertTrue(placer.writes().stream().allMatch(write -> write.x() >= 1 && write.x() <= 3
                && write.z() == 2 && write.y() == 80));
    }

    @Test
    public void paintSupportChecksTheThicknessOfTheSelectedLowerLedge() {
        RecordingPlacer placer = new RecordingPlacer(null);
        placer.terrain(1, 0, 0, 20, 80, 80, 150, 160);
        IrisObjectPlacement placement = placement().setMode(ObjectPlaceMode.PAINT)
                .setRequireSurfaceSupport(true).setSurfaceSupportBuffer(0).setSurfaceSupportDepth(2);

        assertEquals(-1, lineObject(3).place(0, -1, 0, placer, placement, new RNG(2L), data));
        assertTrue(placer.writes().isEmpty());
    }

    private IrisObjectPlacement placement() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setMode(ObjectPlaceMode.CENTER_HEIGHT);
        placement.setRequireSurfaceSupport(false);
        return placement;
    }

    private IrisObject lineObject(int width) {
        IrisObject object = new IrisObject(width, 1, 1);
        for (int x = 0; x < width; x++) {
            object.setUnsigned(x, 0, 0, solid);
        }
        return object;
    }

    private IrisObject boxObject(int width, int height, int depth) {
        IrisObject object = new IrisObject(width, height, depth);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    object.setUnsigned(x, y, z, solid);
                }
            }
        }
        return object;
    }

    private IrisObject object(String key) {
        IrisObject object = lineObject(1);
        object.setLoadKey(key);
        return object;
    }

    private static PlatformBlockState state(String key, boolean solid) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(key);
        when(state.isSolid()).thenReturn(solid);
        when(state.isOccluding()).thenReturn(solid);
        return state;
    }

    private static final class RecordingPlacer implements IObjectPlacer {
        private final List<BlockWrite> writes = new ArrayList<>();
        private final Map<String, PlatformBlockState> world = new HashMap<>();
        private final Map<String, Object> data = new HashMap<>();
        private final List<String> sampledColumns = new ArrayList<>();
        private final Map<String, int[]> terrain = new HashMap<>();
        private final Engine engine;
        private int failAfterWrites = Integer.MAX_VALUE;
        private boolean debugSmartBore;
        private boolean highestFollowsWrites;

        private RecordingPlacer(Engine engine) {
            this.engine = engine;
        }

        private void failAfterWrites(int writes) {
            failAfterWrites = writes;
        }

        private void setDebugSmartBore(boolean debugSmartBore) {
            this.debugSmartBore = debugSmartBore;
        }

        private void terrain(int x, int z, int... spans) {
            terrain.put(x + ":" + z, spans);
        }

        private int highest(int x, int z) {
            int[] spans = terrain.get(x + ":" + z);
            return (spans == null ? ANCHOR_Y : spans[spans.length - 1])
                    + (highestFollowsWrites && !writes.isEmpty() ? 1000 : 0);
        }

        private List<BlockWrite> writes() {
            return writes;
        }

        private List<BlockWrite> writesOf(PlatformBlockState state) {
            return writes.stream().filter(write -> write.state() == state).toList();
        }

        private List<String> sampledColumns() {
            return sampledColumns;
        }

        @Override
        public int getHighest(int x, int z, IrisData data) {
            sampledColumns.add(x + ":" + z);
            return highest(x, z);
        }

        @Override
        public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
            sampledColumns.add(x + ":" + z);
            return highest(x, z);
        }

        @Override
        public void set(int x, int y, int z, PlatformBlockState state) {
            if (writes.size() >= failAfterWrites) {
                throw new IllegalStateException("write failed");
            }
            writes.add(new BlockWrite(x, y, z, state));
            world.put(x + ":" + y + ":" + z, state);
        }

        @Override
        public PlatformBlockState get(int x, int y, int z) {
            return world.get(x + ":" + y + ":" + z);
        }

        @Override
        public boolean isPreventingDecay() {
            return false;
        }

        @Override
        public boolean isCarved(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isSurfaceSolid(int x, int y, int z) {
            int[] spans = terrain.get(x + ":" + z);
            if (spans == null) {
                return y >= 0 && y <= ANCHOR_Y;
            }
            for (int index = 0; index < spans.length; index += 2) {
                if (y >= spans[index] && y <= spans[index + 1]) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            PlatformBlockState state = get(x, y, z);
            return state != null && state.isSolid();
        }

        @Override
        public boolean isUnderwater(int x, int z) {
            return false;
        }

        @Override
        public int getFluidHeight() {
            return 0;
        }

        @Override
        public boolean isDebugSmartBore() {
            return debugSmartBore;
        }

        @Override
        public void setTile(int x, int y, int z, TileData tile) {
            setData(x, y, z, tile);
        }

        @Override
        public <T> void setData(int x, int y, int z, T data) {
            this.data.put(x + ":" + y + ":" + z + ":" + data.getClass().getName(), data);
        }

        @Override
        public <T> T getData(int x, int y, int z, Class<T> type) {
            return type.cast(data.get(x + ":" + y + ":" + z + ":" + type.getName()));
        }

        @Override
        public Engine getEngine() {
            return engine;
        }
    }

    private record BlockWrite(int x, int y, int z, PlatformBlockState state) {
    }
}
