package art.arcane.iris.generation.runtime;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeStructureBootstrapBarrierTest {
    @Test
    public void immediateCloseWaitsForExactRingCompletion() throws Exception {
        NativeStructureBootstrapBarrier barrier = new NativeStructureBootstrapBarrier();
        CompletableFuture<Void> rings = new CompletableFuture<>();
        barrier.start(() -> {
        }, () -> rings);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> close = executor.submit(() -> barrier.await("close"));
            assertThrows(TimeoutException.class, () -> close.get(50L, TimeUnit.MILLISECONDS));
            rings.complete(null);
            close.get(1L, TimeUnit.SECONDS);
            assertFalse(barrier.isActive());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void immediateHotloadWaitsForExactRingCompletion() throws Exception {
        NativeStructureBootstrapBarrier barrier = new NativeStructureBootstrapBarrier();
        CompletableFuture<Void> rings = new CompletableFuture<>();
        barrier.start(() -> {
        }, () -> rings);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> hotload = executor.submit(() -> barrier.await("hotload"));
            assertThrows(TimeoutException.class, () -> hotload.get(50L, TimeUnit.MILLISECONDS));
            rings.complete(null);
            hotload.get(1L, TimeUnit.SECONDS);
            assertFalse(barrier.isActive());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void asynchronousRingFailureFailsTransitionOnceBeforeAllowingRetry() {
        NativeStructureBootstrapBarrier barrier = new NativeStructureBootstrapBarrier();
        CompletableFuture<Void> rings = new CompletableFuture<>();
        barrier.start(() -> {
        }, () -> rings);
        rings.completeExceptionally(new IllegalStateException("ring failure"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> barrier.await("complex hotload"));

        assertTrue(failure.getMessage().contains("before complex hotload"));
        assertTrue(failure.getCause() instanceof IllegalStateException);
        assertFalse(barrier.isActive());
        barrier.await("complex hotload retry");
    }

    @Test
    public void synchronousStarterFailurePermanentlyPoisonsLifecycleTransitions() {
        NativeStructureBootstrapBarrier barrier = new NativeStructureBootstrapBarrier();
        IllegalStateException starterFailure = new IllegalStateException("partial scheduling");

        assertThrows(IllegalStateException.class, () -> barrier.start(
                () -> {
                },
                () -> {
                    throw starterFailure;
                }));

        assertTrue(barrier.isActive());
        assertTrue(barrier.isPoisoned());
        IllegalStateException first = assertThrows(
                IllegalStateException.class,
                () -> barrier.await("close"));
        IllegalStateException second = assertThrows(
                IllegalStateException.class,
                () -> barrier.await("close retry"));
        assertTrue(first.getCause() == starterFailure);
        assertTrue(second.getCause() == starterFailure);
        assertThrows(IllegalStateException.class, () -> barrier.start(
                () -> {
                },
                () -> CompletableFuture.completedFuture(null)));
    }

    @Test
    public void failedClaimDoesNotPoisonAnUnstartedBootstrap() {
        NativeStructureBootstrapBarrier barrier = new NativeStructureBootstrapBarrier();

        assertThrows(IllegalStateException.class, () -> barrier.start(
                () -> {
                    throw new IllegalStateException("cancelled");
                },
                () -> CompletableFuture.completedFuture(null)));

        assertFalse(barrier.isActive());
        assertFalse(barrier.isPoisoned());
        barrier.start(() -> {
        }, () -> CompletableFuture.completedFuture(null));
        assertFalse(barrier.isActive());
    }

    @Test
    public void secondRingBootstrapCannotReplaceAnUnfinishedOne() {
        NativeStructureBootstrapBarrier barrier = new NativeStructureBootstrapBarrier();
        CompletableFuture<Void> first = new CompletableFuture<>();
        barrier.start(() -> {
        }, () -> first);

        assertThrows(IllegalStateException.class,
                () -> barrier.start(
                        () -> {
                        },
                        () -> CompletableFuture.completedFuture(null)));
        assertTrue(barrier.isActive());
        first.complete(null);
        assertFalse(barrier.isActive());
    }
}
