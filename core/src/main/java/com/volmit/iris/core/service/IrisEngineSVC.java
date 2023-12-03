package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisWorld;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.misc.getHardware;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.Looper;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import static com.volmit.iris.util.mantle.Mantle.tectonicLimit;

public class IrisEngineSVC implements IrisService {
    private JavaPlugin plugin;
    public Looper ticker;
    public Mantle mantle;
    public final World IrisWorld = Bukkit.getWorld("test");
   // public Engine engine = IrisToolbelt.access(IrisWorld).getEngine();

    @Override
    public void onEnable() {
        this.plugin = Iris.instance;
        if (IrisSettings.get().getPerformance().dynamicPerformanceMode) {
            this.startupPerformance();
            this.IrisEngine();
            ticker.start();
        }
    }

    public void IrisEngine(){
        ticker = new Looper() {
            @Override
            protected long loop() {
                try {

                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                    return -1;
                }

                return 1000;
            }
        };
    }
    public void startupPerformance(){
        if (IrisSettings.get().getPerformance().dynamicPerformanceMode) {
            tectonicLimit.set(2);
            long t = getHardware.getProcessMemory();
            for (; t > 250; ) {
                tectonicLimit.getAndAdd(1);
                t = t - 250;
            }
             tectonicLimit.set(10);
        }
    }
    public void getAllIrisWorlds(){

    }

    @Override
    public void onDisable() {
        ticker.interrupt();

    }
}

