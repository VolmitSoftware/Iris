package com.volmit.iris.util.scheduling.split;

import com.volmit.iris.util.scheduling.Task;
import com.volmit.iris.util.scheduling.Task.Completable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Scheduler that may be used by plugins to schedule tasks to execute asynchronously from the server tick process.
 */
public interface IAsyncScheduler {
    /**
     * Schedules the specified task to be executed asynchronously immediately.
     * @param task Specified task.
     * @return The {@link Task} that represents the scheduled task.
     */
    default @NotNull Task run(@NotNull Consumer<Task> task) {
        return run(t -> {
            task.accept(t);
            return null;
        });
    }
    
    /**
     * Schedules the specified task to be executed asynchronously immediately.
     * @param task Specified task.
     * @return The {@link Completable} that represents the scheduled task.
     */
    @NotNull <R> Completable<R> run(@NotNull Function<Completable<R>, R> task);

    /**
     * Schedules the specified task to be executed asynchronously after the time delay has passed.
     * @param task Specified task.
     * @param delay The time delay to pass before the task should be executed.
     * @param unit The time unit for the time delay.
     * @return The {@link Task} that represents the scheduled task.
     */
    default @NotNull Task runDelayed(@NotNull Consumer<Task> task,
                                     @Range(from = 0, to = Long.MAX_VALUE) long delay,
                                     @NotNull TimeUnit unit) {
        return runDelayed(t -> {
            task.accept(t);
            return null;
        }, delay, unit);
    }

    /**
     * Schedules the specified task to be executed asynchronously after the time delay has passed.
     * @param task Specified task.
     * @param delay The time delay to pass before the task should be executed.
     * @param unit The time unit for the time delay.
     * @return The {@link Completable} that represents the scheduled task.
     */
    @NotNull <R> Completable<R> runDelayed(@NotNull Function<Completable<R>, R> task,
                                           @Range(from = 0, to = Long.MAX_VALUE) long delay,
                                           @NotNull TimeUnit unit);

    /**
     * Schedules the specified task to be executed asynchronously after the initial delay has passed,
     * and then periodically executed with the specified period.
     * @param task Specified task.
     * @param initialDelay The time delay to pass before the first execution of the task.
     * @param period The time between task executions after the first execution of the task.
     * @param unit The time unit for the initial delay and period.
     * @return The {@link Task} that represents the scheduled task.
     */
    @NotNull Task runAtFixedRate(@NotNull Consumer<Task> task,
                                 @Range(from = 0, to = Long.MAX_VALUE) long initialDelay,
                                 @Range(from = 0, to = Long.MAX_VALUE) long period,
                                 @NotNull TimeUnit unit);
}
