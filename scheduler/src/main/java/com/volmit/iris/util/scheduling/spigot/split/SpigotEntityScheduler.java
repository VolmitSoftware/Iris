package com.volmit.iris.util.scheduling.spigot.split;

import com.volmit.iris.util.scheduling.Task;
import com.volmit.iris.util.scheduling.Task.Completable;
import com.volmit.iris.util.scheduling.split.IEntityScheduler;
import com.volmit.iris.util.scheduling.split.IGlobalScheduler;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.function.Consumer;
import java.util.function.Function;

import static com.volmit.iris.util.scheduling.spigot.SpigotPlatform.isValid;

public class SpigotEntityScheduler implements IEntityScheduler {
    private final IGlobalScheduler scheduler;
    private final Entity entity;

    public SpigotEntityScheduler(IGlobalScheduler scheduler, Entity entity) {
        this.scheduler = scheduler;
        this.entity = entity;
    }

    @Override
    public @Nullable <R> Completable<R> runDelayed(@NotNull Function<Completable<R>, R> task, @Nullable Runnable retired, @Range(from = 1, to = Long.MAX_VALUE) long delayTicks) {
        if (!isValid(entity)) return null;
        return scheduler.runDelayed(t -> {
            if (!isValid(entity)) {
                t.cancel();
                if (retired != null)
                    retired.run();
                return null;
            }
            return task.apply(t);
        }, delayTicks);
    }

    @Override
    public @Nullable Task runAtFixedRate(@NotNull Consumer<Task> task, @Nullable Runnable retired, @Range(from = 1, to = Long.MAX_VALUE) long initialDelayTicks, @Range(from = 1, to = Long.MAX_VALUE) long periodTicks) {
        if (!isValid(entity)) return null;
        return scheduler.runAtFixedRate(t -> {
            if (!isValid(entity)) {
                t.cancel();
                if (retired != null)
                    retired.run();
                return;
            }
            task.accept(t);
        }, initialDelayTicks, periodTicks);
    }
}
