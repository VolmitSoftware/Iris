package art.arcane.iris.integration;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldEditLinkDetectionTest {
    @After
    public void resetDetectionState() {
        WorldEditLink.invalidate();
    }

    @Test
    public void negativeDetectionIsNotMemoized() {
        AtomicInteger calls = new AtomicInteger();
        boolean[] answers = {false, true};

        assertFalse(WorldEditLink.hasWorldEdit(() -> answers[calls.getAndIncrement()]));
        assertTrue("WorldEdit enabling after a negative probe must be picked up without a restart",
                WorldEditLink.hasWorldEdit(() -> answers[calls.getAndIncrement()]));
        assertEquals(2, calls.get());
    }

    @Test
    public void positiveDetectionIsMemoized() {
        AtomicInteger calls = new AtomicInteger();

        assertTrue(WorldEditLink.hasWorldEdit(() -> {
            calls.incrementAndGet();
            return true;
        }));
        assertTrue(WorldEditLink.hasWorldEdit(() -> {
            throw new AssertionError("memoized positive must not re-probe");
        }));
        assertEquals(1, calls.get());
    }

    @Test
    public void selectionFailureDoesNotLatchFalse() {
        assertTrue(WorldEditLink.hasWorldEdit(() -> true));
        WorldEditLink.invalidate();
        assertTrue(WorldEditLink.hasWorldEdit(() -> true));
    }

    @Test
    public void detectorFailureIsNotCachedAndDoesNotThrow() {
        assertFalse(WorldEditLink.hasWorldEdit(() -> {
            throw new IllegalStateException("server not ready");
        }));
        assertTrue(WorldEditLink.hasWorldEdit(() -> true));
    }
}
