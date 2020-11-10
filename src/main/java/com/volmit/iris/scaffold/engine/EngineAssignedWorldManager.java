package com.volmit.iris.scaffold.engine;

import com.volmit.iris.Iris;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public abstract class EngineAssignedWorldManager extends EngineAssignedComponent implements EngineWorldManager, Listener {
    private final int taskId;

    public EngineAssignedWorldManager(Engine engine) {
        super(engine, "World");
        Iris.instance.registerListener(this);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, this::onTick, 0, 0);
    }

    @EventHandler
    public void on(WorldSaveEvent e)
    {
        if(e.getWorld().equals(getTarget().getWorld()))
        {
            onSave();
        }
    }

    @EventHandler
    public void on(WorldUnloadEvent e)
    {
        if(e.getWorld().equals(getTarget().getWorld()))
        {
            getEngine().close();
        }
    }

    @EventHandler
    public void on(EntitySpawnEvent e)
    {
        if(e.getEntity().getWorld().equals(getTarget().getWorld()))
        {
            onEntitySpawn(e);
        }
    }

    @EventHandler
    public void on(BlockBreakEvent e)
    {
        if(e.getPlayer().getWorld().equals(getTarget().getWorld()))
        {
            onBlockBreak(e);
        }
    }

    @EventHandler
    public void on(BlockPlaceEvent e)
    {
        if(e.getPlayer().getWorld().equals(getTarget().getWorld()))
        {
            onBlockPlace(e);
        }
    }

    @Override
    public void close() {
        super.close();
        Iris.instance.unregisterListener(this);
        Bukkit.getScheduler().cancelTask(taskId);
    }
}
