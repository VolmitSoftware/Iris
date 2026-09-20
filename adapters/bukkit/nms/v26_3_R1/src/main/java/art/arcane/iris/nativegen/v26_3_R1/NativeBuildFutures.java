package art.arcane.iris.nativegen.v26_3_R1;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Shared waiter for the racy build caches in this package: the loser of a putIfAbsent race
 * must surface the same exception the builder threw, not a CompletionException wrapper.
 */
final class NativeBuildFutures {
    private NativeBuildFutures() {
    }

    static <T> T awaitBuild(CompletableFuture<T> future, String what) {
        try {
            return future.join();
        } catch (CompletionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            if (cause == null) {
                throw error;
            }
            throw new IllegalStateException(what + " failed", cause);
        }
    }
}
