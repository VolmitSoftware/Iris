package com.volmit.iris.core;

import com.volmit.iris.Iris;
import org.bukkit.TreeType;
import org.bukkit.block.data.type.Sapling;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.Objects;

public class SaplingManager implements Listener {

    public SaplingManager() {
        Iris.instance.registerListener(this);
    }

    @EventHandler
    public void onStructureGrowEvent(StructureGrowEvent event) {
        if (event.getSpecies() == TreeType.JUNGLE)
        Iris.info("Sapling grew @ " + event.getLocation() + " for " + event.getSpecies().name() + " bonemealed is " + event.isFromBonemeal() + " by player " + Objects.requireNonNull(event.getPlayer()).getName());
    }

    @EventHandler
    public void onBlockPlaceEvent(BlockPlaceEvent event) {
        Iris.info("Placed " + event.getBlock().getBlockData().getMaterial().name() + " @ " + event.getBlock().getLocation());
    }
}
