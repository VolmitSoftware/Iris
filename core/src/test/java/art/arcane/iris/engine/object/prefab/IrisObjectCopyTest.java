package art.arcane.iris.engine.object.prefab;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.IrisBlockVector;
import art.arcane.volmlib.util.math.Vector3i;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class IrisObjectCopyTest {
    private static final long WAIT_SECONDS = 5;
    private static final long BLOCKED_PROBE_MILLIS = 150;

    @Test
    public void copyWaitsForVolumeMutationToComplete() throws Exception {
        IrisObject source = new IrisObject(1, 1, 1);
        PlatformBlockState block = mock(PlatformBlockState.class);
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            source.writeLock.lock();
            Future<IrisObject> result;
            try {
                result = executor.submit(() -> {
                    started.countDown();
                    return source.copy();
                });
                assertTrue(started.await(WAIT_SECONDS, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> result.get(BLOCKED_PROBE_MILLIS, TimeUnit.MILLISECONDS));
                source.setW(3);
                source.setCenter(new Vector3i(1, 0, 0));
                source.setUnsigned(2, 0, 0, block);
            } finally {
                source.writeLock.unlock();
            }

            IrisObject copy = result.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals(3, copy.getW());
            assertEquals(new Vector3i(1, 0, 0), copy.getCenter());
            assertSame(block, copy.getBlocks().get(new IrisBlockVector(1, 0, 0)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void copyOwnsItsCenterAndTiles() {
        IrisObject source = new IrisObject(1, 1, 1);
        PlatformBlockState block = mock(PlatformBlockState.class);
        TileData tile = new TileData("minecraft:chest", new KMap<>());
        tile.getProperties().put("name", "source");
        source.setUnsigned(0, 0, 0, block);
        source.setUnsignedTile(0, 0, 0, tile);

        IrisObject copy = source.copy();
        IrisBlockVector position = new IrisBlockVector(0, 0, 0);
        TileData copiedTile = copy.getStates().get(position);
        assertNotSame(source.getCenter(), copy.getCenter());
        assertNotSame(tile, copiedTile);
        assertNotSame(tile.getProperties(), copiedTile.getProperties());
        copiedTile.getProperties().put("name", "copy");
        copy.getBlocks().remove(position);

        assertEquals("source", tile.getProperties().get("name"));
        assertSame(block, source.getBlocks().get(position));
    }
}
