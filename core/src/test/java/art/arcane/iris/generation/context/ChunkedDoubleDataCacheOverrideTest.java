package art.arcane.iris.generation.context;

import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChunkedDoubleDataCacheOverrideTest {
    @Test
    public void reconciledHeightOverridesCachedAndUncachedStreams() {
        ProceduralStream<Double> stream = mock(ProceduralStream.class);
        when(stream.getDouble(17D, 34D)).thenReturn(400D);
        for (boolean cached : new boolean[]{true, false}) {
            ChunkedDoubleDataCache cache = new ChunkedDoubleDataCache(stream, 16, 32, cached);
            assertEquals(400D, cache.getDouble(1, 2), 0D);
            cache.setDouble(1, 2, 23D);
            assertEquals(23D, cache.getDouble(1, 2), 0D);
            assertThrows(IllegalArgumentException.class, () -> cache.setDouble(-1, 2, 10D));
        }
    }
}
