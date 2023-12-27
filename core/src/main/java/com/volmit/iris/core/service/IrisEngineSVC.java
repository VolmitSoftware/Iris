package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.misc.getHardware;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.Looper;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class IrisEngineSVC implements IrisService {
    private static final AtomicInteger tectonicLimit = new AtomicInteger(30);
    private final ReentrantLock lastUseLock = new ReentrantLock();
    private final KMap<World, Long> lastUse = new KMap<>();
    private Looper cacheTicker;
    private Looper trimTicker;
    private Looper unloadTicker;
    public List<World> corruptedIrisWorlds = new ArrayList<>();

    @Override
    public void onEnable() {
        tectonicLimit.set(2);
        long t = getHardware.getProcessMemory();
        while (t > 200) {
            tectonicLimit.getAndAdd(1);
            t = t - 200;
        }
        this.setup();
        cacheTicker.start();
        trimTicker.start();
        unloadTicker.start();
    }

    public static int getTectonicLimit() {
        return tectonicLimit.get();
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
                        if (now - last > 60000) { // 1 minute
                            lastUse.remove(key);
                        }
                    }
                } finally {
                    lastUseLock.unlock();
                }
                return 1000;
            }
        };
        trimTicker = new Looper() {
            private final Supplier<Engine> supplier = createSupplier();
            @Override
            protected long loop() {
                long start = System.currentTimeMillis();
                try {
                    Engine engine = supplier.get();
                    if (engine != null) {
                        engine.getMantle().trim(tectonicLimit.get() / lastUse.size());
                    }
                } catch (Throwable e) {
                    Iris.reportError(e);
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
                        if (engine != null) {
                            lastUseLock.lock();
                            lastUse.put(world, System.currentTimeMillis());
                            lastUseLock.unlock();
                            return engine;
                        }
                    }
                }
            } catch (Throwable e) {
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
