package com.volmit.iris.core.service;

import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.volmit.iris.Iris;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.SFG.WorldHandlerSFG;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.misc.getHardware;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.Looper;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class MemoryLeakSVC extends Looper implements IrisService {
    private WatchService watchService;
    private JavaPlugin plugin;

    @Override
    public void onEnable() {
        //Iris.info("Enabled Mem Leak Detection thing wow it actually worked");
        //this.plugin = Iris.instance;
    }

    @Override
    public void onDisable() {

    }

    @Override
    protected long loop() {
        try {
            if (getHardware.getAvailableProcessMemory() < 50){
                PrintMemoryLeakDetected();
                for (World world : Bukkit.getWorlds()) {
                    if (IrisToolbelt.isIrisWorld(world)){
                        Engine engine = IrisToolbelt.access(world).getEngine();
                        if (engine.getMantle().getLoadedRegionCount() > 0){
                            if (!IrisToolbelt.isIrisWorld(world)) {
                               Iris.info(C.RED + "This is not an Iris world. Iris worlds: " + String.join(", ", Bukkit.getServer().getWorlds().stream().filter(IrisToolbelt::isIrisWorld).map(World::getName).toList()));
                                return -1;
                            }
                            Iris.info(C.GREEN + "Unloading world: " + world.getName());
                            try {
                                IrisToolbelt.evacuate(world);
                                Bukkit.unloadWorld(world, false);
                                Iris.info(C.GREEN + "World unloaded successfully.");
                            } catch (Exception e) {
                                Iris.info(C.RED + "Failed to unload the world: " + e.getMessage());
                                e.printStackTrace();
                            }

                        }
                    }
                }
            }
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
            return -1;
        }

        return 1000;
    }
    public void PrintMemoryLeakDetected(){
        Iris.info(C.DARK_RED + "--==< MEMORY LEAK DETECTED >==--");
    }
}

