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
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static com.volmit.iris.util.mantle.Mantle.tectonicLimit;

public class IrisEngineSVC implements IrisService {
    private JavaPlugin plugin;
    public Looper ticker1;
    public Looper ticker2;
    public Looper engineTicker;
    public World selectedWorld;
    public List<World> IrisWorlds = new ArrayList<>();
    public List<World> corruptedIrisWorlds = new ArrayList<>();

    // todo make this work with multiple worlds

    @Override
    public void onEnable() {
        this.plugin = Iris.instance;
        tectonicLimit.set(2);
        long t = getHardware.getProcessMemory();
        for (; t > 250; ) {
            tectonicLimit.getAndAdd(1);
            t = t - 250;
        }
        tectonicLimit.set(10); // DEBUG CODE
        this.IrisEngine();
        engineTicker.start();
        ticker1.start();
        ticker2.start();
    }

    private final AtomicReference<World> selectedWorldRef = new AtomicReference<>();

    public CompletableFuture<World> initializeAsync() {
        return CompletableFuture.supplyAsync(() -> {
            World selectedWorld = null;
            while (selectedWorld == null) {
                synchronized (this) {
                    IrisWorlds.clear();
                    for (World w : Bukkit.getServer().getWorlds()) {
                        if (IrisToolbelt.access(w) != null) {
                            IrisWorlds.add(w);
                        }
                    }
                    if (!IrisWorlds.isEmpty()) {
                        Random rand = new Random();
                        int randomIndex = rand.nextInt(IrisWorlds.size());
                        selectedWorld = IrisWorlds.get(randomIndex);
                    }
                }
                if (selectedWorld == null) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
            return selectedWorld;
        });
    }

    public void IrisEngine() {
        engineTicker = new Looper() {
            @Override
            protected long loop() {
                try {
                    World world = selectedWorldRef.get();
                    PlatformChunkGenerator generator = IrisToolbelt.access(world);
                    if (generator == null) {
                        initializeAsync().thenAcceptAsync(foundWorld -> selectedWorldRef.set(foundWorld));
                    } else {
                        selectedWorld = world;
                    }
                    selectedWorld = Bukkit.getWorld("localmemtest"); // debug code
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                    return -1;
                }

                return 1000;
            }

        };
        ticker1 = new Looper() {
            @Override
            protected long loop() {
                try {
                    World world = selectedWorld;
                    PlatformChunkGenerator generator = IrisToolbelt.access(world);
                    if (generator != null) {
                        Engine engine = IrisToolbelt.access(world).getEngine();
                        if (generator != null && generator.getEngine() != null) {
                            engine.getMantle().trim();
                        } else {
                            Iris.info("something is null 1");
                        }

                    }
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                    return -1;
                }

                return 1000;
            }
        };

        ticker2 = new Looper() {
            @Override
            protected long loop() {
                try {
                    World world = selectedWorld;
                    PlatformChunkGenerator generator = IrisToolbelt.access(world);
                    if (generator != null) {
                        Engine engine = IrisToolbelt.access(world).getEngine();
                        if (generator != null && generator.getEngine() != null) {
                            engine.getMantle().unloadTectonicPlate();
                        } else {
                            Iris.info("something is null 2");
                        }
                    }
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                    return -1;
                }
                return 1000;
            }
        };
    }

    @Override
    public void onDisable() {
        ticker1.interrupt();
        ticker2.interrupt();
        engineTicker.interrupt();
    }
}

