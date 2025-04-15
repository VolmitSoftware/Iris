package com.volmit.iris.util.parallel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class Sync<T> {
    private final AtomicReference<CompletableFuture<T>> tick = new AtomicReference<>(new CompletableFuture<>());

    public void cancel(boolean mayInterruptIfRunning) {
        tick.getAndSet(new CompletableFuture<>()).cancel(mayInterruptIfRunning);
    }

    public CompletableFuture<T> next() {
        return tick.get();
    }

    public void advance(T value) {
        tick.getAndSet(new CompletableFuture<>()).complete(value);
    }
}
