package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.volmlib.util.mantle.MantleClosedException;
import org.junit.Test;

import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GenerationFailuresTest {
    @Test
    public void engineAndLifecycleFailuresAreRecognizedThroughWrappers() {
        assertTrue(GenerationFailures.isEngineFailure(new GenerationClosedException("router closed")));
        assertTrue(GenerationFailures.isEngineFailure(new MantleClosedException("Tectonic Plate is closed!")));
        assertTrue(GenerationFailures.isEngineFailure(new RejectedExecutionException("pool shut down")));
        assertTrue(GenerationFailures.isEngineFailure(new ClosedByInterruptException()));
        assertTrue(GenerationFailures.isEngineFailure(new SavedBiomeUnavailableException("loading", true)));
        assertTrue(GenerationFailures.isEngineFailure(new GenerationSessionException("sealed", true)));
        assertTrue(GenerationFailures.isEngineFailure(new OutOfMemoryError()));
        assertTrue(GenerationFailures.isEngineFailure(new CompletionException(
                new IllegalStateException("placement failed", new GenerationClosedException("router closed")))));
    }

    @Test
    public void contentFailuresRemainSkippable() {
        assertFalse(GenerationFailures.isEngineFailure(new IllegalArgumentException("malformed object")));
        assertFalse(GenerationFailures.isEngineFailure(new NullPointerException("missing block")));
        assertFalse(GenerationFailures.isEngineFailure(new IllegalStateException("unrelated")));
    }

    @Test
    public void anyFailureOnAnInterruptedThreadIsAnEngineFailure() {
        Thread.currentThread().interrupt();
        try {
            assertTrue(GenerationFailures.isEngineFailure(new IllegalArgumentException("malformed object")));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void rethrowPropagatesEngineFailuresAndReturnsForContentFailures() {
        GenerationClosedException closed = new GenerationClosedException("router closed");
        try {
            GenerationFailures.rethrowEngineFailure(closed);
            fail("engine failure was not rethrown");
        } catch (GenerationClosedException rethrown) {
            assertSame(closed, rethrown);
        }

        GenerationSessionException sealed = new GenerationSessionException("sealed", true);
        try {
            GenerationFailures.rethrowEngineFailure(sealed);
            fail("checked engine failure was not rethrown");
        } catch (IllegalStateException wrapped) {
            assertSame(sealed, wrapped.getCause());
        }

        GenerationFailures.rethrowEngineFailure(new IllegalArgumentException("malformed object"));
    }
}
