package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.TectonicPlate;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.misc.getHardware;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.HyperLock;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.Looper;
import io.papermc.lib.PaperLib;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.volmit.iris.util.mantle.Mantle.tectonicLimit;

public class IrisEngineSVC implements IrisService {
    private JavaPlugin plugin;
    public Looper ticker;
    public List<World> IrisWorlds = new ArrayList<>();
    public List<World> corruptedIrisWorlds = new ArrayList<>();


    @Override
    public void onEnable() {
        this.plugin = Iris.instance;
        this.IrisStartup();
        this.IrisEngine();
        ticker.start();
    }

    public void IrisEngine(){
        ticker = new Looper() {
            @Override
            protected long loop() {
                try {
                    for (World world : IrisWorlds){
                        Engine engine = IrisToolbelt.access(world).getEngine();


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
    public void IrisStartup(){
        tectonicLimit.set(2);
        long t = getHardware.getProcessMemory();
        for (; t > 250; ) {
            tectonicLimit.getAndAdd(1);
            t = t - 250;
        }
        tectonicLimit.set(10); // DEBUG CODE

        for (World w : Bukkit.getServer().getWorlds()) {
            File container = Bukkit.getWorldContainer();
            Bukkit.getWorldContainer();
            if(IrisToolbelt.access(w) != null){
                IrisWorlds.add(w);
            } else {
                File worldDirectory = new File(container, w.getName());
                File IrisWorldTest = new File(worldDirectory, "Iris");
                if (IrisWorldTest.exists()){
                    if(IrisToolbelt.access(w) == null){
                        corruptedIrisWorlds.add(w);
                    }
                }
            }
        }
    }

    @Override
    public void onDisable() {
        ticker.interrupt();

    }
}

