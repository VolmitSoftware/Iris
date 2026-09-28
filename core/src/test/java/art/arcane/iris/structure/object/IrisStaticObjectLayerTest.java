package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.mantle.ObjectContinuationBundle;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.value.IrisPosition;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.GenerationBoundary;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IrisStaticObjectLayerTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private final Map<String, NativeBlockState> states = new HashMap<>();
    private IrisData data;
    private ResourceLoader<IrisObject> loader;
    private IrisObjectRotation.StateRotator previousRotator;

    @Before
    public void setup() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(call -> state(call.getArgument(0)));
        when(registries.decodeBlockState(anyString())).thenAnswer(call -> state(call.getArgument(0)));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        previousRotator = IrisObjectRotation.bindPlatformRotator((rotation, block, x, y, z) -> block);
        data = mock(IrisData.class);
        loader = mock(ResourceLoader.class);
        when(data.getObjectLoader()).thenReturn(loader);
    }

    @After
    public void cleanup() {
        IrisObjectRotation.restorePlatformRotator(previousRotator);
        IrisPlatforms.unbind();
    }

    @Test
    public void emptyConfigurationDoesNotLoadObjectsOrTouchWorldData() {
        IrisStaticObjectLayer layer = IrisStaticObjectLayer.compile(new IrisDimension(), data);
        Engine engine = mock(Engine.class);
        layer.apply(engine, 0, 0, Hunk.newArrayHunk(16, 384, 16));

        assertTrue(layer.isEmpty());
        assertFalse(layer.contains(0, 0, 0));
        verifyNoInteractions(engine, loader);
    }

    @Test
    public void globalScalingKeepsSavedOriginsAndExplicitEntrySizes() {
        IrisObject cube = object("cube", 4, 4, 4);
        for (int x = -2; x < 2; x++) {
            for (int y = -2; y < 2; y++) {
                for (int z = -2; z < 2; z++) {
                    cube.getBlocks().put(new IrisBlockVector(x, y, z), state("minecraft:stone"));
                }
            }
        }
        for (double factor : new double[]{2D, 0.5D}) {
            IrisDimension dimension = new IrisDimension().setAllObjectScaleFactor(factor)
                    .setStaticObjects(new KList<>(entry("cube", 16, 100, 16),
                            entry("cube", 48, 100, 16).setScale(1D),
                            entry("cube", 80, 100, 16).setScale(1D / factor)));
            IrisStaticObjectLayer layer = dimension.getStaticObjectLayer(data);
            int[] counts = new int[3];
            for (int chunkX = 0; chunkX < 6; chunkX++) {
                for (int chunkZ = 0; chunkZ < 2; chunkZ++) {
                    counts[chunkX / 2] += layer.blocks(chunkX, chunkZ).size();
                }
            }
            assertEquals(factor == 2D ? 512 : 8, counts[0]);
            assertEquals(64, counts[1]);
            assertEquals(factor == 2D ? 8 : 512, counts[2]);
            int halfWidth = factor == 2D ? 4 : 1;
            assertTrue(layer.contains(16 - halfWidth, 164 - halfWidth, 16 - halfWidth));
            assertFalse(layer.contains(15 - halfWidth, 164 - halfWidth, 16 - halfWidth));
            dimension.setAllObjectScaleFactor(1D);
            assertNotSame(layer, dimension.getStaticObjectLayer(data));
        }
        assertEquals(64, cube.getBlocks().size());
        assertEquals(4, cube.getW());
    }

    @Test
    public void rotatedNegativeCoordinatesAndMinusOneYUseExactWorldOrigin() {
        IrisObject object = object("cross", 5, 3, 5);
        object.getBlocks().put(new IrisBlockVector(-2, -1, 0), state("minecraft:gold_block"));
        object.getBlocks().put(new IrisBlockVector(2, 1, 0), state("minecraft:diamond_block"));
        object.getBlocks().put(new IrisBlockVector(0, 0, 2), state("minecraft:emerald_block"));
        IrisStaticObject entry = entry("cross", 15, -1, -16)
                .setRotation(new IrisStaticObjectRotation().setY(90));
        IrisStaticObjectLayer layer = compile(entry);

        assertTrue(layer.contains(15, 62, -14));
        assertTrue(layer.contains(15, 64, -18));
        assertTrue(layer.contains(17, 63, -16));
        assertEquals(1, layer.blocks(0, -1).size());
        assertEquals(1, layer.blocks(0, -2).size());
        assertEquals(1, layer.blocks(1, -1).size());
        assertEquals(62, layer.blocks(0, -1).getFirst().y());
        assertEquals(3, object.getBlocks().size());
    }

    @Test
    public void laterEntryAndBoreClearEarlierBlocksAndTiles() {
        IrisObject chest = object("chest", 1, 1, 1);
        chest.getBlocks().put(new IrisBlockVector(0, 0, 0), state("minecraft:chest"));
        chest.getStates().put(new IrisBlockVector(0, 0, 0), new TileData("minecraft:chest", new KMap<>()));
        IrisObject replacement = object("replacement", 3, 1, 1);
        replacement.getBlocks().put(new IrisBlockVector(-1, 0, 0), state("minecraft:stone"));
        replacement.getBlocks().put(new IrisBlockVector(1, 0, 0), state("minecraft:stone"));
        IrisStaticObjectLayer layer = compile(entry("chest", 0, 100, 0),
                entry("replacement", 0, 100, 0).setBore(true));

        IrisStaticObjectLayer.Block center = block(layer, 0, 164, 0);
        assertSame(IrisObject.States.air(), center.state());
        assertNull(center.tile());
        assertEquals(1, chest.getStates().size());
    }

    @Test
    public void chunkWritesStayLocalAndRemoveReplacedTileMetadata() {
        IrisObject object = object("line", 3, 1, 1);
        for (int x = -1; x <= 1; x++) {
            object.getBlocks().put(new IrisBlockVector(x, 0, 0), state("minecraft:stone"));
        }
        IrisStaticObjectLayer layer = compile(entry("line", 15, 0, -1));
        Engine engine = mock(Engine.class);
        EngineMantle engineMantle = mock(EngineMantle.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        Matter section = mock(Matter.class);
        MatterSlice<TileWrapper> tiles = mock(MatterSlice.class);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(mantle.useChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.getOrCreate(anyInt())).thenReturn(section);
        when(section.getSlice(TileWrapper.class)).thenReturn(tiles);
        when(chunk.isFlagged(MantleFlag.REAL)).thenReturn(true);
        Hunk<NativeBlockState> left = Hunk.newArrayHunk(16, 384, 16);
        Hunk<NativeBlockState> right = Hunk.newArrayHunk(16, 384, 16);

        layer.apply(engine, 16, -16, right);
        layer.apply(engine, 0, -16, left);

        assertSame(state("minecraft:stone"), left.get(14, 64, 15));
        assertSame(state("minecraft:stone"), left.get(15, 64, 15));
        assertSame(state("minecraft:stone"), right.get(0, 64, 15));
        assertNull(right.get(1, 64, 15));
        verify(tiles).set(0, 0, 15, null);
        verify(tiles).set(14, 0, 15, null);
        verify(tiles).set(15, 0, 15, null);
    }

    @Test
    public void transitionRejectsWholeRotatedPlacementBeforeChunkWrites() {
        IrisObject object = object("rotated", 1, 1, 5);
        object.getBlocks().put(new IrisBlockVector(0, 0, -2), state("minecraft:stone"));
        object.getBlocks().put(new IrisBlockVector(0, 0, 2), state("minecraft:stone"));
        IrisStaticObjectLayer layer = compile(entry("rotated", 15, 0, 0)
                .setRotation(new IrisStaticObjectRotation().setY(90)));
        assertFalse(layer.blocks(0, 0).isEmpty());
        assertFalse(layer.blocks(1, 0).isEmpty());
        Engine runtime = historicalChunk(0, 0);
        IrisStaticObjectLayer filtered = layer.forTransition(runtime);

        assertTrue(filtered.isEmpty());
        assertFalse(filtered.contains(17, 64, 0));
        assertSame(filtered, layer.forTransition(runtime));
        Engine engine = mock(Engine.class);
        filtered.apply(engine, 16, 0, Hunk.newArrayHunk(16, 384, 16));
        verifyNoInteractions(engine);
        assertSame(layer, layer.forTransition(null));
    }

    @Test
    public void rejectedLaterPlacementRetainsEarlierBlocksAndTiles() {
        IrisObject chest = object("chest", 1, 1, 1);
        chest.getBlocks().put(new IrisBlockVector(0, 0, 0), state("minecraft:chest"));
        TileData tile = new TileData("minecraft:chest", new KMap<>());
        chest.getStates().put(new IrisBlockVector(0, 0, 0), tile);
        IrisObject bridge = object("bridge", 5, 1, 1);
        bridge.getBlocks().put(new IrisBlockVector(-2, 0, 0), state("minecraft:stone"));
        bridge.getBlocks().put(new IrisBlockVector(2, 0, 0), state("minecraft:stone"));
        IrisStaticObjectLayer layer = compile(entry("chest", 17, 0, 0),
                entry("bridge", 15, 0, 0).setBore(true));
        assertSame(state("minecraft:stone"), block(layer, 17, 64, 0).state());
        IrisStaticObjectLayer filtered = layer.forTransition(historicalChunk(0, 0));

        assertSame(state("minecraft:chest"), block(filtered, 17, 64, 0).state());
        assertEquals(tile, block(filtered, 17, 64, 0).tile());
        assertFalse(filtered.contains(16, 64, 0));
        assertTrue(layer.forTransition(historicalChunk(1, 0)).isEmpty());
    }

    @Test
    public void continuationOverlapRejectsWholePlacementWithoutRejectingOtherCellsInTheChunk() {
        IrisObject line = object("line", 3, 1, 1);
        for (int x = -1; x <= 1; x++) {
            line.getBlocks().put(new IrisBlockVector(x, 0, 0), state("minecraft:stone"));
        }
        IrisStaticObjectLayer layer = compile(entry("line", 17, 0, 0), entry("line", 24, 0, 0));
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getTransitionGenerationPlan()).thenReturn(mock(TransitionGenerationPlan.class));
        when(complex.allowsNewGenerationFootprint(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(call -> (int) call.getArgument(0) > 18);
        Engine runtime = runtime(complex);
        IrisStaticObjectLayer filtered = layer.forTransition(runtime);

        assertFalse(filtered.contains(16, 64, 0));
        assertFalse(filtered.contains(18, 64, 0));
        assertTrue(filtered.contains(23, 64, 0));
        assertTrue(filtered.contains(25, 64, 0));
        verify(complex).allowsNewGenerationFootprint(16, 0, 18, 0);
        verify(complex).allowsNewGenerationFootprint(23, 0, 25, 0);
    }

    private static Engine historicalChunk(int x, int z) {
        GenerationBoundary boundary = GenerationBoundary.freeze("static-test",
                List.of(new GenerationBoundary.ChunkCoordinate(x, z)));
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getTransitionGenerationPlan()).thenReturn(mock(TransitionGenerationPlan.class));
        when(complex.allowsNewGenerationFootprint(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(call -> !boundary.intersectsHistoricalBlocks(call.getArgument(0), call.getArgument(1),
                        call.getArgument(2), call.getArgument(3)));
        return runtime(complex);
    }

    private static Engine runtime(IrisComplex complex) {
        Engine engine = mock(Engine.class);
        when(engine.getComplex()).thenReturn(complex);
        return engine;
    }

    @Test
    public void routerAttachmentInvalidatesEarlierStaticAdmission() {
        IrisObject object = object("single", 1, 1, 1);
        object.getBlocks().put(new IrisBlockVector(0, 0, 0), state("minecraft:stone"));
        IrisStaticObjectLayer layer = compile(entry("single", 16, 0, 0));
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getTransitionGenerationPlan()).thenReturn(mock(TransitionGenerationPlan.class));
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());
        when(complex.allowsNewGenerationFootprint(16, 0, 16, 0)).thenReturn(true);
        assertSame(layer, layer.forTransition(engine));

        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(mock(GenerationHistoryRuntimeRouter.class)));
        when(complex.allowsNewGenerationFootprint(16, 0, 16, 0)).thenReturn(false);
        assertTrue(layer.forTransition(engine).isEmpty());
    }

    @Test
    public void placementKeepsSavedTilesWithoutSharingTheirInstances() {
        IrisObject object = object("chest", 1, 1, 1);
        object.getBlocks().put(new IrisBlockVector(0, 0, 0), state("minecraft:chest"));
        TileData tile = new TileData("minecraft:chest", new KMap<>());
        object.getStates().put(new IrisBlockVector(0, 0, 0), tile);
        IrisStaticObjectLayer layer = compile(entry("chest", 100, 100, -100));

        assertEquals(tile, block(layer, 100, 164, -100).tile());
        assertNotSame(tile, block(layer, 100, 164, -100).tile());
    }

    @Test
    public void rejectsMissingObjectsAndTransformedHeightOverflow() {
        assertThrows(IllegalArgumentException.class, () -> compile(entry("missing", 0, 0, 0)));
        IrisObject object = object("tall", 1, 3, 1);
        object.getBlocks().put(new IrisBlockVector(0, 1, 0), state("minecraft:stone"));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> compile(entry("tall", 0, 319, 0)));
        assertTrue(failure.getMessage().contains("staticObjects[0]"));
        assertTrue(failure.getMessage().contains("outside the dimension height"));
    }

    @Test
    public void savedStaticPlacementRetainsPendingBlocksCustomIdentityAndTiles() throws Exception {
        NativeBlockState chest = state("minecraft:chest");
        NativeBlockState custom = state("test:custom_chest");
        when(custom.isCustom()).thenReturn(true);
        when(custom.deferredPlacementKey()).thenReturn("test:custom_chest");
        when(custom.placementBaseState()).thenReturn(chest);
        IrisObject object = object("crossing", 3, 1, 1);
        object.getBlocks().put(new IrisBlockVector(-1, 0, 0), state("minecraft:stone"));
        object.getBlocks().put(new IrisBlockVector(0, 0, 0), custom);
        object.getBlocks().put(new IrisBlockVector(1, 0, 0), state("minecraft:stone"));
        TileData tile = new TileData("minecraft:chest", new KMap<>());
        object.getStates().put(new IrisBlockVector(0, 0, 0), tile);
        IrisStaticObjectLayer layer = compile(entry("crossing", 16, 0, 0), entry("crossing", 48, 0, 0));
        ContinuationFixture fixture = new ContinuationFixture();
        TileData.TileReader previous = TileData.bindPlatformReader(input -> {
            String material = input.readUTF();
            assertEquals("{}", input.readUTF());
            return new TileData(material, new KMap<>());
        });
        try {
            layer.apply(fixture.engine, 0, 0, Hunk.newArrayHunk(16, 384, 16));
            ObjectContinuationBundle pending = fixture.bundle(1, 0);
            assertNotNull(pending);
            assertEquals(1, pending.fragments().size());
            ObjectContinuationBundle.Fragment fragment = pending.fragments().getFirst();
            assertEquals(ObjectContinuationBundle.Kind.STATIC, fragment.key().kind());
            assertEquals(0, fragment.key().ordinal());
            Matter restored = fragment.decode();
            assertSame(chest, restored.getSlice(NativeBlockState.class).get(0, 64, 0));
            assertSame(state("minecraft:stone"), restored.getSlice(NativeBlockState.class).get(1, 64, 0));
            assertEquals(Identifier.fromString("test:custom_chest"), restored.getSlice(Identifier.class).get(0, 64, 0));
            assertEquals(tile, restored.<TileWrapper>getSlice(TileWrapper.class).get(0, 64, 0).getData());
            assertNull(fixture.bundle(2, 0));
            assertNull(fixture.bundle(3, 0));
            layer.apply(fixture.engine, 0, 0, Hunk.newArrayHunk(16, 384, 16));
            assertSame(pending, fixture.bundle(1, 0));
        } finally {
            TileData.restorePlatformReader(previous);
        }
    }

    @Test
    public void realStaticDestinationsDoNotBuildOrEncodeContinuationPayloads() {
        NativeBlockState stone = state("minecraft:stone");
        IrisObject object = object("crossing", 3, 1, 1);
        object.getBlocks().put(new IrisBlockVector(-1, 0, 0), stone);
        object.getBlocks().put(new IrisBlockVector(1, 0, 0), stone);
        IrisStaticObjectLayer layer = compile(entry("crossing", 16, 0, 0));
        ContinuationFixture fixture = new ContinuationFixture();
        when(fixture.chunk(0, 0).isFlagged(MantleFlag.REAL)).thenReturn(true);
        when(fixture.chunk(1, 0).isFlagged(MantleFlag.REAL)).thenReturn(true);
        clearInvocations(stone);

        layer.apply(fixture.engine, 0, 0, Hunk.newArrayHunk(16, 384, 16));

        assertNull(fixture.bundle(0, 0));
        assertNull(fixture.bundle(1, 0));
        verify(stone, never()).key();
    }

    private static final class ContinuationFixture {
        private final Engine engine = mock(Engine.class);
        private final Map<ObjectContinuationBundle.ChunkPosition, MantleChunk<Matter>> chunks = new HashMap<>();

        @SuppressWarnings("unchecked")
        private ContinuationFixture() {
            IrisMatterSupport.ensureRegistered();
            EngineMantle engineMantle = mock(EngineMantle.class);
            Mantle<Matter> mantle = mock(Mantle.class);
            when(engine.getMantle()).thenReturn(engineMantle);
            when(engineMantle.getMantle()).thenReturn(mantle);
            when(mantle.getWorldHeight()).thenReturn(384);
            when(mantle.useChunk(anyInt(), anyInt())).thenAnswer(call -> chunk(call.getArgument(0), call.getArgument(1)));
        }

        @SuppressWarnings("unchecked")
        private MantleChunk<Matter> chunk(int x, int z) {
            return chunks.computeIfAbsent(new ObjectContinuationBundle.ChunkPosition(x, z), key -> {
                MantleChunk<Matter> chunk = mock(MantleChunk.class);
                Map<Integer, Matter> sections = new HashMap<>();
                when(chunk.get(anyInt())).thenAnswer(call -> sections.get((int) call.getArgument(0)));
                when(chunk.getOrCreate(anyInt())).thenAnswer(call -> sections.computeIfAbsent(call.getArgument(0),
                        section -> new IrisMatter(16, 16, 16)));
                return chunk;
            });
        }

        private ObjectContinuationBundle bundle(int x, int z) {
            MantleChunk<Matter> chunk = chunks.get(new ObjectContinuationBundle.ChunkPosition(x, z));
            Matter section = chunk == null ? null : chunk.get(0);
            MatterSlice<ObjectContinuationBundle> slice = section == null ? null : section.getSlice(ObjectContinuationBundle.class);
            return slice == null ? null : slice.get(0, 0, 0);
        }
    }

    @Test
    public void repeatedCompilationDoesNotChangeSavedBlocksOrPlacement() {
        IrisObject object = object("line", 3, 1, 1);
        object.getBlocks().put(new IrisBlockVector(-1, 0, 0), state("minecraft:stone"));
        object.getBlocks().put(new IrisBlockVector(1, 0, 0), state("minecraft:stone"));
        IrisStaticObject entry = entry("line", 100, 100, -100).setBore(true).setSeed(948L);
        IrisStaticObjectLayer first = compile(entry);
        IrisStaticObjectLayer second = compile(entry);

        assertEquals(first.blocks(6, -7), second.blocks(6, -7));
        assertEquals(2, object.getBlocks().size());
    }

    private IrisStaticObjectLayer compile(IrisStaticObject... entries) {
        return IrisStaticObjectLayer.compile(new IrisDimension().setStaticObjects(new KList<>(entries)), data);
    }

    private IrisObject object(String key, int width, int height, int depth) {
        IrisObject object = new IrisObject(width, height, depth);
        object.setLoadKey(key);
        object.setLoader(data);
        when(loader.load(key)).thenReturn(object);
        return object;
    }

    private static IrisStaticObject entry(String key, int x, int y, int z) {
        return new IrisStaticObject().setObject(key).setPosition(new IrisPosition(x, y, z));
    }

    private static IrisStaticObjectLayer.Block block(IrisStaticObjectLayer layer, int x, int y, int z) {
        List<IrisStaticObjectLayer.Block> blocks = layer.blocks(x >> 4, z >> 4);
        return blocks.stream().filter(block -> block.x() == (x & 15) && block.y() == y && block.z() == (z & 15))
                .findFirst().orElseThrow();
    }

    private NativeBlockState state(String key) {
        return states.computeIfAbsent(key.toLowerCase(), value -> {
            NativeBlockState block = mock(NativeBlockState.class);
            when(block.key()).thenReturn(value);
            when(block.materialKey()).thenReturn(value);
            when(block.isAir()).thenReturn(value.contains("air"));
            when(block.isSolid()).thenReturn(!value.contains("air"));
            when(block.isOccluding()).thenReturn(!value.contains("air"));
            when(block.placementBaseState()).thenReturn(block);
            return block;
        });
    }
}
