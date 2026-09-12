package art.arcane.iris.client;

import art.arcane.iris.platform.protocol.IrisTileEncoder;
import art.arcane.iris.spi.protocol.IrisMessage;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Every structure here is filled from the wire, so its size is a dial the server holds. Caps mean a hostile or
 * merely buggy server can waste bandwidth but not the client's heap.
 */
public class IrisClientWireBoundsTest {
    @Test
    public void markerTilesBeyondTheCapEvictTheOldest() {
        IrisClientMarkers markers = new IrisClientMarkers();
        int overflow = IrisClientMarkers.MAX_TILES + 8;
        for (int tileX = 0; tileX < overflow; tileX++) {
            markers.onMarkers(new IrisMessage.VisionMarkers(tileX, 0, 0,
                    List.of(new IrisMessage.VisionMarkers.Marker(tileX, 0, 0, "m" + tileX))));
        }

        assertEquals(IrisClientMarkers.MAX_TILES, markers.trackedTiles());
        assertNull("the oldest marker tile must be gone", markers.forTile(new IrisTileKey(0, 0, 0)));
        assertNotNull("the newest marker tile must be retained", markers.forTile(new IrisTileKey(overflow - 1, 0, 0)));
    }

    @Test
    public void markerTileIsReplacedNotAccumulated() {
        IrisClientMarkers markers = new IrisClientMarkers();
        IrisTileKey key = new IrisTileKey(4, 5, 1);
        markers.onMarkers(new IrisMessage.VisionMarkers(4, 5, 1,
                List.of(new IrisMessage.VisionMarkers.Marker(1, 1, 0, "first"),
                        new IrisMessage.VisionMarkers.Marker(2, 2, 0, "second"))));
        markers.onMarkers(new IrisMessage.VisionMarkers(4, 5, 1,
                List.of(new IrisMessage.VisionMarkers.Marker(3, 3, 0, "third"))));

        assertEquals(1, markers.forTile(key).size());
        assertEquals(1, markers.trackedTiles());
    }

    @Test
    public void pregenJobsBeyondTheCapEvictTheOldest() {
        IrisClientPregenState pregen = new IrisClientPregenState();
        int overflow = IrisClientPregenState.MAX_JOBS + 5;
        for (int jobId = 0; jobId < overflow; jobId++) {
            pregen.onProgress(progress(jobId));
        }

        assertEquals(IrisClientPregenState.MAX_JOBS, pregen.trackedJobs());
        assertEquals(Long.valueOf(overflow - 1L), pregen.activeJobId());
        assertNotNull(pregen.active());
    }

    @Test
    public void pregenPanelGoesStaleThenExpiresWithoutUpdates() {
        AtomicLong clock = new AtomicLong(10_000L);
        IrisClientPregenState pregen = new IrisClientPregenState(clock::get);
        pregen.onProgress(progress(1L));

        assertEquals(0L, pregen.activeAgeMillis());
        assertFalse(pregen.activeStale());
        assertFalse(pregen.activeExpired());

        clock.addAndGet(IrisClientPregenState.STALE_AFTER_MILLIS);
        assertTrue(pregen.activeStale());
        assertFalse(pregen.activeExpired());

        clock.addAndGet(IrisClientPregenState.EXPIRE_AFTER_MILLIS);
        assertTrue(pregen.activeExpired());

        pregen.onProgress(progress(1L));
        assertFalse("a fresh frame must revive the panel", pregen.activeStale());
    }

    @Test
    public void noActiveJobIsNeitherStaleNorExpired() {
        AtomicLong clock = new AtomicLong(0L);
        IrisClientPregenState pregen = new IrisClientPregenState(clock::get);

        assertEquals(-1L, pregen.activeAgeMillis());
        assertFalse(pregen.activeStale());
        assertFalse(pregen.activeExpired());

        pregen.onProgress(progress(7L));
        pregen.onEnd(7L);

        assertNull(pregen.active());
        assertEquals(-1L, pregen.activeAgeMillis());
        assertFalse(pregen.activeExpired());
    }

    @Test
    public void tileCacheKeepsTheDefaultBudgetForASmallViewport() {
        assertEquals(IrisClientTileCache.DEFAULT_MAX_CACHED_TILES, retainedTiles(4));
    }

    @Test
    public void tileCacheGrowsForALargeViewportButNotPastTheCeiling() {
        assertEquals(IrisClientTileCache.ABSOLUTE_MAX_CACHED_TILES,
                retainedTiles(IrisClientTileCache.ABSOLUTE_MAX_CACHED_TILES * 4));
    }

    @Test
    public void malformedTileIsDroppedAndCountedWithoutPoisoningTheCache() {
        IrisClientTileCache cache = new IrisClientTileCache(frame -> {
        }, () -> 0L);
        byte[] garbage = new byte[32];
        Arrays.fill(garbage, (byte) 0x7F);

        cache.onVisionTile(new IrisMessage.VisionTile(0, 0, 0, 1, 0, 1, garbage));

        assertEquals(1L, cache.droppedMalformedCount());
        assertNull(cache.get(new IrisTileKey(0, 0, 0)));

        cache.onVisionTile(flatTile(0));
        assertNotNull("a bad frame must not block later tiles", cache.get(new IrisTileKey(0, 0, 0)));
    }

    /**
     * Capacity is not exposed, so it is measured the only way that matters: how many tiles survive. Tiles go in
     * through the real wire path, so each one is a decoded image, not a stub.
     */
    private static int retainedTiles(int viewportTiles) {
        IrisClientTileCache cache = new IrisClientTileCache(frame -> {
        }, () -> 0L);
        cache.ensureCapacity(viewportTiles);
        int expected = Math.max(IrisClientTileCache.DEFAULT_MAX_CACHED_TILES,
                Math.min(IrisClientTileCache.ABSOLUTE_MAX_CACHED_TILES, viewportTiles));
        int inserted = expected + 8;
        for (int tileX = 0; tileX < inserted; tileX++) {
            cache.onVisionTile(flatTile(tileX));
        }
        int retained = 0;
        for (int tileX = 0; tileX < inserted; tileX++) {
            if (cache.get(new IrisTileKey(tileX, 0, 0)) != null) {
                retained++;
            }
        }
        return retained;
    }

    private static IrisMessage.VisionTile flatTile(int tileX) {
        int[] pixels = new int[4];
        Arrays.fill(pixels, 0xFF102030);
        return new IrisMessage.VisionTile(tileX, 0, 0, 1, 0, 1, IrisTileEncoder.encodePixels(pixels, 2, 2));
    }

    private static IrisMessage.PregenProgress progress(long jobId) {
        return new IrisMessage.PregenProgress(jobId, 10L, 100L, 5.0D, 1000L, IrisMessage.PregenProgress.STATE_RUNNING);
    }
}
