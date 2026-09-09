package art.arcane.iris.engine;

import art.arcane.iris.engine.framework.Engine;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisComplexStudioCacheTest {
    @Test
    public void studioUsesWideNoiseCacheWithoutChangingConfiguredLiveWorldSize() {
        Engine engine = mock(Engine.class);
        when(engine.isStudio()).thenReturn(false);
        assertEquals(1_024, IrisComplex.noiseCacheSize(engine, 1_024, false));
        when(engine.isStudio()).thenReturn(true);
        assertEquals(32_768, IrisComplex.noiseCacheSize(engine, 1_024, false));
        assertEquals(65_536, IrisComplex.noiseCacheSize(engine, 65_536, false));
    }

    @Test
    public void detachedRuntimesKeepTheConfiguredCacheSizeInStudio() {
        // Historical activation runtimes only answer transition-band and saved-biome queries; every
        // one of them carrying the Studio-wide cache multiplied resident memory per hotload.
        Engine engine = mock(Engine.class);
        when(engine.isStudio()).thenReturn(true);
        assertEquals(1_024, IrisComplex.noiseCacheSize(engine, 1_024, true));
        assertEquals(65_536, IrisComplex.noiseCacheSize(engine, 65_536, true));
        when(engine.isStudio()).thenReturn(false);
        assertEquals(1_024, IrisComplex.noiseCacheSize(engine, 1_024, true));
    }
}
