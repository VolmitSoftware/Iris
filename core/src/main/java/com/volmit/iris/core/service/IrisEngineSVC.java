package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.misc.getHardware;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Looper;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.volmit.iris.util.mantle.Mantle.tectonicLimit;

public class IrisEngineSVC implements IrisService {
    public Looper trimTicker;
    public Looper unloadTicker;
    public List<World> corruptedIrisWorlds = new ArrayList<>();

    // todo make this work with multiple worlds

    @Override
    public void onEnable() {
        tectonicLimit.set(2);
        long t = getHardware.getProcessMemory();
        while (t > 250) {
            tectonicLimit.getAndAdd(1);
            t = t - 250;
        }
        tectonicLimit.set(10); // DEBUG CODE
        this.setup();
        trimTicker.start();
        unloadTicker.start();
    }

    private void setup() {
        trimTicker = new Looper() {
            private final Supplier<Engine> supplier = createSupplier();
            private Engine engine = supplier.get();

            @Override
            protected long loop() {
                try {
                    if (engine != null) {
                        engine.getMantle().trim();
                    }
                    engine = supplier.get();
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                    return -1;
                }

                return 1000;
            }
        };

        unloadTicker = new Looper() {
            private final Supplier<Engine> supplier = createSupplier();
            private Engine engine = supplier.get();

            @Override
            protected long loop() {
                try {
                    if (engine != null) {
                        engine.getMantle().unloadTectonicPlate();
                    }
                    engine = supplier.get();
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                    return -1;
                }
                return 1000;
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
            for (int j = 0; j < worlds.size(); j++) {
                PlatformChunkGenerator generator = IrisToolbelt.access(worlds.get(i.getAndIncrement()));
                if (i.get() >= worlds.size()) {
                    i.set(0);
                }

                if (generator != null && generator.getEngine() != null) {
                    return generator.getEngine();
                }
            }
            return null;
        };
    }

    @Override
    public void onDisable() {
        trimTicker.interrupt();
        unloadTicker.interrupt();
    }
}
