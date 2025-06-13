package com.volmit.iris.util.scheduling.spigot;

import com.volmit.iris.util.scheduling.Platform;
import com.volmit.iris.util.scheduling.spigot.split.SpigotAsyncScheduler;
import com.volmit.iris.util.scheduling.spigot.split.SpigotEntityScheduler;
import com.volmit.iris.util.scheduling.spigot.split.SpigotGlobalScheduler;
import com.volmit.iris.util.scheduling.spigot.split.SpigotRegionScheduler;
import com.volmit.iris.util.scheduling.split.IAsyncScheduler;
import com.volmit.iris.util.scheduling.split.IEntityScheduler;
import com.volmit.iris.util.scheduling.split.IGlobalScheduler;
import com.volmit.iris.util.scheduling.split.IRegionScheduler;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class SpigotPlatform implements Platform {
    private final Server server;
    private final IAsyncScheduler async;
    private final IGlobalScheduler global;
    private final IRegionScheduler region;

    public SpigotPlatform(@NotNull Plugin plugin) {
        server = plugin.getServer();
        var scheduler = server.getScheduler();
        async = new SpigotAsyncScheduler(plugin, scheduler);
        global = new SpigotGlobalScheduler(plugin, scheduler);
        region = new SpigotRegionScheduler(global);
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Location location) {
        return server.isPrimaryThread();
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Location location, int squareRadiusChunks) {
        return server.isPrimaryThread();
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Block block) {
        return server.isPrimaryThread();
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull World world, int chunkX, int chunkZ) {
        return server.isPrimaryThread();
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull World world, int chunkX, int chunkZ, int squareRadiusChunks) {
        return server.isPrimaryThread();
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Entity entity) {
        return server.isPrimaryThread();
    }

    @Override
    public boolean isGlobalTickThread() {
        return server.isPrimaryThread();
    }

    @Override
    public @NotNull IAsyncScheduler async() {
        return async;
    }

    @Override
    public @NotNull IEntityScheduler entity(@NotNull Entity entity) {
        return new SpigotEntityScheduler(global, entity);
    }

    @Override
    public @NotNull IGlobalScheduler global() {
        return global;
    }

    @Override
    public @NotNull IRegionScheduler region() {
        return region;
    }

    @Override
    public @NotNull CompletableFuture<@NotNull Boolean> teleportAsync(@NotNull Entity entity, @NotNull Location location, PlayerTeleportEvent.@NotNull TeleportCause cause) {
        return global().<Boolean>run(task -> isValid(entity) && entity.teleport(location)).result().thenApply(b -> b != null ? b : false);
    }

    public static boolean isValid(Entity entity) {
        if (entity.isValid()) {
            return !(entity instanceof Player) || ((Player) entity).isOnline();
        }
        return entity instanceof Projectile && !entity.isDead();
    }
}
