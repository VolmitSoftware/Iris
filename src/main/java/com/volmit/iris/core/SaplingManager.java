package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.IrisWorldManager;
import com.volmit.iris.engine.IrisWorlds;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisDimension;
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

        // Must be iris world
        if (!IrisWorlds.isIrisWorld(event.getWorld())) return;

        IrisAccess worldAccess;
        try {
            worldAccess = Objects.requireNonNull(IrisWorlds.access(event.getWorld()));
        } catch (Throwable e){
            Iris.reportError(e);
            return;
        }
        IrisDimension dim = worldAccess.getCompound().getDefaultEngine().getDimension();

        // Must have override enabled
        if (!dim.isOverrideSaplings()) return;

        // TODO: Remove this line
        Iris.info("Sapling grew @ " + event.getLocation() + " for " + event.getSpecies().name() + " bonemealed is " + event.isFromBonemeal() + " by player " + Objects.requireNonNull(event.getPlayer()).getName());

        Iris.info("Should replace sapling now!");
    }
}
