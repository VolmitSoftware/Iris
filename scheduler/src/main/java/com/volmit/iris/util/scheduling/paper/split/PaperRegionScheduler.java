package com.volmit.iris.util.scheduling.paper.split;

import com.volmit.iris.util.scheduling.Ref;
import com.volmit.iris.util.scheduling.Task;
import com.volmit.iris.util.scheduling.Task.Completable;
import com.volmit.iris.util.scheduling.paper.PaperTask;
import com.volmit.iris.util.scheduling.split.IRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.function.Consumer;
import java.util.function.Function;

public class PaperRegionScheduler implements IRegionScheduler {
    private final Plugin plugin;
    private final RegionScheduler scheduler;

    public PaperRegionScheduler(Plugin plugin, RegionScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public @NotNull <R> Completable<R> runDelayed(@NotNull World world,
                                                  int chunkX,
                                                  int chunkZ,
                                                  @NotNull Function<Completable<R>, R> task, @Range(from = 1, to = Long.MAX_VALUE) long delayTicks) {
        Ref<Completable<R>> ref = new Ref<>();
        return ref.value = new PaperTask.Completable<>(scheduler.runDelayed(plugin, world, chunkX, chunkZ, t -> ref.value.complete(task), delayTicks), false);
    }

    @Override
    public @NotNull Task runAtFixedRate(@NotNull World world,
                                        int chunkX,
                                        int chunkZ,
                                        @NotNull Consumer<Task> task,
                                        @Range(from = 1, to = Long.MAX_VALUE) long initialDelayTicks,
                                        @Range(from = 1, to = Long.MAX_VALUE) long periodTicks) {
        Ref<Task> ref = new Ref<>();
        return ref.value = new PaperTask(scheduler.runAtFixedRate(plugin, world, chunkX, chunkZ, t -> task.accept(ref.value), initialDelayTicks, periodTicks), false);
    }
}
