package com.volmit.iris.util.scheduling.split;

import com.volmit.iris.util.scheduling.Task;
import com.volmit.iris.util.scheduling.Task.Completable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The global region task scheduler may be used to schedule tasks that will execute on the global region.
 * <p>
 * The global region is responsible for maintaining world day time, world game time, weather cycle,
 * sleep night skipping, executing commands for console, and other misc. tasks that do not belong to any specific region.
 * </p>
 */
public interface IGlobalScheduler {
    /**
     * Schedules a task to be executed on the global region on the next tick.
     * @param task The task to execute
     * @return The {@link Task} that represents the scheduled task.
     */
    default @NotNull Task run(@NotNull Consumer<Task> task) {
        return run(t -> {
            task.accept(t);
            return null;
        });
    }

    /**
     * Schedules a task to be executed on the global region on the next tick.
     * @param task The task to execute
     * @return The {@link Completable} that represents the scheduled task.
     */
    default @NotNull <R> Completable<R> run(@NotNull Function<Completable<R>, R> task) {
        return runDelayed(task, 1);
    }

    /**
     * Schedules a task to be executed on the global region after the specified delay in ticks.
     * @param task The task to execute
     * @param delayTicks The delay, in ticks.
     * @return The {@link Task} that represents the scheduled task.
     */
    default @NotNull Task runDelayed(@NotNull Consumer<Task> task,
                                     @Range(from = 1, to = Long.MAX_VALUE) long delayTicks) {
        return runDelayed(t -> {
            task.accept(t);
            return null;
        }, delayTicks);
    }

    /**
     * Schedules a task to be executed on the global region after the specified delay in ticks.
     * @param task The task to execute
     * @param delayTicks The delay, in ticks.
     * @return The {@link Completable} that represents the scheduled task.
     */
    @NotNull <R> Completable<R> runDelayed(@NotNull Function<Completable<R>, R> task,
                                           @Range(from = 1, to = Long.MAX_VALUE) long delayTicks);

    /**
     * Schedules a repeating task to be executed on the global region after the initial delay with the
     * specified period.
     * @param task The task to execute
     * @param initialDelayTicks The initial delay, in ticks.
     * @param periodTicks The period, in ticks.
     * @return The {@link Task} that represents the scheduled task.
     */
    @NotNull Task runAtFixedRate(@NotNull Consumer<Task> task,
                                 @Range(from = 1, to = Long.MAX_VALUE) long initialDelayTicks,
                                 @Range(from = 1, to = Long.MAX_VALUE) long periodTicks);
}
