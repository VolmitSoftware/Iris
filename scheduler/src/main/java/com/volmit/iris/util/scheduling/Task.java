package com.volmit.iris.util.scheduling;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface Task {
    void cancel();
    boolean cancelled();
    @NotNull Plugin owner();
    boolean async();

    interface Completable<T> extends Task {
        @NotNull CompletableFuture<T> result();

        default void complete(Function<Completable<T>, T> function) {
            var future = result();
            try {
                future.complete(function.apply(this));
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        }
    }
}
