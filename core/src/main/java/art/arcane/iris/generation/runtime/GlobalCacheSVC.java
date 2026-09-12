package art.arcane.iris.generation.runtime;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.world.pregen.PregenCache;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import art.arcane.volmlib.util.scheduling.Looper;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
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
    private static final long TRIMMER_JOIN_MS = 2_000;
    private static final KMap<String, Reference<PregenCache>> REFERENCE_CACHE = new KMap<>();
    private final KMap<String, PregenCache> globalCache = new KMap<>();
    private transient boolean lastState;
    private static volatile boolean disabled = true;
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
        Looper activeTrimmer = trimmer;
        trimmer = null;
        if (activeTrimmer != null) {
            activeTrimmer.interrupt();
            try {
                activeTrimmer.join(TRIMMER_JOIN_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                IrisLogging.warn("Interrupted while waiting for the global cache trimmer to stop.");
            }
            if (activeTrimmer.isAlive()) {
                IrisLogging.warn("Global cache trimmer did not stop within " + TRIMMER_JOIN_MS + "ms.");
            }
        }
        globalCache.qclear((world, cache) -> cache.write());
    }

    @Nullable
    public PregenCache get(@NonNull World world) {
        return globalCache.get(WorldIdentity.serialize(world));
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
        PregenCache cache = globalCache.remove(WorldIdentity.serialize(event.getWorld()));
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
        String worldIdentity = WorldIdentity.serialize(world);
        globalCache.computeIfAbsent(worldIdentity, GlobalCacheSVC::createDefault);
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
    public static PregenCache createCache(@NonNull String worldIdentity, @NonNull Function<String, PregenCache> provider) {
        PregenCache[] holder = new PregenCache[1];
        REFERENCE_CACHE.compute(worldIdentity, (identity, ref) -> {
            if (ref != null) {
                if ((holder[0] = ref.get()) != null)
                    return ref;
            }
            return new WeakReference<>(holder[0] = provider.apply(worldIdentity));
        });
        return holder[0];
    }

    @NonNull
    public static PregenCache createDefault(@NonNull String worldIdentity) {
        return createCache(worldIdentity, GlobalCacheSVC::createDefault0);
    }

    private static PregenCache createDefault0(String worldIdentity) {
        if (disabled) return PregenCache.EMPTY;
        NamespacedKey worldKey = WorldIdentity.parse(worldIdentity);
        File dimensionRoot = requireCacheDimensionRoot(
                Bukkit.getWorldContainer(),
                IrisWorldStorage.levelRoot(),
                worldKey
        );
        return PregenCache.create(new File(dimensionRoot, "iris/pregen")).sync();
    }

    static File requireCacheDimensionRoot(File worldContainer, File levelRoot, NamespacedKey worldKey) {
        return IrisWorldStorage.requireFrozenDimensionRoot(
                worldContainer,
                levelRoot,
                IrisWorldStorage.configuredWorldName(worldKey, levelRoot.getName()),
                worldKey
        );
    }
}
