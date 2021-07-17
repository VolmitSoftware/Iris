package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.IrisWorldManager;
import com.volmit.iris.engine.IrisWorlds;
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
        if (!IrisWorlds.isIrisWorld(event.getWorld())) return;

        // TODO: Remove this line
        Iris.info("Sapling grew @ " + event.getLocation() + " for " + event.getSpecies().name() + " bonemealed is " + event.isFromBonemeal() + " by player " + Objects.requireNonNull(event.getPlayer()).getName());

    }
}
