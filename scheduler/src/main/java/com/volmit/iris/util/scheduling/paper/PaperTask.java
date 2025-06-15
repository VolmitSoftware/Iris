package com.volmit.iris.util.scheduling.paper;

import com.volmit.iris.util.scheduling.Task;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class PaperTask implements Task {
    protected final ScheduledTask task;
    protected final boolean async;

    public PaperTask(@NotNull ScheduledTask task, boolean async) {
        this.task = task;
        this.async = async;
    }

    @Override
    public void cancel() {
        task.cancel();
    }

    @Override
    public boolean cancelled() {
        return task.isCancelled();
    }

    @Override
    public @NotNull Plugin owner() {
        return task.getOwningPlugin();
    }

    @Override
    public boolean async() {
        return async;
    }

    public static class Completable<T> extends PaperTask implements Task.Completable<T> {
        private final CompletableFuture<T> result = new CompletableFuture<>();

        public Completable(@NotNull ScheduledTask task, boolean async) {
            super(task, async);
        }

        @Override
        public void cancel() {
            ScheduledTask.CancelledState cancel = task.cancel();
            if (cancel == ScheduledTask.CancelledState.CANCELLED_BY_CALLER || cancel == ScheduledTask.CancelledState.NEXT_RUNS_CANCELLED || cancel == ScheduledTask.CancelledState.CANCELLED_ALREADY) {
                result.cancel(false);
            }
        }

        @Override
        public @NotNull CompletableFuture<T> result() {
            return result;
        }
    }
}
