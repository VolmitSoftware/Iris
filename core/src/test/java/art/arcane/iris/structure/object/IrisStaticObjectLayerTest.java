package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.value.IrisPosition;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
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
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IrisStaticObjectLayerTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private final Map<String, PlatformBlockState> states = new HashMap<>();
    private IrisData data;
    private ResourceLoader<IrisObject> loader;
    private IrisObjectRotation.StateRotator previousRotator;

    @Before
    public void setup() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(call -> state(call.getArgument(0)));
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
        when(mantle.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.getOrCreate(anyInt())).thenReturn(section);
        when(section.getSlice(TileWrapper.class)).thenReturn(tiles);
        Hunk<PlatformBlockState> left = Hunk.newArrayHunk(16, 384, 16);
        Hunk<PlatformBlockState> right = Hunk.newArrayHunk(16, 384, 16);

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

    private PlatformBlockState state(String key) {
        return states.computeIfAbsent(key.toLowerCase(), value -> {
            PlatformBlockState block = mock(PlatformBlockState.class);
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
