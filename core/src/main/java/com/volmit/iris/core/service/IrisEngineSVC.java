package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.TectonicPlate;
import com.volmit.iris.util.misc.getHardware;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.Looper;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.checkerframework.checker.units.qual.A;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class IrisEngineSVC implements IrisService {
    public static IrisEngineSVC instance;
    public boolean isServerShuttingDown = false;
    public boolean isServerLoaded = false;
    private static final AtomicInteger tectonicLimit = new AtomicInteger(30);
    private ReentrantLock lastUseLock;
    private KMap<World, Long> lastUse;
    private List<World> IrisWorlds;
    private Looper cacheTicker;
    private Looper trimTicker;
    private Looper unloadTicker;
    private Looper updateTicker;
    private PrecisionStopwatch trimAlive;
    private PrecisionStopwatch unloadAlive;
    public PrecisionStopwatch trimActiveAlive;
    public PrecisionStopwatch unloadActiveAlive;
    private AtomicInteger TotalTectonicPlates;
    private AtomicInteger TotalQueuedTectonicPlates;
    private AtomicInteger TotalNotQueuedTectonicPlates;
    private AtomicBoolean IsUnloadAlive;
    private AtomicBoolean IsTrimAlive;
    ChronoLatch cl;

    public List<World> corruptedIrisWorlds = new ArrayList<>();

    @Override
    public void onEnable() {
        this.cl = new ChronoLatch(5000);
        lastUse = new KMap<>();
        lastUseLock = new ReentrantLock();
        IrisWorlds = new ArrayList<>();
        IsUnloadAlive = new AtomicBoolean(true);
        IsTrimAlive = new AtomicBoolean(true);
        trimActiveAlive = new PrecisionStopwatch();
        unloadActiveAlive = new PrecisionStopwatch();
        trimAlive = new PrecisionStopwatch();
        unloadAlive = new PrecisionStopwatch();
        TotalTectonicPlates = new AtomicInteger();
        TotalQueuedTectonicPlates = new AtomicInteger();
        TotalNotQueuedTectonicPlates = new AtomicInteger();
        tectonicLimit.set(2);
        long t = getHardware.getProcessMemory();
        while (t > 200) {
            tectonicLimit.getAndAdd(1);
            t = t - 200;
        }
        this.setup();
        this.TrimLogic();
        this.UnloadLogic();

        trimAlive.begin();
        unloadAlive.begin();
        trimActiveAlive.begin();
        unloadActiveAlive.begin();

        updateTicker.start();
        cacheTicker.start();
        //trimTicker.start();
        //unloadTicker.start();
        instance = this;

    }

    public void engineStatus() {
        boolean trimAlive = trimTicker.isAlive();
        boolean unloadAlive = unloadTicker.isAlive();
        Iris.info("Status:");
        Iris.info("- Trim: " + trimAlive);
        Iris.info("- Unload: " + unloadAlive);

    }

    public static int getTectonicLimit() {
        return tectonicLimit.get();
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        updateWorlds();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        updateWorlds();
    }

    @EventHandler
    public void onServerBoot(ServerLoadEvent event) {
        isServerLoaded = true;
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(Iris.instance)) {
            isServerShuttingDown = true;
        }
    }

    public void updateWorlds() {
        for (World world : Bukkit.getWorlds()) {
            try {
                if (IrisToolbelt.access(world).getEngine() != null) {
                    IrisWorlds.add(world);
                }
            } catch (Exception e) {
                // no
            }
        }
    }

    private void setup() {
        cacheTicker = new Looper() {
            @Override
            protected long loop() {
                long now = System.currentTimeMillis();
                lastUseLock.lock();
                try {
                    for (World key : new ArrayList<>(lastUse.keySet())) {
                        Long last = lastUse.get(key);
                        if (last == null)
                            continue;
                        if (now - last > 60000) {
                            lastUse.remove(key);
                        }
                    }
                } finally {
                    lastUseLock.unlock();
                }
                return 1000;
            }
        };

        updateTicker = new Looper() {
            @Override
            protected long loop() {
                try {
                    TotalQueuedTectonicPlates.set(0);
                    TotalNotQueuedTectonicPlates.set(0);
                    TotalTectonicPlates.set(0);
                    for (World world : IrisWorlds) {
                        Engine engine = Objects.requireNonNull(IrisToolbelt.access(world)).getEngine();
                        TotalQueuedTectonicPlates.addAndGet((int) engine.getMantle().getToUnload());
                        TotalNotQueuedTectonicPlates.addAndGet((int) engine.getMantle().getNotQueuedLoadedRegions());
                        TotalTectonicPlates.addAndGet(engine.getMantle().getLoadedRegionCount());
                    }
                    if (!isServerShuttingDown && isServerLoaded) {
                        if (!trimTicker.isAlive()) {
                            Iris.info(C.RED + "TrimTicker found dead! Booting it up!");
                            try {
                                TrimLogic();
                            } catch (Exception e) {
                                Iris.error("What happened?");
                                e.printStackTrace();
                            }
                        }

                        if (!unloadTicker.isAlive()) {
                            Iris.info(C.RED + "UnloadTicker found dead! Booting it up!");
                            try {
                               UnloadLogic();
                            } catch (Exception e) {
                                Iris.error("What happened?");
                                e.printStackTrace();
                            }
                        }
                    }

                } catch (Exception e) {
                    return -1;
                }
                return 1000;
            }
        };
    }
    public void TrimLogic() {
        if (trimTicker == null || !trimTicker.isAlive()) {
            trimTicker = new Looper() {
                private final Supplier<Engine> supplier = createSupplier();

                @Override
                protected long loop() {
                    long start = System.currentTimeMillis();
                    trimAlive.reset();
                    try {
                        Engine engine = supplier.get();
                        if (engine != null) {
                            engine.getMantle().trim(tectonicLimit.get() / lastUse.size());
                        }
                    } catch (Throwable e) {
                        Iris.reportError(e);
                        Iris.info(C.RED + "EngineSVC: Failed to trim.");
                        e.printStackTrace();
                        return -1;
                    }

                    int size = lastUse.size();
                    long time = (size > 0 ? 1000 / size : 1000) - (System.currentTimeMillis() - start);
                    if (time <= 0)
                        return 0;
                    return time;
                }
            };
            trimTicker.start();
        }
    }
    public void UnloadLogic() {
        if (unloadTicker == null || !unloadTicker.isAlive()) {
            unloadTicker = new Looper() {
                private final Supplier<Engine> supplier = createSupplier();

                @Override
                protected long loop() {
                    long start = System.currentTimeMillis();
                    unloadAlive.reset();
                    try {
                        Engine engine = supplier.get();
                        if (engine != null) {
                            long unloadStart = System.currentTimeMillis();
                            int count = engine.getMantle().unloadTectonicPlate(tectonicLimit.get() / lastUse.size());
                            if (count > 0) {
                                Iris.debug(C.GOLD + "Unloaded " + C.YELLOW + count + " TectonicPlates in " + C.RED + Form.duration(System.currentTimeMillis() - unloadStart, 2));
                            }
                        }
                    } catch (Throwable e) {
                        Iris.reportError(e);
                        Iris.info(C.RED + "EngineSVC: Failed to unload.");
                        e.printStackTrace();
                        return -1;
                    }

                    int size = lastUse.size();
                    long time = (size > 0 ? 1000 / size : 1000) - (System.currentTimeMillis() - start);
                    if (time <= 0)
                        return 0;
                    return time;
                }
            };
            unloadTicker.start();
        }
    }

    private Supplier<Engine> createSupplier() {
        AtomicInteger i = new AtomicInteger();
        return () -> {
            List<World> worlds = Bukkit.getWorlds();
            if (i.get() >= worlds.size()) {
                i.set(0);
            }
            try {
                for (int j = 0; j < worlds.size(); j++) {
                    World world = worlds.get(i.getAndIncrement());
                    PlatformChunkGenerator generator = IrisToolbelt.access(world);
                    if (i.get() >= worlds.size()) {
                        i.set(0);
                    }

                    if (generator != null) {
                        Engine engine = generator.getEngine();
                        boolean closed = engine.getMantle().getData().isClosed();
                        if (engine != null && !engine.isStudio() && !closed) {
                            lastUseLock.lock();
                            lastUse.put(world, System.currentTimeMillis());
                            lastUseLock.unlock();
                            return engine;
                        }
                    }
                }
            } catch (Throwable e) {
                Iris.info(C.RED + "EngineSVC: Failed to create supplier.");
                e.printStackTrace();
                Iris.reportError(e);
            }
            return null;
        };
    }

    @Override
    public void onDisable() {
        cacheTicker.interrupt();
        trimTicker.interrupt();
        unloadTicker.interrupt();
        lastUse.clear();
    }
}
