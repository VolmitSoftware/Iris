package com.volmit.generation;

import com.volmit.ModuleLog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenerationFailuresTest {
    private static final long START_NANOS = 1_000_000_000L;

    private static final class ClosedSession extends Exception implements EngineFailure {
        ClosedSession(String message) {
            super(message);
        }
    }

    private static ModuleLog logInto(List<String> warnings) {
        return (ModuleLog) Proxy.newProxyInstance(ModuleLog.class.getClassLoader(), new Class<?>[] {ModuleLog.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("warn")) {
                        warnings.add((String) arguments[0]);
                    }
                    return null;
                });
    }

    @Test
    void engineFailuresAreRecognizedThroughWrappers() {
        assertTrue(GenerationFailures.isEngineFailure(new ClosedSession("sealed")));
        assertTrue(GenerationFailures.isEngineFailure(new RejectedExecutionException("pool shut down")));
        assertTrue(GenerationFailures.isEngineFailure(new OutOfMemoryError()));
        assertTrue(GenerationFailures.isEngineFailure(new UncheckedIOException(new IOException("plate read"))));
        assertTrue(GenerationFailures.isEngineFailure(new CompletionException(
                new IllegalStateException("placement failed", new ClosedSession("sealed")))));
    }

    @Test
    void contentFailuresAreNotEngineFailures() {
        assertFalse(GenerationFailures.isEngineFailure(new IllegalArgumentException("malformed object")));
        assertFalse(GenerationFailures.isEngineFailure(new NullPointerException("missing block")));
        assertFalse(GenerationFailures.isEngineFailure(new IllegalStateException("unrelated")));
    }

    @Test
    void anyFailureOnAnInterruptedThreadIsAnEngineFailure() {
        Thread.currentThread().interrupt();
        try {
            assertTrue(GenerationFailures.isEngineFailure(new IllegalArgumentException("malformed object")));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rethrowPropagatesEngineFailuresAndReturnsForContentFailures() {
        var closed = new IllegalStateException("closed", new RejectedExecutionException());
        assertSame(closed, assertThrows(IllegalStateException.class, () -> GenerationFailures.rethrowEngineFailure(closed)));

        var checked = new ClosedSession("sealed");
        var wrapped = assertThrows(IllegalStateException.class, () -> GenerationFailures.rethrowEngineFailure(checked));
        assertSame(checked, wrapped.getCause());

        var error = new OutOfMemoryError();
        assertSame(error, assertThrows(OutOfMemoryError.class, () -> GenerationFailures.rethrowEngineFailure(error)));

        GenerationFailures.rethrowEngineFailure(new IllegalArgumentException("malformed object"));
    }

    @Test
    void skippedPlacementRethrowsEngineFailuresAndWarnsOnContentFailures() {
        var warnings = new ArrayList<String>();
        var log = logInto(warnings);
        GenerationFailures.skipPlacement(log, new IllegalArgumentException("Invalid\nblockFace"),
                "decoration", "minecraft:vine", "at 1,2,3");
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).startsWith(
                "Skipped decoration minecraft:vine at 1,2,3: IllegalArgumentException: Invalid blockFace ("), warnings.get(0));
        assertTrue(warnings.get(0).contains("GenerationFailuresTest."), warnings.get(0));

        var closed = new ClosedSession("sealed");
        var thrown = assertThrows(IllegalStateException.class,
                () -> GenerationFailures.skipPlacement(log, closed, "decoration", "minecraft:vine", "at 1,2,3"));
        assertSame(closed, thrown.getCause());
        assertEquals(1, warnings.size());
    }

    @Test
    void repeatedSkipsAreLimitedPerKeyAndCountedOnTheNextLine() {
        assertEquals(0L, GenerationFailures.claimSkipWarning("limit-test", START_NANOS));
        assertEquals(-1L, GenerationFailures.claimSkipWarning("limit-test", START_NANOS + 1L));
        assertEquals(-1L, GenerationFailures.claimSkipWarning("limit-test", START_NANOS + 2L));
        assertEquals(0L, GenerationFailures.claimSkipWarning("limit-test-other", START_NANOS + 2L));
        assertEquals(2L, GenerationFailures.claimSkipWarning("limit-test",
                START_NANOS + GenerationFailures.SKIP_WARNING_INTERVAL_NANOS + 1L));
    }

    @Test
    void theSkipWarningNamesTheFirstIrisFrameAndTheSuppressedCount() {
        var failure = new IllegalArgumentException("bad");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("java.util.HashMap", "get", "HashMap.java", 1),
                new StackTraceElement("art.arcane.iris.engine.decorator.DecoratorCore", "place", "DecoratorCore.java", 2)});
        assertEquals("Skipped decoration minecraft:vine at 1,2,3: IllegalArgumentException: bad (DecoratorCore.place), 4 more",
                GenerationFailures.skipWarning("decoration", "minecraft:vine", "at 1,2,3", failure, 4L));
    }
}
