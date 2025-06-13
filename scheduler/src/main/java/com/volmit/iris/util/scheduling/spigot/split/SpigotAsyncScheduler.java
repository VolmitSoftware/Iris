package com.volmit.iris.util.scheduling.spigot.split;

import com.volmit.iris.util.scheduling.Ref;
import com.volmit.iris.util.scheduling.Task;
import com.volmit.iris.util.scheduling.Task.Completable;
import com.volmit.iris.util.scheduling.spigot.SpigotTask;
import com.volmit.iris.util.scheduling.split.IAsyncScheduler;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("deprecation")
public class SpigotAsyncScheduler implements IAsyncScheduler {
    private final Plugin plugin;
    private final BukkitScheduler scheduler;

    public SpigotAsyncScheduler(Plugin plugin, BukkitScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public @NotNull <R> Completable<R> run(@NotNull Function<Completable<R>, R> task) {
        Ref<Completable<R>> ref = new Ref<>();
        return ref.value = new SpigotTask.Completable<>(scheduler.runTaskAsynchronously(plugin, () -> ref.value.complete(task)), scheduler);
    }

    @Override
    public @NotNull <R> Completable<R> runDelayed(@NotNull Function<Completable<R>, R> task, @Range(from = 0, to = Long.MAX_VALUE) long delay, @NotNull TimeUnit unit) {
        Ref<Completable<R>> ref = new Ref<>();
        return ref.value = new SpigotTask.Completable<>(scheduler.runTaskLaterAsynchronously(plugin, () -> ref.value.complete(task), unit.toMillis(delay) / 50), scheduler);
    }

    @Override
    public @NotNull Task runAtFixedRate(@NotNull Consumer<Task> task, @Range(from = 0, to = Long.MAX_VALUE) long initialDelay, @Range(from = 0, to = Long.MAX_VALUE) long period, @NotNull TimeUnit unit) {
        Ref<Task> ref = new Ref<>();
        return ref.value = new SpigotTask(scheduler.runTaskTimerAsynchronously(plugin, () -> task.accept(ref.value), unit.toMillis(initialDelay) / 50, unit.toMillis(period) / 50));
    }
}
