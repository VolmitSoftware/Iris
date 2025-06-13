package com.volmit.iris.util.scheduling.paper;

import com.volmit.iris.util.scheduling.Platform;
import com.volmit.iris.util.scheduling.paper.split.PaperAsyncScheduler;
import com.volmit.iris.util.scheduling.paper.split.PaperEntityScheduler;
import com.volmit.iris.util.scheduling.paper.split.PaperGlobalScheduler;
import com.volmit.iris.util.scheduling.paper.split.PaperRegionScheduler;
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
import java.util.function.BooleanSupplier;

public class PaperPlatform implements Platform {
    private final Plugin plugin;
    private final Server server;
    private final IAsyncScheduler async;
    private final IGlobalScheduler global;
    private final IRegionScheduler region;
    private final BooleanSupplier globalTickThread;

    public PaperPlatform(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        async = new PaperAsyncScheduler(plugin, server.getAsyncScheduler());
        global = new PaperGlobalScheduler(plugin, server.getGlobalRegionScheduler());
        region = new PaperRegionScheduler(plugin, server.getRegionScheduler());

        BooleanSupplier method;
        try {
            method = server::isGlobalTickThread;
        } catch (Throwable e) {
            method = server::isPrimaryThread;
        }
        globalTickThread = method;
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Location location) {
        return server.isOwnedByCurrentRegion(location);
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Location location, int squareRadiusChunks) {
        return server.isOwnedByCurrentRegion(location, squareRadiusChunks);
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Block block) {
        return server.isOwnedByCurrentRegion(block);
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull World world, int chunkX, int chunkZ) {
        return server.isOwnedByCurrentRegion(world, chunkX, chunkZ);
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull World world, int chunkX, int chunkZ, int squareRadiusChunks) {
        return server.isOwnedByCurrentRegion(world, chunkX, chunkZ, squareRadiusChunks);
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Entity entity) {
        return server.isOwnedByCurrentRegion(entity);
    }

    @Override
    public boolean isGlobalTickThread() {
        return globalTickThread.getAsBoolean();
    }

    @Override
    public @NotNull IAsyncScheduler async() {
        return async;
    }

    @Override
    public @NotNull IEntityScheduler entity(@NotNull Entity entity) {
        return new PaperEntityScheduler(plugin, entity.getScheduler());
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
        return entity.teleportAsync(location, cause);
    }
}
