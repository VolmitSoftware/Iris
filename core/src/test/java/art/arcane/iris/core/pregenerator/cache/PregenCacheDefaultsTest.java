package art.arcane.iris.core.pregenerator.cache;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

public class PregenCacheDefaultsTest {
    @Test
    public void anAbsentDirectoryResolvesToTheSharedEmptyCache() {
        assertSame(PregenCache.EMPTY, PregenCache.create(null));
    }

    @Test
    public void theEmptyCacheNeverClaimsAChunkItWasToldToCache() {
        PregenCache.EMPTY.cacheChunk(3, -7);
        PregenCache.EMPTY.cacheChunk(0, 0);

        assertFalse(PregenCache.EMPTY.isChunkCached(3, -7));
        assertFalse(PregenCache.EMPTY.isChunkCached(0, 0));
    }

    @Test
    public void theEmptyCacheNeverClaimsARegionItWasToldToCache() {
        PregenCache.EMPTY.cacheRegion(-2, 5);

        assertFalse(PregenCache.EMPTY.isRegionCached(-2, 5));
    }

    @Test
    public void theEmptyCacheSurvivesMaintenanceCalls() {
        PregenCache.EMPTY.write();
        PregenCache.EMPTY.trim(0L);
        PregenCache.EMPTY.trim(60_000L);

        assertFalse(PregenCache.EMPTY.isChunkCached(0, 0));
    }

    @Test
    public void aCacheIsItsOwnSynchronousView() {
        assertSame(PregenCache.EMPTY, PregenCache.EMPTY.sync());
    }
}
