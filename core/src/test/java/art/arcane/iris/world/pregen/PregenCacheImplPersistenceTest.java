package art.arcane.iris.world.pregen;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PregenCacheImplPersistenceTest {
    private File directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("iris-pregen-cache-persist").toFile();
    }

    @Test
    public void partialRegionChunkPersistsAcrossReload() {
        PregenCache cache = PregenCache.create(directory);
        cache.cacheChunk(5, 7);
        cache.write();

        PregenCache reloaded = PregenCache.create(directory);
        assertTrue(reloaded.isChunkCached(5, 7));
        assertFalse(reloaded.isChunkCached(5, 8));
        assertFalse(reloaded.isRegionCached(0, 0));
    }

    @Test
    public void partialRegionNegativeCoordinatesPersistAcrossReload() {
        PregenCache cache = PregenCache.create(directory);
        cache.cacheChunk(-3, -1);
        cache.write();

        PregenCache reloaded = PregenCache.create(directory);
        assertTrue(reloaded.isChunkCached(-3, -1));
        assertFalse(reloaded.isChunkCached(-3, -2));
    }

    @Test
    public void fullRegionPersistsAcrossReload() {
        PregenCache cache = PregenCache.create(directory);
        cache.cacheRegion(2, 3);
        cache.write();

        PregenCache reloaded = PregenCache.create(directory);
        assertTrue(reloaded.isRegionCached(2, 3));
        assertTrue(reloaded.isChunkCached((2 << 5) + 11, (3 << 5) + 19));
        assertFalse(reloaded.isRegionCached(2, 4));
    }

    @Test
    public void regionCompletedChunkByChunkPersistsAsRegion() {
        PregenCache cache = PregenCache.create(directory);
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                cache.cacheChunk(x, z);
            }
        }
        cache.write();

        PregenCache reloaded = PregenCache.create(directory);
        assertTrue(reloaded.isRegionCached(0, 0));
        assertTrue(reloaded.isChunkCached(31, 31));
        assertFalse(reloaded.isChunkCached(32, 0));
    }
}
