package com.volmit.iris.util.scheduling.paper.split;

import com.volmit.iris.util.scheduling.Ref;
import com.volmit.iris.util.scheduling.Task;
import com.volmit.iris.util.scheduling.Task.Completable;
import com.volmit.iris.util.scheduling.paper.PaperTask;
import com.volmit.iris.util.scheduling.split.IEntityScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.function.Consumer;
import java.util.function.Function;

public class PaperEntityScheduler implements IEntityScheduler {
    private final Plugin plugin;
    private final EntityScheduler scheduler;

    public PaperEntityScheduler(Plugin plugin, EntityScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public @Nullable <R> Completable<R> runDelayed(@NotNull Function<Completable<R>, R> task,
                                                   @Nullable Runnable retired,
                                                   @Range(from = 1, to = Long.MAX_VALUE) long delayTicks) {
        Ref<Completable<R>> ref = new Ref<>();
        var raw = scheduler.runDelayed(plugin, t -> ref.value.complete(task), () -> {
            if (retired != null) retired.run();
            ref.value.cancel();
        }, delayTicks);
        if (raw == null) return null;
        return ref.value = new PaperTask.Completable<>(raw, false);
    }

    @Override
    public @Nullable Task runAtFixedRate(@NotNull Consumer<Task> task,
                                         @Nullable Runnable retired,
                                         @Range(from = 1, to = Long.MAX_VALUE) long initialDelayTicks,
                                         @Range(from = 1, to = Long.MAX_VALUE) long periodTicks) {
        Ref<Task> ref = new Ref<>();
        var raw = scheduler.runAtFixedRate(plugin, t -> task.accept(ref.value), retired, initialDelayTicks, periodTicks);
        if (raw == null) return null;
        return ref.value = new PaperTask(raw, false);
    }
}
