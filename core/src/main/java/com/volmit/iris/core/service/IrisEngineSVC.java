package com.volmit.iris.core.service;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.Looper;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class IrisEngineSVC implements IrisService {
    private final AtomicInteger tectonicLimit = new AtomicInteger(30);
    private final AtomicInteger tectonicPlates = new AtomicInteger();
    private final AtomicInteger queuedTectonicPlates = new AtomicInteger();
    private final AtomicDouble maxIdleDuration = new AtomicDouble();
    private final AtomicDouble minIdleDuration = new AtomicDouble();
    private final AtomicLong loadedChunks = new AtomicLong();
    private final List<Pair<World, PlatformChunkGenerator>> worlds = new CopyOnWriteArrayList<>();
    private Looper trimTicker;
    private Looper unloadTicker;
    private Looper updateTicker;

    @Override
    public void onEnable() {
        tectonicLimit.set(IrisSettings.get().getPerformance().getTectonicPlateSize());
        for (World world : Bukkit.getWorlds()) {
            var access = IrisToolbelt.access(world);
            if (access == null) return;
            worlds.add(new Pair<>(world, access));
        }

        trimLogic();
        unloadLogic();
        setup();
    }

    @Override
    public void onDisable() {
        updateTicker.interrupt();
        trimTicker.interrupt();
        unloadTicker.interrupt();
        worlds.clear();
    }

    public void engineStatus(VolmitSender sender) {
        sender.sendMessage(C.DARK_PURPLE + "-------------------------");
        sender.sendMessage(C.DARK_PURPLE + "Status:");
        sender.sendMessage(C.DARK_PURPLE + "- Trim: " + C.LIGHT_PURPLE + trimTicker.isAlive());
        sender.sendMessage(C.DARK_PURPLE + "- Unload: " + C.LIGHT_PURPLE + unloadTicker.isAlive());
        sender.sendMessage(C.DARK_PURPLE + "- Update: " + C.LIGHT_PURPLE + updateTicker.isAlive());
        sender.sendMessage(C.DARK_PURPLE + "Tectonic Plates:");
        sender.sendMessage(C.DARK_PURPLE + "- Limit: " + C.LIGHT_PURPLE + tectonicLimit.get());
        sender.sendMessage(C.DARK_PURPLE + "- Total: " + C.LIGHT_PURPLE + tectonicPlates.get());
        sender.sendMessage(C.DARK_PURPLE + "- Queued: " + C.LIGHT_PURPLE + queuedTectonicPlates.get());
        sender.sendMessage(C.DARK_PURPLE + "- Max Idle Duration: " + C.LIGHT_PURPLE + Form.duration(maxIdleDuration.get(), 2));
        sender.sendMessage(C.DARK_PURPLE + "- Min Idle Duration: " + C.LIGHT_PURPLE + Form.duration(minIdleDuration.get(), 2));
        sender.sendMessage(C.DARK_PURPLE + "Other:");
        sender.sendMessage(C.DARK_PURPLE + "- Iris Worlds: " + C.LIGHT_PURPLE + worlds.size());
        sender.sendMessage(C.DARK_PURPLE + "- Loaded Chunks: " + C.LIGHT_PURPLE + loadedChunks.get());
        sender.sendMessage(C.DARK_PURPLE + "- Cache Size: " + C.LIGHT_PURPLE + Form.f(IrisData.cacheSize()));
        sender.sendMessage(C.DARK_PURPLE + "-------------------------");
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        worlds.removeIf(p -> p.getA() == event.getWorld());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        var world = event.getWorld();
        var access = IrisToolbelt.access(world);
        if (access == null) return;
        worlds.add(new Pair<>(world, access));
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

                    double maxDuration = Long.MIN_VALUE;
                    double minDuration = Long.MAX_VALUE;
                    for (var pair : worlds) {
                        var engine = pair.getB().getEngine();
                        if (engine == null) continue;

                        queuedTectonicPlates.addAndGet((int) engine.getMantle().getUnloadRegionCount());
                        tectonicPlates.addAndGet(engine.getMantle().getLoadedRegionCount());
                        loadedChunks.addAndGet(pair.getA().getLoadedChunks().length);

                        double duration = engine.getMantle().getAdjustedIdleDuration();
                        if (duration > maxDuration) maxDuration = duration;
                        if (duration < minDuration) minDuration = duration;
                    }
                    maxIdleDuration.set(maxDuration);
                    minIdleDuration.set(minDuration);

                    if (!trimTicker.isAlive()) {
                        Iris.error("TrimTicker found dead! Booting it up!");
                        try {
                            trimLogic();
                        } catch (Exception e) {
                            Iris.error("What happened?");
                            e.printStackTrace();
                        }
                    }

                    if (!unloadTicker.isAlive()) {
                        Iris.error("UnloadTicker found dead! Booting it up!");
                        try {
                            unloadLogic();
                        } catch (Exception e) {
                            Iris.error("What happened?");
                            e.printStackTrace();
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                return 1000;
            }
        };
        updateTicker.start();
    }

    private synchronized void trimLogic() {
        if (trimTicker != null && trimTicker.isAlive())
            return;

        trimTicker = new Looper() {
            private final Supplier<Engine> supplier = createSupplier();

            @Override
            protected long loop() {
                long start = System.currentTimeMillis();
                try {
                    Engine engine = supplier.get();
                    if (engine != null) {
                        engine.getMantle().trim(tectonicLimit.get() / worlds.size());
                    }
                } catch (Throwable e) {
                    Iris.reportError(e);
                    Iris.error("EngineSVC: Failed to trim.");
                    e.printStackTrace();
                    return -1;
                }

                int size = worlds.size();
                long time = (size > 0 ? 1000 / size : 1000) - (System.currentTimeMillis() - start);
                if (time <= 0)
                    return 0;
                return time;
            }
        };
        trimTicker.start();
    }

    private synchronized void unloadLogic() {
        if (unloadTicker != null && unloadTicker.isAlive())
            return;

        unloadTicker = new Looper() {
            private final Supplier<Engine> supplier = createSupplier();

            @Override
            protected long loop() {
                long start = System.currentTimeMillis();
                try {
                    Engine engine = supplier.get();
                    if (engine != null) {
                        long unloadStart = System.currentTimeMillis();
                        int count = engine.getMantle().unloadTectonicPlate(tectonicLimit.get() / worlds.size());
                        if (count > 0) {
                            Iris.debug(C.GOLD + "Unloaded " + C.YELLOW + count + " TectonicPlates in " + C.RED + Form.duration(System.currentTimeMillis() - unloadStart, 2));
                        }
                    }
                } catch (Throwable e) {
                    Iris.reportError(e);
                    Iris.error("EngineSVC: Failed to unload.");
                    e.printStackTrace();
                    return -1;
                }

                int size = worlds.size();
                long time = (size > 0 ? 1000 / size : 1000) - (System.currentTimeMillis() - start);
                if (time <= 0)
                    return 0;
                return time;
            }
        };
        unloadTicker.start();
    }

    private Supplier<Engine> createSupplier() {
        AtomicInteger i = new AtomicInteger();
        return () -> {
            if (i.get() >= worlds.size()) {
                i.set(0);
            }
            try {
                for (int j = 0; j < worlds.size(); j++) {
                    var pair = worlds.get(i.getAndIncrement());
                    if (i.get() >= worlds.size()) {
                        i.set(0);
                    }

                    var engine = pair.getB().getEngine();
                    if (engine != null && !engine.isClosed() && engine.getMantle().getMantle().shouldReduce(engine)) {
                        return engine;
                    }
                }
            } catch (Throwable e) {
                Iris.error("EngineSVC: Failed to create supplier.");
                e.printStackTrace();
                Iris.reportError(e);
            }
            return null;
        };
    }
}
