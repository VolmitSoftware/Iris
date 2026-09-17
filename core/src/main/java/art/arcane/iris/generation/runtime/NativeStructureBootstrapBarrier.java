package art.arcane.iris.generation.runtime;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

final class NativeStructureBootstrapBarrier {
    private static final long TRANSITION_TIMEOUT_SECONDS = 120L;

    private CompletableFuture<Void> active;
    private CompletableFuture<Void> poisoned;

    synchronized CompletableFuture<Void> start(
            Runnable claim,
            Supplier<CompletableFuture<Void>> starter
    ) {
        Objects.requireNonNull(claim, "Native structure bootstrap claim");
        Objects.requireNonNull(starter, "Native structure bootstrap starter");
        if (active != null) {
            throw new IllegalStateException("Native structure bootstrap is already active.");
        }
        claim.run();
        CompletableFuture<Void> bridge = new CompletableFuture<>();
        active = bridge;
        bridge.whenComplete((ignored, failure) -> clearSuccessful(bridge, failure));
        try {
            CompletableFuture<Void> completion = Objects.requireNonNull(
                    starter.get(),
                    "Native structure bootstrap completion");
            completion.whenComplete((ignored, failure) -> completeBridge(bridge, failure));
            return bridge.copy();
        } catch (Throwable failure) {
            poisoned = bridge;
            bridge.completeExceptionally(failure);
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Native structure bootstrap could not start.", failure);
        }
    }

    void await(String transition) {
        Throwable failure = awaitCompletion(transition);
        if (failure != null) {
            throw new IllegalStateException(
                    "Native structure bootstrap failed before " + transition + ".", failure);
        }
    }

    Throwable awaitForClose() {
        return awaitCompletion("close");
    }

    synchronized boolean isActive() {
        return active != null;
    }

    synchronized boolean isPoisoned() {
        return poisoned != null;
    }

    private Throwable awaitCompletion(String transition) {
        CompletableFuture<Void> completion;
        synchronized (this) {
            completion = active;
        }
        if (completion == null) {
            return null;
        }
        try {
            completion.get(TRANSITION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            clearCompleted(completion);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for native structure bootstrap before " + transition + ".", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            synchronized (this) {
                if (poisoned == completion) {
                    throw new IllegalStateException(
                            "Native structure bootstrap could not be tracked before " + transition + ".", cause);
                }
                clearCompleted(completion);
            }
            return cause;
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    "Native structure bootstrap did not finish before " + transition + " within "
                            + TRANSITION_TIMEOUT_SECONDS + " seconds.", e);
        }
    }

    private synchronized void clearCompleted(CompletableFuture<Void> completion) {
        if (active == completion) {
            active = null;
        }
    }

    private void completeBridge(CompletableFuture<Void> bridge, Throwable failure) {
        if (failure == null) {
            bridge.complete(null);
            return;
        }
        bridge.completeExceptionally(failure);
    }

    private synchronized void clearSuccessful(CompletableFuture<Void> completion, Throwable failure) {
        if (failure == null && active == completion) {
            active = null;
        }
    }
}
