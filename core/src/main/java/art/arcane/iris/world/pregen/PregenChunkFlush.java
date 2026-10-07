package art.arcane.iris.world.pregen;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class PregenChunkFlush {
    private final Executor executor;
    private final Runnable flush;
    private CompletableFuture<Void> pending;
    private boolean scheduled;

    PregenChunkFlush(Executor executor, Runnable flush) {
        this.executor = Objects.requireNonNull(executor);
        this.flush = Objects.requireNonNull(flush);
    }

    CompletableFuture<Void> request() {
        CompletableFuture<Void> requested;
        boolean submit;
        synchronized (this) {
            if (pending == null) {
                pending = new CompletableFuture<>();
            }
            requested = pending;
            submit = !scheduled;
            scheduled = true;
        }
        if (submit) {
            try {
                executor.execute(this::drain);
            } catch (RuntimeException | Error failure) {
                synchronized (this) {
                    pending = null;
                    scheduled = false;
                }
                requested.completeExceptionally(failure);
            }
        }
        return requested.copy();
    }

    private void drain() {
        while (true) {
            CompletableFuture<Void> batch;
            synchronized (this) {
                batch = pending;
                if (batch == null) {
                    scheduled = false;
                    return;
                }
                pending = null;
            }
            try {
                flush.run();
                batch.complete(null);
            } catch (Throwable failure) {
                batch.completeExceptionally(failure);
            }
        }
    }
}
