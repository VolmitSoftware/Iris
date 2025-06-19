package com.volmit.iris.core.service;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.Looper;
import lombok.Synchronized;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class IrisEngineSVC implements IrisService {
    private final AtomicInteger tectonicLimit = new AtomicInteger(30);
    private final AtomicInteger tectonicPlates = new AtomicInteger();
    private final AtomicInteger queuedTectonicPlates = new AtomicInteger();
    private final AtomicInteger trimmerAlive = new AtomicInteger();
    private final AtomicInteger unloaderAlive = new AtomicInteger();
    private final AtomicInteger totalWorlds = new AtomicInteger();
    private final AtomicDouble maxIdleDuration = new AtomicDouble();
    private final AtomicDouble minIdleDuration = new AtomicDouble();
    private final AtomicLong loadedChunks = new AtomicLong();
    private final KMap<World, Registered> worlds = new KMap<>();
    private ScheduledExecutorService service;
    private Looper updateTicker;

    @Override
    public void onEnable() {
        var settings = IrisSettings.get().getPerformance();
        var engine = settings.getEngineSVC();
        service = Executors.newScheduledThreadPool(0,
                (engine.isUseVirtualThreads()
                        ? Thread.ofVirtual()
                        : Thread.ofPlatform().priority(engine.getPriority()))
                        .name("Iris EngineSVC-", 0)
                        .factory());
        tectonicLimit.set(settings.getTectonicPlateSize());
        Bukkit.getWorlds().forEach(this::add);
        setup();
    }

    @Override
    public void onDisable() {
        service.shutdown();
        updateTicker.interrupt();
        worlds.keySet().forEach(this::remove);
        worlds.clear();
    }

    public void engineStatus(VolmitSender sender) {
        sender.sendMessage(C.DARK_PURPLE + "-------------------------");
        sender.sendMessage(C.DARK_PURPLE + "Status:");
        sender.sendMessage(C.DARK_PURPLE + "- Service: " + C.LIGHT_PURPLE + (service.isShutdown() ? "Shutdown" : "Running"));
        sender.sendMessage(C.DARK_PURPLE + "- Updater: " + C.LIGHT_PURPLE + (updateTicker.isAlive() ? "Running" : "Stopped"));
        sender.sendMessage(C.DARK_PURPLE + "- Trimmers: " + C.LIGHT_PURPLE + trimmerAlive.get());
        sender.sendMessage(C.DARK_PURPLE + "- Unloaders: " + C.LIGHT_PURPLE + unloaderAlive.get());
        sender.sendMessage(C.DARK_PURPLE + "Tectonic Plates:");
        sender.sendMessage(C.DARK_PURPLE + "- Limit: " + C.LIGHT_PURPLE + tectonicLimit.get());
        sender.sendMessage(C.DARK_PURPLE + "- Total: " + C.LIGHT_PURPLE + tectonicPlates.get());
        sender.sendMessage(C.DARK_PURPLE + "- Queued: " + C.LIGHT_PURPLE + queuedTectonicPlates.get());
        sender.sendMessage(C.DARK_PURPLE + "- Max Idle Duration: " + C.LIGHT_PURPLE + Form.duration(maxIdleDuration.get(), 2));
        sender.sendMessage(C.DARK_PURPLE + "- Min Idle Duration: " + C.LIGHT_PURPLE + Form.duration(minIdleDuration.get(), 2));
        sender.sendMessage(C.DARK_PURPLE + "Other:");
        sender.sendMessage(C.DARK_PURPLE + "- Iris Worlds: " + C.LIGHT_PURPLE + totalWorlds.get());
        sender.sendMessage(C.DARK_PURPLE + "- Loaded Chunks: " + C.LIGHT_PURPLE + loadedChunks.get());
        sender.sendMessage(C.DARK_PURPLE + "- Cache Size: " + C.LIGHT_PURPLE + Form.f(IrisData.cacheSize()));
        sender.sendMessage(C.DARK_PURPLE + "-------------------------");
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        remove(event.getWorld());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        add(event.getWorld());
    }

    private void remove(World world) {
        var entry = worlds.remove(world);
        if (entry == null) return;
        entry.close();
    }

    private void add(World world) {
        var access = IrisToolbelt.access(world);
        if (access == null) return;
        worlds.put(world, new Registered(world.getName(), access));
    }

    private synchronized void setup() {
        if (updateTicker != null && updateTicker.isAlive())
            return;

        updateTicker = new Looper() {
            @Override
            protected long loop() {
                try {
                    queuedTectonicPlates.set(0);
                    tectonicPlates.set(0);
                    loadedChunks.set(0);
                    unloaderAlive.set(0);
                    trimmerAlive.set(0);
                    totalWorlds.set(0);

                    double maxDuration = Long.MIN_VALUE;
                    double minDuration = Long.MAX_VALUE;
                    for (var entry : worlds.entrySet()) {
                        var registered = entry.getValue();
                        if (registered.closed) continue;

                        totalWorlds.incrementAndGet();
                        unloaderAlive.addAndGet(registered.unloaderAlive() ? 1 : 0);
                        trimmerAlive.addAndGet(registered.trimmerAlive() ? 1 : 0);

                        var engine = registered.getEngine();
                        if (engine == null) continue;

                        queuedTectonicPlates.addAndGet((int) engine.getMantle().getUnloadRegionCount());
                        tectonicPlates.addAndGet(engine.getMantle().getLoadedRegionCount());
                        loadedChunks.addAndGet(entry.getKey().getLoadedChunks().length);

                        double duration = engine.getMantle().getAdjustedIdleDuration();
                        if (duration > maxDuration) maxDuration = duration;
                        if (duration < minDuration) minDuration = duration;
                    }
                    maxIdleDuration.set(maxDuration);
                    minIdleDuration.set(minDuration);

                    worlds.values().forEach(Registered::update);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                return 1000;
            }
        };
        updateTicker.start();
    }

    private final class Registered {
        private final String name;
        private final PlatformChunkGenerator access;
        private transient ScheduledFuture<?> trimmer;
        private transient ScheduledFuture<?> unloader;
        private transient boolean closed;

        private Registered(String name, PlatformChunkGenerator access) {
            this.name = name;
            this.access = access;
            update();
        }

        private boolean unloaderAlive() {
            return unloader != null && !unloader.isDone() && !unloader.isCancelled();
        }

        private boolean trimmerAlive() {
            return trimmer != null && !trimmer.isDone() && !trimmer.isCancelled();
        }

        @Synchronized
        private void update() {
            if (closed || service == null || service.isShutdown())
                return;

            if (trimmer == null || trimmer.isDone() || trimmer.isCancelled()) {
                trimmer = service.scheduleAtFixedRate(() -> {
                    Engine engine = getEngine();
                    if (engine == null || !engine.getMantle().getMantle().shouldReduce(engine))
                        return;

                    try {
                        engine.getMantle().trim(tectonicLimit());
                    } catch (Throwable e) {
                        Iris.reportError(e);
                        Iris.error("EngineSVC: Failed to trim for " + name);
                        e.printStackTrace();
                    }
                }, RNG.r.nextInt(1000), 1000, TimeUnit.MILLISECONDS);
            }

            if (unloader == null || unloader.isDone() || unloader.isCancelled()) {
                unloader = service.scheduleAtFixedRate(() -> {
                    Engine engine = getEngine();
                    if (engine == null || !engine.getMantle().getMantle().shouldReduce(engine))
                        return;

                    try {
                        long unloadStart = System.currentTimeMillis();
                        int count = engine.getMantle().unloadTectonicPlate(tectonicLimit());
                        if (count > 0) {
                            Iris.debug(C.GOLD + "Unloaded " + C.YELLOW + count + " TectonicPlates in " + C.RED + Form.duration(System.currentTimeMillis() - unloadStart, 2));
                        }
                    } catch (Throwable e) {
                        Iris.reportError(e);
                        Iris.error("EngineSVC: Failed to unload for " + name);
                        e.printStackTrace();
                    }
                }, RNG.r.nextInt(1000), 1000, TimeUnit.MILLISECONDS);
            }
        }

        private int tectonicLimit() {
            return tectonicLimit.get() / Math.max(worlds.size(), 1);
        }

        @Synchronized
        private void close() {
            if (closed) return;
            closed = true;

            if (trimmer != null) {
                trimmer.cancel(false);
                trimmer = null;
            }

            if (unloader != null) {
                unloader.cancel(false);
                unloader = null;
            }
        }

        @Nullable
        private Engine getEngine() {
            if (closed) return null;
            return access.getEngine();
        }
    }
}
