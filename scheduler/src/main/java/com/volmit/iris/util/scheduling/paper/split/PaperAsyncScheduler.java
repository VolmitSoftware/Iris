package com.volmit.iris.util.scheduling.paper.split;

import com.volmit.iris.util.scheduling.Ref;
import com.volmit.iris.util.scheduling.Task;
import com.volmit.iris.util.scheduling.Task.Completable;
import com.volmit.iris.util.scheduling.paper.PaperTask;
import com.volmit.iris.util.scheduling.split.IAsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class PaperAsyncScheduler implements IAsyncScheduler {
    private final Plugin plugin;
    private final AsyncScheduler scheduler;

    public PaperAsyncScheduler(Plugin plugin, AsyncScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public @NotNull <R> Completable<R> run(@NotNull Function<Completable<R>, R> task) {
        Ref<Completable<R>> ref = new Ref<>();
        return ref.value = new PaperTask.Completable<>(scheduler.runNow(plugin, t -> ref.value.complete(task)), true);
    }

    @Override
    public @NotNull <R> Completable<R> runDelayed(@NotNull Function<Completable<R>, R> task,
                                                  @Range(from = 0, to = Long.MAX_VALUE) long delay,
                                                  @NotNull TimeUnit unit) {
        Ref<Completable<R>> ref = new Ref<>();
        return ref.value = new PaperTask.Completable<>(scheduler.runDelayed(plugin, t -> ref.value.complete(task), delay, unit), true);
    }

    @Override
    public @NotNull Task runAtFixedRate(@NotNull Consumer<Task> task,
                                        @Range(from = 0, to = Long.MAX_VALUE) long initialDelay,
                                        @Range(from = 1, to = Long.MAX_VALUE) long period,
                                        @NotNull TimeUnit unit) {
        Ref<Task> ref = new Ref<>();
        return ref.value = new PaperTask(scheduler.runAtFixedRate(plugin, t -> task.accept(ref.value), initialDelay, period, unit), true);
    }
}
