package art.arcane.iris.generation.cache;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class AtomicCacheTest {
    @Test
    public void aquireOnceOrThrowCachesTheComputedValue() {
        AtomicCache<String> cache = new AtomicCache<>();
        AtomicInteger calls = new AtomicInteger();

        assertEquals("value", cache.aquireOnceOrThrow(() -> {
            calls.incrementAndGet();
            return "value";
        }));
        assertEquals("value", cache.aquireOnceOrThrow(() -> {
            calls.incrementAndGet();
            return "other";
        }));
        assertEquals(1, calls.get());
    }

    /**
     * The retrying variant is what a caller that can repair the cause between attempts needs, and the
     * world generator relies on it, so memoization must stay confined to aquireOnceOrThrow.
     */
    @Test
    public void aquireOrThrowStillRetriesAFailedSupplier() {
        AtomicCache<String> cache = new AtomicCache<>();
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> cache.aquireOrThrow(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("repairable");
        }));
        assertEquals("repaired", cache.aquireOrThrow(() -> {
            calls.incrementAndGet();
            return "repaired";
        }));
        assertEquals(2, calls.get());
    }

    @Test
    public void aquireOnceOrThrowMemoizesTheSupplierFailureInsteadOfRerunningIt() {
        AtomicCache<String> cache = new AtomicCache<>();
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException cause = new IllegalStateException("Frozen Iris pack snapshot is missing");

        IllegalStateException first = assertThrows(IllegalStateException.class, () -> cache.aquireOnceOrThrow(() -> {
            calls.incrementAndGet();
            throw cause;
        }));
        IllegalStateException second = assertThrows(IllegalStateException.class, () -> cache.aquireOnceOrThrow(() -> {
            calls.incrementAndGet();
            return "recovered";
        }));

        assertSame(cause, first);
        assertSame(cause, second);
        assertEquals("the failure is stated once and replayed, never recomputed", 1, calls.get());
    }

    @Test
    public void aquireOnceOrThrowRejectsANullValueAndRemembersThat() {
        AtomicCache<String> cache = new AtomicCache<>();
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> cache.aquireOnceOrThrow(() -> {
            calls.incrementAndGet();
            return null;
        }));
        assertThrows(IllegalStateException.class, () -> cache.aquireOnceOrThrow(() -> {
            calls.incrementAndGet();
            return "recovered";
        }));
        assertEquals(1, calls.get());
    }

    @Test
    public void resetClearsAMemoizedFailure() {
        AtomicCache<String> cache = new AtomicCache<>();

        assertThrows(IllegalStateException.class, () -> cache.aquireOnceOrThrow(() -> {
            throw new IllegalStateException("transient");
        }));
        cache.reset();

        assertEquals("recovered", cache.aquireOnceOrThrow(() -> "recovered"));
    }

    @Test
    public void aquireStillSwallowsFailuresForOptionalValues() {
        AtomicCache<String> cache = new AtomicCache<>();

        assertNull(cache.aquire(() -> {
            throw new IllegalStateException("optional value");
        }));
        assertEquals("later", cache.aquire(() -> "later"));
    }
}
