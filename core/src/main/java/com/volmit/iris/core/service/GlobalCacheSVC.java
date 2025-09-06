package com.volmit.iris.core.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.pregenerator.cache.PregenCache;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.KCache;
import com.volmit.iris.util.plugin.IrisService;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.function.Function;

public class GlobalCacheSVC implements IrisService {
    private static final Cache<String, PregenCache> REFERENCE_CACHE = Caffeine.newBuilder()
            .executor(KCache.EXECUTOR)
            .scheduler(Scheduler.systemScheduler())
            .weakValues()
            .build();
    private final KMap<String, PregenCache> globalCache = new KMap<>();
    private transient boolean lastState;
    private static boolean disabled = true;

    @Override
    public void onEnable() {
        disabled = false;
        lastState = !IrisSettings.get().getWorld().isGlobalPregenCache();
        if (lastState) return;
        Bukkit.getWorlds().forEach(this::createCache);
    }

    @Override
    public void onDisable() {
        disabled = true;
        globalCache.qclear((world, cache) -> cache.write());
    }

    @Nullable
    public PregenCache get(@NonNull World world) {
        return globalCache.get(world.getName());
    }

    @Nullable
    public PregenCache get(@NonNull String world) {
        return globalCache.get(world);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(WorldInitEvent event) {
        if (isDisabled()) return;
        createCache(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(WorldUnloadEvent event) {
        var cache = globalCache.remove(event.getWorld().getName());
        if (cache == null) return;
        cache.write();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ChunkLoadEvent event) {
        var cache = get(event.getWorld());
        if (cache == null) return;
        cache.cacheChunk(event.getChunk().getX(), event.getChunk().getZ());
    }

    private void createCache(World world) {
        globalCache.computeIfAbsent(world.getName(), GlobalCacheSVC::createDefault);
    }

    private boolean isDisabled() {
        boolean conf = IrisSettings.get().getWorld().isGlobalPregenCache();
        if (lastState != conf)
            return lastState;

        if (conf) {
            Bukkit.getWorlds().forEach(this::createCache);
        } else {
            globalCache.values().removeIf(cache -> {
                cache.write();
                return true;
            });
        }

        return lastState = !conf;
    }


    @NonNull
    public static PregenCache createCache(@NonNull String worldName, @NonNull Function<String, PregenCache> provider) {
        return REFERENCE_CACHE.get(worldName, provider);
    }

    @NonNull
    public static PregenCache createDefault(@NonNull String worldName) {
        return createCache(worldName, GlobalCacheSVC::createDefault0);
    }

    private static PregenCache createDefault0(String worldName) {
        if (disabled) return PregenCache.EMPTY;
        return PregenCache.create(new File(Bukkit.getWorldContainer(), String.join(File.separator, worldName, "iris", "pregen"))).sync();
    }
}
