package com.volmit.iris.scaffold.engine;

import org.bukkit.Chunk;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

public interface EngineWorldManager
{
    public void close();

    public void onEntitySpawn(EntitySpawnEvent e);

    public void onTick();

    public void onSave();

    public void spawnInitialEntities(Chunk chunk);

    public void onBlockBreak(BlockBreakEvent e);

    public void onBlockPlace(BlockPlaceEvent e);
}
