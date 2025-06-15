package com.volmit.iris.util.scheduling.spigot.split;

import com.volmit.iris.util.scheduling.Ref;
import com.volmit.iris.util.scheduling.Task;
import com.volmit.iris.util.scheduling.Task.Completable;
import com.volmit.iris.util.scheduling.spigot.SpigotTask;
import com.volmit.iris.util.scheduling.split.IGlobalScheduler;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("deprecation")
public class SpigotGlobalScheduler implements IGlobalScheduler {
    private final Plugin plugin;
    private final BukkitScheduler scheduler;

    public SpigotGlobalScheduler(Plugin plugin, BukkitScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public @NotNull <R> Completable<R> runDelayed(@NotNull Function<Completable<R>, R> task, @Range(from = 1, to = Long.MAX_VALUE) long delayTicks) {
        Ref<Completable<R>> ref = new Ref<>();
        return ref.value = new SpigotTask.Completable<>(scheduler.runTaskLater(plugin, () -> ref.value.complete(task), delayTicks), scheduler);
    }

    @Override
    public @NotNull Task runAtFixedRate(@NotNull Consumer<Task> task, @Range(from = 1, to = Long.MAX_VALUE) long initialDelayTicks, @Range(from = 1, to = Long.MAX_VALUE) long periodTicks) {
        Ref<Task> ref = new Ref<>();
        return ref.value = new SpigotTask(scheduler.runTaskTimer(plugin, () -> task.accept(ref.value), initialDelayTicks, periodTicks));
    }
}
