package com.volmit.iris.util.scheduling.split;

import com.volmit.iris.util.scheduling.Task;
import com.volmit.iris.util.scheduling.Task.Completable;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The region task scheduler can be used to schedule tasks by location to be executed on the region which owns the location.
 * <p>
 * <b>Note</b>: It is entirely inappropriate to use the region scheduler to schedule tasks for entities.
 * If you wish to schedule tasks to perform actions on entities, you should be using {@link Entity#getScheduler()}
 * as the entity scheduler will "follow" an entity if it is teleported, whereas the region task scheduler
 * will not.
 * </p>
 */
public interface IRegionScheduler {
    /**
     * Schedules a task to be executed on the region which owns the location on the next tick.
     *
     * @param world  The world of the region that owns the task
     * @param chunkX The chunk X coordinate of the region that owns the task
     * @param chunkZ The chunk Z coordinate of the region that owns the task
     * @param task   The task to execute
     * @return The {@link Task} that represents the scheduled task.
     */
    default @NotNull Task run(@NotNull World world,
                              int chunkX,
                              int chunkZ,
                              @NotNull Consumer<Task> task) {
        return run(world, chunkX, chunkZ, t -> {
            task.accept(t);
            return null;
        });
    }

    /**
     * Schedules a task to be executed on the region which owns the location on the next tick.
     *
     * @param world  The world of the region that owns the task
     * @param chunkX The chunk X coordinate of the region that owns the task
     * @param chunkZ The chunk Z coordinate of the region that owns the task
     * @param task   The task to execute
     * @return The {@link Completable} that represents the scheduled task.
     */
    default @NotNull <R> Completable<R> run(@NotNull World world,
                                            int chunkX,
                                            int chunkZ,
                                            @NotNull Function<Completable<R>, R> task) {
        return runDelayed(world, chunkX, chunkZ, task, 1);
    }

    /**
     * Schedules a task to be executed on the region which owns the location on the next tick.
     *
     * @param location The location at which the region executing should own
     * @param task     The task to execute
     * @return The {@link Task} that represents the scheduled task.
     */
    default @NotNull Task run(@NotNull Location location,
                              @NotNull Consumer<Task> task) {
        return run(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, task);
    }

    /**
     * Schedules a task to be executed on the region which owns the location on the next tick.
     *
     * @param location The location at which the region executing should own
     * @param task     The task to execute
     * @return The {@link Completable} that represents the scheduled task.
     */
    default @NotNull <R> Completable<R> run(@NotNull Location location,
                                            @NotNull Function<Completable<R>, R> task) {
        return run(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, task);
    }

    /**
     * Schedules a task to be executed on the region which owns the location after the specified delay in ticks.
     *
     * @param world      The world of the region that owns the task
     * @param chunkX     The chunk X coordinate of the region that owns the task
     * @param chunkZ     The chunk Z coordinate of the region that owns the task
     * @param task       The task to execute
     * @param delayTicks The delay, in ticks.
     * @return The {@link Task} that represents the scheduled task.
     */
    default @NotNull Task runDelayed(@NotNull World world,
                                     int chunkX,
                                     int chunkZ,
                                     @NotNull Consumer<Task> task,
                                     @Range(from = 1, to = Long.MAX_VALUE) long delayTicks) {
        return runDelayed(world, chunkX, chunkZ, t -> {
            task.accept(t);
            return null;
        }, delayTicks);
    }

    /**
     * Schedules a task to be executed on the region which owns the location after the specified delay in ticks.
     *
     * @param world      The world of the region that owns the task
     * @param chunkX     The chunk X coordinate of the region that owns the task
     * @param chunkZ     The chunk Z coordinate of the region that owns the task
     * @param task       The task to execute
     * @param delayTicks The delay, in ticks.
     * @return The {@link Completable} that represents the scheduled task.
     */
    @NotNull <R> Completable<R> runDelayed(@NotNull World world,
                                           int chunkX,
                                           int chunkZ,
                                           @NotNull Function<Completable<R>, R> task,
                                           @Range(from = 1, to = Long.MAX_VALUE) long delayTicks);

    /**
     * Schedules a task to be executed on the region which owns the location after the specified delay in ticks.
     *
     * @param location   The location at which the region executing should own
     * @param task       The task to execute
     * @param delayTicks The delay, in ticks.
     * @return The {@link Task} that represents the scheduled task.
     */
    default @NotNull Task runDelayed(@NotNull Location location,
                                     @NotNull Consumer<Task> task,
                                     @Range(from = 1, to = Long.MAX_VALUE) long delayTicks) {
        return runDelayed(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, task, delayTicks);
    }
    /**
     * Schedules a task to be executed on the region which owns the location after the specified delay in ticks.
     *
     * @param location   The location at which the region executing should own
     * @param task       The task to execute
     * @param delayTicks The delay, in ticks.
     * @return The {@link Completable} that represents the scheduled task.
     */
    default @NotNull <R> Completable<R> runDelayed(@NotNull Location location,
                                                   @NotNull Function<Completable<R>, R> task,
                                                   @Range(from = 1, to = Long.MAX_VALUE) long delayTicks) {
        return runDelayed(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, task, delayTicks);
    }

    /**
     * Schedules a repeating task to be executed on the region which owns the location after the initial delay with the
     * specified period.
     *
     * @param world             The world of the region that owns the task
     * @param chunkX            The chunk X coordinate of the region that owns the task
     * @param chunkZ            The chunk Z coordinate of the region that owns the task
     * @param task              The task to execute
     * @param initialDelayTicks The initial delay, in ticks.
     * @param periodTicks       The period, in ticks.
     * @return The {@link Task} that represents the scheduled task.
     */
    @NotNull Task runAtFixedRate(@NotNull World world,
                                 int chunkX,
                                 int chunkZ,
                                 @NotNull Consumer<Task> task,
                                 @Range(from = 1, to = Long.MAX_VALUE) long initialDelayTicks,
                                 @Range(from = 1, to = Long.MAX_VALUE) long periodTicks);

    /**
     * Schedules a repeating task to be executed on the region which owns the location after the initial delay with the
     * specified period.
     *
     * @param location          The location at which the region executing should own
     * @param task              The task to execute
     * @param initialDelayTicks The initial delay, in ticks.
     * @param periodTicks       The period, in ticks.
     * @return The {@link Task} that represents the scheduled task.
     */
    default @NotNull Task runAtFixedRate(@NotNull Location location,
                                         @NotNull Consumer<Task> task,
                                         @Range(from = 1, to = Long.MAX_VALUE) long initialDelayTicks,
                                         @Range(from = 1, to = Long.MAX_VALUE) long periodTicks) {
        return runAtFixedRate(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, task, initialDelayTicks, periodTicks);
    }
}
