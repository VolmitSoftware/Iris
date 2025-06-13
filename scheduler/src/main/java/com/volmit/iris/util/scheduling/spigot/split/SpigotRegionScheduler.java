package com.volmit.iris.util.scheduling.spigot.split;

import com.volmit.iris.util.scheduling.Task;
import com.volmit.iris.util.scheduling.Task.Completable;
import com.volmit.iris.util.scheduling.split.IGlobalScheduler;
import com.volmit.iris.util.scheduling.split.IRegionScheduler;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.function.Consumer;
import java.util.function.Function;

public class SpigotRegionScheduler implements IRegionScheduler {
    private final IGlobalScheduler scheduler;

    public SpigotRegionScheduler(IGlobalScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public @NotNull <R> Completable<R> runDelayed(@NotNull World world, int chunkX, int chunkZ, @NotNull Function<Completable<R>, R> task, @Range(from = 1, to = Long.MAX_VALUE) long delayTicks) {
        return scheduler.runDelayed(task, delayTicks);
    }

    @Override
    public @NotNull Task runAtFixedRate(@NotNull World world, int chunkX, int chunkZ, @NotNull Consumer<Task> task, @Range(from = 1, to = Long.MAX_VALUE) long initialDelayTicks, @Range(from = 1, to = Long.MAX_VALUE) long periodTicks) {
        return scheduler.runAtFixedRate(task, initialDelayTicks, periodTicks);
    }
}
