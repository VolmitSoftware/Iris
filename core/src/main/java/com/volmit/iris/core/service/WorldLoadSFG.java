package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.util.plugin.IrisService;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import static java.lang.System.getLogger;

public class WorldLoadSFG implements IrisService {
    private JavaPlugin plugin;
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();

    }

    @Override
    public void onEnable() {
        this.plugin = Iris.instance;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {

    }
}
