package com.volmit.iris.util.scheduling;

import com.volmit.iris.util.scheduling.paper.PaperPlatform;
import com.volmit.iris.util.scheduling.spigot.SpigotPlatform;
import com.volmit.iris.util.scheduling.split.IAsyncScheduler;
import com.volmit.iris.util.scheduling.split.IEntityScheduler;
import com.volmit.iris.util.scheduling.split.IGlobalScheduler;
import com.volmit.iris.util.scheduling.split.IRegionScheduler;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
public interface Platform {
    /**
     * Folia: Returns whether the current thread is ticking a region and that
     * the region being ticked owns the chunk at the specified world and block
     * position as included in the specified location.
     * Paper/Spigot: Returns {@link Server#isPrimaryThread()}
     *
     * @param location Specified location, must have a non-null world
     * @return true if the current thread is ticking the region that owns the chunk at the specified location
     */
    boolean isOwnedByCurrentRegion(@NotNull Location location);

    /**
     * Folia: Returns whether the current thread is ticking a region and that
     * the region being ticked owns the chunks centered at the specified world
     * and block position as included in the specified location within the
     * specified square radius. Specifically, this function checks that every
     * chunk with position x in [centerX - radius, centerX + radius] and
     * position z in [centerZ - radius, centerZ + radius] is owned by the
     * current ticking region.
     * Paper/Spigot: Returns {@link Server#isPrimaryThread()}
     *
     * @param location Specified location, must have a non-null world
     * @param squareRadiusChunks Specified square radius. Must be >= 0. Note that this parameter is not a squared radius, but rather a Chebyshev Distance
     * @return true if the current thread is ticking the region that owns the chunks centered at the specified location within the specified square radius
     */
    boolean isOwnedByCurrentRegion(@NotNull Location location, int squareRadiusChunks);

    /**
     * Folia: Returns whether the current thread is ticking a region and that
     * the region being ticked owns the chunk at the specified block position.
     * Paper/Spigot: Returns {@link Server#isPrimaryThread()}
     *
     * @param block Specified block position
     * @return true if the current thread is ticking the region that owns the chunk at the specified block position
     */
    boolean isOwnedByCurrentRegion(@NotNull Block block);

    /**
     * Folia: Returns whether the current thread is ticking a region and that
     * the region being ticked owns the chunk at the specified world and chunk
     * position.
     * Paper/Spigot: Returns {@link Server#isPrimaryThread()}
     *
     * @param world Specified world
     * @param chunkX Specified x-coordinate of the chunk position
     * @param chunkZ Specified z-coordinate of the chunk position
     * @return true if the current thread is ticking the region that owns the chunk at the specified world and chunk position
     */
    boolean isOwnedByCurrentRegion(@NotNull World world, int chunkX, int chunkZ);

    /**
     * Folia: Returns whether the current thread is ticking a region and that
     * the region being ticked owns the chunks centered at the specified world
     * and chunk position within the specified square radius. Specifically,
     * this function checks that every chunk with position x in [centerX -
     * radius, centerX + radius] and position z in [centerZ - radius, centerZ +
     * radius] is owned by the current ticking region.
     * Paper/Spigot: Returns {@link Server#isPrimaryThread()}
     *
     * @param world Specified world
     * @param chunkX Specified x-coordinate of the chunk position
     * @param chunkZ Specified z-coordinate of the chunk position
     * @param squareRadiusChunks Specified square radius. Must be >= 0. Note that this parameter is not a squared radius, but rather a Chebyshev Distance.
     * @return true if the current thread is ticking the region that owns the chunks centered at the specified world and chunk position within the specified square radius
     */
    boolean isOwnedByCurrentRegion(@NotNull World world, int chunkX, int chunkZ, int squareRadiusChunks);

    /**
     * Folia: Returns whether the current thread is ticking a region and that
     * the region being ticked owns the specified entity. Note that this
     * function is the only appropriate method of checking for ownership of an
     * entity, as retrieving the entity's location is undefined unless the
     * entity is owned by the current region.
     * Paper/Spigot: Returns {@link Server#isPrimaryThread()}
     *
     * @param entity Specified entity
     * @return true if the current thread is ticking the region that owns the specified entity
     */
    boolean isOwnedByCurrentRegion(@NotNull Entity entity);

    /**
     * Folia: Returns whether the current thread is ticking the global region.
     * Paper/Spigot: Returns {@link Server#isPrimaryThread()}
     *
     * @return true if the current thread is ticking the global region
     */
    boolean isGlobalTickThread();

    /**
     * Scheduler that may be used by plugins to schedule tasks to execute asynchronously from the server tick process.
     */
    @NotNull IAsyncScheduler async();

    /**
     * An entity can move between worlds with an arbitrary tick delay, be temporarily removed
     * for players (i.e end credits), be partially removed from world state (i.e inactive but not removed),
     * teleport between ticking regions, teleport between worlds, and even be removed entirely from the server.
     * The uncertainty of an entity's state can make it difficult to schedule tasks without worrying about undefined
     * behaviors resulting from any of the states listed previously.
     *
     * <p>
     * This class is designed to eliminate those states by providing an interface to run tasks only when an entity
     * is contained in a world, on the owning thread for the region, and by providing the current Entity object.
     * The scheduler also allows a task to provide a callback, the "retired" callback, that will be invoked
     * if the entity is removed before a task that was scheduled could be executed. The scheduler is also
     * completely thread-safe, allowing tasks to be scheduled from any thread context. The scheduler also indicates
     * properly whether a task was scheduled successfully (i.e scheduler not retired), thus the code scheduling any task
     * knows whether the given callbacks will be invoked eventually or not - which may be critical for off-thread
     * contexts.
     * </p>
     */
    @NotNull IEntityScheduler entity(@NotNull Entity entity);

    /**
     * The global region task scheduler may be used to schedule tasks that will execute on the global region.
     * <p>
     * The global region is responsible for maintaining world day time, world game time, weather cycle,
     * sleep night skipping, executing commands for console, and other misc. tasks that do not belong to any specific region.
     * </p>
     */
    @NotNull IGlobalScheduler global();

    /**
     * The region task scheduler can be used to schedule tasks by location to be executed on the region which owns the location.
     * <p>
     * <b>Note</b>: It is entirely inappropriate to use the region scheduler to schedule tasks for entities.
     * If you wish to schedule tasks to perform actions on entities, you should be using {@link Entity#getScheduler()}
     * as the entity scheduler will "follow" an entity if it is teleported, whereas the region task scheduler
     * will not.
     * </p>
     */
    @NotNull IRegionScheduler region();

    /**
     * Teleport an entity to a location async
     * @param entity Entity to teleport
     * @param location Location to teleport to
     * @return Future when the teleport is completed or failed
     */
    default @NotNull CompletableFuture<@NotNull Boolean> teleportAsync(@NotNull Entity entity, @NotNull Location location) {
        return teleportAsync(entity, location, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    /**
     * Teleport an entity to a location async with a cause
     * @param entity Entity to teleport
     * @param location Location to teleport to
     * @param cause Cause of the teleport
     * @return Future when the teleport is completed or failed
     */
    @NotNull CompletableFuture<@NotNull Boolean> teleportAsync(@NotNull Entity entity, @NotNull Location location, @NotNull PlayerTeleportEvent.TeleportCause cause);

    static @NotNull Platform create(@NotNull Plugin plugin) {
        if (hasClass("com.destroystokyo.paper.PaperConfig") || hasClass("io.papermc.paper.configuration.Configuration"))
            return new PaperPlatform(plugin);
        if (hasClass("org.spigotmc.SpigotConfig"))
            return new SpigotPlatform(plugin);
        throw new IllegalStateException("Unsupported platform!");
    }

    static boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
