package com.volmit.iris.core.service;

import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.pregenerator.cache.PregenCache;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.Looper;
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
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.function.Function;

public class GlobalCacheSVC implements IrisService {
    private static final KMap<String, Reference<PregenCache>> REFERENCE_CACHE = new KMap<>();
    private final KMap<String, PregenCache> globalCache = new KMap<>();
    private transient boolean lastState;
    private static boolean disabled = true;
    private Looper trimmer;

    @Override
    public void onEnable() {
        disabled = false;
        trimmer = new Looper() {
            @Override
            protected long loop() {
                var it = REFERENCE_CACHE.values().iterator();
                while (it.hasNext()) {
                    var cache = it.next().get();
                    if (cache == null) it.remove();
                    else cache.trim(10_000);
                }
                return disabled ? -1 : 2_000;
            }
        };
        trimmer.start();
        lastState = !IrisSettings.get().getWorld().isGlobalPregenCache();
        if (lastState) return;
        Bukkit.getWorlds().forEach(this::createCache);
    }

    @Override
    public void onDisable() {
        disabled = true;
        try {
            trimmer.join();
        } catch (InterruptedException ignored) {}
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
        if (!IrisToolbelt.isIrisWorld(world)) return;
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
        PregenCache[] holder = new PregenCache[1];
        REFERENCE_CACHE.compute(worldName, (name, ref) -> {
            if (ref != null) {
                if ((holder[0] = ref.get()) != null)
                    return ref;
            }
            return new WeakReference<>(holder[0] = provider.apply(worldName));
        });
        return holder[0];
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
