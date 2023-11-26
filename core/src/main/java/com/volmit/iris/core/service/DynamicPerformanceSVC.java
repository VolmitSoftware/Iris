package com.volmit.iris.core.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.SFG.WorldHandlerSFG;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.misc.getHardware;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.Looper;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.volmit.iris.util.mantle.Mantle.*;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

public class DynamicPerformanceSVC implements IrisService {
    private JavaPlugin plugin;
    public Looper ticker;
    public Mantle mantle;
    public Engine engine;

    @Override
    public void onEnable() {
        this.plugin = Iris.instance;
        if (IrisSettings.get().getPerformance().dynamicPerformanceMode) {
            this.startupPerformance();
            this.DynamicPerformance();
            ticker.start();
        }
    }

    public void DynamicPerformance(){
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
            // tectonicLimit.set(10);
        }
    }

    @Override
    public void onDisable() {
        ticker.interrupt();

    }
}

