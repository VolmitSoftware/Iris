package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
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
    private static final AtomicInteger tectonicLimit = new AtomicInteger(30);
    private final ReentrantLock lastUseLock = new ReentrantLock();
    private final KMap<World, Long> lastUse = new KMap<>();
    private List<World> IrisWorlds;
    private Looper cacheTicker;
    private Looper freezeTicker;
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
        trimAlive.begin();
        unloadAlive.begin();
        trimActiveAlive.begin();
        unloadActiveAlive.begin();

        updateTicker.start();
        cacheTicker.start();
        trimTicker.start();
        unloadTicker.start();
        freezeTicker.start();
        instance = this;

    }

    public static int getTectonicLimit() {
        return tectonicLimit.get();
    }

    public void EngineReport() {
        Iris.info(C.RED + "CRITICAL ENGINE FAILURE! The Tectonic Trim subsystem has not responded for: " + Form.duration(trimAlive.getMillis()) + ".");
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        updateWorlds();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        updateWorlds();
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
                        Engine engine = IrisToolbelt.access(world).getEngine();
                        TotalQueuedTectonicPlates.addAndGet((int) engine.getMantle().getToUnload());
                        TotalNotQueuedTectonicPlates.addAndGet((int) engine.getMantle().getNotQueuedLoadedRegions());
                        TotalTectonicPlates.addAndGet(engine.getMantle().getLoadedRegionCount());
                    }
                } catch (Exception e) {
                    return -1;
                }
                return 1000;
            }
        };

        freezeTicker = new Looper() {
            @Override
            protected long loop() {
                if (cl.flip()) {
                    if (!unloadTicker.isAlive()) {
                        Iris.debug(C.YELLOW + "UnloadTicker Found dead?");
                    }
                    if (!trimTicker.isAlive()) {
                        Iris.debug(C.YELLOW + "UnloadTicker Found dead?");
                    }
                    Runtime runtime = Runtime.getRuntime();
                    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                    long maxMemory = runtime.maxMemory();
                    double memoryUsagePercentage = ((double) usedMemory / (double) maxMemory);
                    double externalTrim = trimActiveAlive.getMillis();
                    double externalUnload = unloadActiveAlive.getMillis();
                    double localTrim = trimAlive.getMillis();
                    double localUnload = unloadAlive.getMillis();
                    if (localTrim > 120000) {
                        Iris.info(C.YELLOW + "EngineSVC Alert! The Trim subsystem has exceeded its expected response time: " + Form.duration(trimAlive.getMillis()) + ".");
                    }
                    if (localUnload > 120000) {
                        Iris.info(C.YELLOW + "EngineSVC Alert! The Tectonic subsystem has exceeded its expected response time: " + Form.duration(trimAlive.getMillis()) + ".");
                    }

                    if (memoryUsagePercentage > 0.9 && tectonicLimit.get() < TotalTectonicPlates.get()) {
                        if (localTrim > 30000 && externalTrim > 10000) {
                            Iris.info(C.RED + "CRITICAL EngineSVC FAILURE! The Tectonic Trim subsystem has not trimmed for: " + Form.duration(trimAlive.getMillis()) + ".");
                            IsTrimAlive.set(false);
                        } else {
                            Iris.info(C.IRIS + "EngineSVC reports activity within the Trim subsystem system!");
                            IsTrimAlive.set(true);
                        }

                        if (localUnload > 30000 && externalUnload > 12000 && TotalQueuedTectonicPlates.get() != 0) {
                            Iris.info(C.RED + "CRITICAL EngineSVC FAILURE! The Tectonic Unload subsystem has not unloaded for: " + Form.duration(trimAlive.getMillis()) + ".");
                            IsUnloadAlive.set(false);
                        } else {
                            Iris.info(C.IRIS + "EngineSVC reports activity within the Unload subsystem system!");
                            IsUnloadAlive.set(true);
                        }
                    }
                }
                return 1;
            }

        };

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
                    Iris.info(C.RED + "EngineSVC: Failed to trim. Please contact support!");
                    e.printStackTrace();
                    return -1;
                }

                int size = lastUse.size();
                long time = (size > 0 ? 1000/size : 1000) - (System.currentTimeMillis() - start);
                if (time <= 0)
                    return 0;
                return time;
            }
        };

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
                            Iris.debug(C.GOLD + "Unloaded " +  C.YELLOW + count + " TectonicPlates in " + C.RED + Form.duration(System.currentTimeMillis() - unloadStart, 2));
                        }
                    }
                } catch (Throwable e) {
                    Iris.reportError(e);
                    Iris.info(C.RED + "EngineSVC: Failed to unload. Please contact support!");
                    e.printStackTrace();
                    return -1;
                }

                int size = lastUse.size();
                long time = (size > 0 ? 1000/size : 1000) - (System.currentTimeMillis() - start);
                if (time <= 0)
                    return 0;
                return time;
            }
        };
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
                        if (engine != null && !engine.isStudio()) {
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
