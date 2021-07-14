package com.volmit.iris.scaffold.engine;

import org.bukkit.Chunk;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

public interface EngineWorldManager {
    void close();

    void onEntitySpawn(EntitySpawnEvent e);

    void onTick();

    void onSave();

    void spawnInitialEntities(Chunk chunk);

    void onBlockBreak(BlockBreakEvent e);

    void onBlockPlace(BlockPlaceEvent e);
}
