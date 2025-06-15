package com.volmit.iris.util.scheduling.spigot;

import com.volmit.iris.util.scheduling.Task;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class SpigotTask implements Task {
    protected final BukkitTask task;

    public SpigotTask(@NotNull BukkitTask task) {
        this.task = task;
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
        return task.getOwner();
    }

    @Override
    public boolean async() {
        return !task.isSync();
    }

    @SuppressWarnings("deprecation")
    public static class Completable<T> extends SpigotTask implements Task.Completable<T> {
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private final BukkitScheduler scheduler;

        public Completable(@NotNull BukkitTask task, @NotNull BukkitScheduler scheduler) {
            super(task);
            this.scheduler = scheduler;
        }

        @Override
        public void cancel() {
            scheduler.cancelTask(task.getTaskId());
            if (scheduler.isCurrentlyRunning(task.getTaskId())) return;
            result.cancel(false);
        }

        @Override
        public @NotNull CompletableFuture<T> result() {
            return result;
        }
    }
}
