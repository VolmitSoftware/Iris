package com.volmit.iris.util.scheduling.paper.split;

import com.volmit.iris.util.scheduling.Ref;
import com.volmit.iris.util.scheduling.Task;
import com.volmit.iris.util.scheduling.Task.Completable;
import com.volmit.iris.util.scheduling.paper.PaperTask;
import com.volmit.iris.util.scheduling.split.IGlobalScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.function.Consumer;
import java.util.function.Function;

public class PaperGlobalScheduler implements IGlobalScheduler {
    private final Plugin plugin;
    private final GlobalRegionScheduler scheduler;

    public PaperGlobalScheduler(Plugin plugin, GlobalRegionScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public @NotNull <R> Completable<R> runDelayed(@NotNull Function<Completable<R>, R> task,
                                                  @Range(from = 1, to = Long.MAX_VALUE) long delayTicks) {
        Ref<Completable<R>> ref = new Ref<>();
        return ref.value = new PaperTask.Completable<>(scheduler.runDelayed(plugin, t -> ref.value.complete(task), delayTicks), false);
    }

    @Override
    public @NotNull Task runAtFixedRate(@NotNull Consumer<Task> task,
                                        @Range(from = 1, to = Long.MAX_VALUE) long initialDelayTicks,
                                        @Range(from = 1, to = Long.MAX_VALUE) long periodTicks) {
        Ref<Task> ref = new Ref<>();
        return ref.value = new PaperTask(scheduler.runAtFixedRate(plugin, t -> task.accept(ref.value), initialDelayTicks, periodTicks), false);
    }
}
