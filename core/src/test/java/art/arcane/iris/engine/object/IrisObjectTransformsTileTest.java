package art.arcane.iris.engine.object;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.common.math.Vector3i;
import art.arcane.volmlib.util.collection.KMap;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisObjectTransformsTileTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        Map<String, PlatformBlockState> states = new HashMap<>();
        when(registries.block(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return states.computeIfAbsent(key, IrisObjectTransformsTileTest::state);
        });
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void rotationOmitsUnsupportedBlocksAndTheirTiles() {
        IrisObject source = new IrisObject(3, 1, 1);
        PlatformBlockState unsupported = state("minecraft:wall_torch");
        source.setUnsigned(0, 0, 0, unsupported);
        source.setUnsignedTile(0, 0, 0, tile("minecraft:wall_torch", "omitted"));
        source.setUnsigned(1, 0, 0, state("minecraft:chest"));
        source.setUnsignedTile(1, 0, 0, tile("minecraft:chest", "kept"));
        source.setUnsigned(2, 0, 0, state("minecraft:stone"));
        IrisObjectRotation.StateRotator previous = IrisObjectRotation.bindPlatformRotator(
                (rotation, block, x, y, z) -> block == unsupported ? null : block);
        IrisObject rotated;
        try {
            rotated = source.rotateCopy(IrisObjectRotation.of(0, 90, 0));
        } finally {
            IrisObjectRotation.restorePlatformRotator(previous);
        }

        assertEquals(2, rotated.getBlocks().size());
        assertEquals(1, rotated.getStates().size());
        for (Map.Entry<IrisBlockVector, TileData> entry : rotated.getStates()) {
            assertEquals("minecraft:chest", rotated.getBlocks().get(entry.getKey()).materialKey());
            assertEquals("kept", entry.getValue().getProperties().get("name"));
        }
        assertEquals(3, source.getBlocks().size());
        assertEquals(2, source.getStates().size());
    }

    @Test
    public void scalingWaitsForMatchingGeometryAndVolume() throws Exception {
        assertConsistentScaling(false);
        assertConsistentScaling(true);
    }

    @Test
    public void enlargedTilesFollowEveryExpandedBlockAndCloneProperties() {
        IrisObject source = new IrisObject(1, 1, 1);
        TileData original = tile("minecraft:chest", "saved inventory");
        source.setUnsigned(0, 0, 0, state("minecraft:chest"));
        source.setUnsignedTile(0, 0, 0, original);

        IrisObject scaled = source.scaled(2, IrisObjectPlacementScaleInterpolator.NONE);

        assertEquals(8, scaled.getBlocks().size());
        assertEquals(8, scaled.getStates().size());
        Set<TileData> copies = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<IrisBlockVector, PlatformBlockState> block : scaled.getBlocks()) {
            TileData copy = scaled.getStates().get(block.getKey());
            assertNotNull(copy);
            assertNotSame(original, copy);
            assertNotSame(original.getProperties(), copy.getProperties());
            assertEquals(original, copy);
            assertTrue(copies.add(copy));
        }
    }

    @Test
    public void reducedTilesStayWithTheWinningBlocks() {
        IrisObject source = new IrisObject(7, 1, 1);
        for (int x = 0; x < 7; x++) {
            String material = x % 2 == 0 ? "minecraft:chest" : "minecraft:barrel";
            source.setUnsigned(x, 0, 0, state(material));
            source.setUnsignedTile(x, 0, 0, tile(material, material));
        }

        IrisObject scaled = source.scaled(0.5, IrisObjectPlacementScaleInterpolator.NONE);

        assertTrue(scaled.getBlocks().size() < source.getBlocks().size());
        assertEquals(scaled.getBlocks().size(), scaled.getStates().size());
        for (Map.Entry<IrisBlockVector, PlatformBlockState> block : scaled.getBlocks()) {
            TileData copy = scaled.getStates().get(block.getKey());
            assertNotNull(copy);
            assertEquals(block.getValue().materialKey(), copy.getMaterialKey());
            assertEquals(block.getValue().materialKey(), copy.getProperties().get("name"));
        }
    }

    @Test
    public void interpolatedTilesOnlyRemainOnTheirMaterial() {
        IrisObject source = new IrisObject(3, 3, 3);
        source.setUnsigned(1, 1, 1, state("minecraft:chest"));
        source.setUnsignedTile(1, 1, 1, tile("minecraft:chest", "saved inventory"));
        source.setUnsigned(2, 1, 1, state("minecraft:stone"));
        for (IrisObjectPlacementScaleInterpolator interpolation : new IrisObjectPlacementScaleInterpolator[] {
                IrisObjectPlacementScaleInterpolator.TRILINEAR,
                IrisObjectPlacementScaleInterpolator.TRICUBIC,
                IrisObjectPlacementScaleInterpolator.TRIHERMITE
        }) {
            IrisObject scaled = source.scaled(2, interpolation);

            assertFalse(scaled.getStates().isEmpty());
            for (Map.Entry<IrisBlockVector, TileData> tile : scaled.getStates()) {
                PlatformBlockState block = scaled.getBlocks().get(tile.getKey());
                assertNotNull(block);
                assertEquals(tile.getValue().getMaterialKey(), block.materialKey());
            }
        }
    }

    @Test
    public void incompatibleSourceTileDoesNotSurviveScaling() {
        IrisObject source = new IrisObject(1, 1, 1);
        source.setUnsigned(0, 0, 0, state("minecraft:stone"));
        source.setUnsignedTile(0, 0, 0, tile("minecraft:chest", "saved inventory"));

        IrisObject scaled = source.scaled(2, IrisObjectPlacementScaleInterpolator.NONE);

        assertTrue(scaled.getStates().isEmpty());
        assertEquals(1, source.getStates().size());
    }

    @Test
    public void enlargedAsymmetricObjectKeepsItsSavedOrigin() {
        IrisObject source = asymmetricObject();

        IrisObject scaled = source.scaledAroundOrigin(2, IrisObjectPlacementScaleInterpolator.NONE);

        assertEquals(new Vector3i(10, 2, 4), scaled.getCenter());
        assertEquals(14, scaled.getW());
        assertEquals(10, scaled.getH());
        assertEquals(6, scaled.getD());
        assertEquals("minecraft:chest", scaled.getBlocks().get(new IrisBlockVector(0, 0, 0)).materialKey());
        assertEquals("minecraft:chest", scaled.getBlocks().get(new IrisBlockVector(1, 1, 1)).materialKey());
        assertEquals("minecraft:stone", scaled.getBlocks().get(new IrisBlockVector(-8, 0, 0)).materialKey());
        assertEquals("minecraft:barrel", scaled.getBlocks().get(new IrisBlockVector(2, 4, 0)).materialKey());
        assertEquals("origin", scaled.getStates().get(new IrisBlockVector(0, 0, 0)).getProperties().get("name"));
        assertEquals(-10, scaled.getAABB().min().getX());
        assertEquals(3, scaled.getAABB().max().getX());
        assertEquals(new Vector3i(5, 1, 2), source.getCenter());
    }

    @Test
    public void reducedAsymmetricObjectKeepsItsSavedOriginAndTiles() {
        IrisObject source = asymmetricObject();

        IrisObject scaled = source.scaledAroundOrigin(0.5, IrisObjectPlacementScaleInterpolator.NONE);

        assertEquals("minecraft:chest", scaled.getBlocks().get(new IrisBlockVector(0, 0, 0)).materialKey());
        assertEquals("minecraft:stone", scaled.getBlocks().get(new IrisBlockVector(-2, 0, 0)).materialKey());
        assertEquals("minecraft:barrel", scaled.getBlocks().get(new IrisBlockVector(0, 1, 0)).materialKey());
        assertEquals("origin", scaled.getStates().get(new IrisBlockVector(0, 0, 0)).getProperties().get("name"));
        assertEquals(3, scaled.getBlocks().size());
        assertEquals(2, scaled.getStates().size());
    }

    @Test
    public void fractionalEnlargementScalesBothSidesOfTheSavedOrigin() {
        IrisObject source = new IrisObject(5, 1, 1);
        source.setUnsigned(0, 0, 0, state("minecraft:stone"));
        source.setUnsigned(2, 0, 0, state("minecraft:chest"));
        source.setUnsigned(4, 0, 0, state("minecraft:barrel"));

        IrisObject scaled = source.scaledAroundOrigin(1.5, IrisObjectPlacementScaleInterpolator.NONE);

        assertEquals("minecraft:stone", scaled.getBlocks().get(new IrisBlockVector(-3, 0, 0)).materialKey());
        assertEquals("minecraft:stone", scaled.getBlocks().get(new IrisBlockVector(-2, 0, 0)).materialKey());
        assertEquals("minecraft:chest", scaled.getBlocks().get(new IrisBlockVector(0, 0, 0)).materialKey());
        assertEquals("minecraft:barrel", scaled.getBlocks().get(new IrisBlockVector(3, 0, 0)).materialKey());
        assertEquals("minecraft:barrel", scaled.getBlocks().get(new IrisBlockVector(4, 0, 0)).materialKey());
    }

    @Test
    public void untypedTilesRemainAttachedToTheirScaledSourceMaterial() {
        IrisObject source = new IrisObject(1, 1, 1);
        TileData tile = mock(TileData.class);
        when(tile.clone()).thenReturn(tile);
        source.setUnsigned(0, 0, 0, state("minecraft:chest"));
        source.setUnsignedTile(0, 0, 0, tile);

        IrisObject scaled = source.scaledAroundOrigin(2, IrisObjectPlacementScaleInterpolator.NONE);

        assertEquals(8, scaled.getStates().size());
    }

    private static void assertConsistentScaling(boolean savedOrigin) throws Exception {
        IrisObject source = new IrisObject(9, 5, 3);
        source.setUnsigned(0, 0, 0, state("minecraft:stone"));
        FutureTask<IrisObject> scaling = new FutureTask<>(() -> savedOrigin
                ? source.scaledAroundOrigin(2, IrisObjectPlacementScaleInterpolator.NONE)
                : source.scaled(2, IrisObjectPlacementScaleInterpolator.NONE));
        Thread worker = new Thread(scaling, "object-scaling-snapshot");
        source.writeLock.lock();
        try {
            worker.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (worker.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            assertEquals(Thread.State.WAITING, worker.getState());
            source.setW(2);
            source.setH(2);
            source.setD(2);
            source.setCenter(new Vector3i(1, 1, 1));
            source.getBlocks().clear();
            source.setUnsigned(0, 0, 0, state("minecraft:chest"));
            source.setUnsignedTile(0, 0, 0, tile("minecraft:chest", "new volume"));
        } finally {
            source.writeLock.unlock();
        }
        try {
            IrisObject actual = scaling.get(5, TimeUnit.SECONDS);
            IrisObject expected = savedOrigin
                    ? source.scaledAroundOrigin(2, IrisObjectPlacementScaleInterpolator.NONE)
                    : source.scaled(2, IrisObjectPlacementScaleInterpolator.NONE);
            assertEquals(expected.getCenter(), actual.getCenter());
            assertEquals(expected.getW(), actual.getW());
            assertEquals(expected.getH(), actual.getH());
            assertEquals(expected.getD(), actual.getD());
            assertEquals(expected.getBlocks().size(), actual.getBlocks().size());
            assertEquals(expected.getStates().size(), actual.getStates().size());
            for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : expected.getBlocks()) {
                assertEquals(entry.getValue(), actual.getBlocks().get(entry.getKey()));
                assertEquals(expected.getStates().get(entry.getKey()), actual.getStates().get(entry.getKey()));
            }
        } finally {
            worker.interrupt();
            worker.join(TimeUnit.SECONDS.toMillis(5));
        }
    }

    private static IrisObject asymmetricObject() {
        IrisObject source = new IrisObject(7, 5, 3);
        source.setCenter(new Vector3i(5, 1, 2));
        source.setUnsigned(5, 1, 2, state("minecraft:chest"));
        source.setUnsignedTile(5, 1, 2, tile("minecraft:chest", "origin"));
        source.setUnsigned(1, 1, 2, state("minecraft:stone"));
        source.setUnsigned(6, 3, 2, state("minecraft:barrel"));
        source.setUnsignedTile(6, 3, 2, tile("minecraft:barrel", "upper"));
        return source;
    }

    private static TileData tile(String material, String name) {
        KMap<String, Object> properties = new KMap<>();
        properties.put("name", name);
        return new TileData(material, properties);
    }

    private static PlatformBlockState state(String key) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(key);
        when(state.isAir()).thenReturn(key.contains("air"));
        return state;
    }
}
