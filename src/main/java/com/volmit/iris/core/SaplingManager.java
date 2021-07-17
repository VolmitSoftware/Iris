package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.IrisWorldManager;
import com.volmit.iris.engine.IrisWorlds;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
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
        Iris.info("Loading Sapling Manager");
    }

    /**
     * This function does the following:
     * 1. Is the sapling growing in an Iris world? No -> exit
     * 2. Is the sapling overwriting setting on in that dimension? No -> exit
     * 3. Check biome for overrides for that sapling type -> Found -> use
     * 4. Check region ...
     * 5. Check dimension ...
     * 6. Exit if none are found
     * @param event Checks the given event for sapling overrides
     */
    @EventHandler
    public void onStructureGrowEvent(StructureGrowEvent event) {

        // TODO: Remove this line
        Iris.info("Sapling grew @ " + event.getLocation() + " for " + event.getSpecies().name() + " bonemealed is " + event.isFromBonemeal());


        // TODO: Remove if statement here once Iris worlds are creatable again
        boolean debug = true;

        if (!debug) {
            // Must be iris world
            if (!IrisWorlds.isIrisWorld(event.getWorld())) return;

            IrisAccess worldAccess;
            try {
                worldAccess = Objects.requireNonNull(IrisWorlds.access(event.getWorld()));
            } catch (Throwable e) {
                Iris.reportError(e);
                return;
            }

            IrisDimension dim = worldAccess.getCompound().getRootDimension();
        }

        // TODO: Remove this line
        IrisDimension dimension = IrisDataManager.loadAnyDimension("overworld");


        // Must have override enabled
        if (!dimension.isOverrideSaplings()) return;

        // TODO: Remove this line
        Iris.info("Should replace sapling now!");

        IrisAccess worldAccess = IrisWorlds.access(event.getWorld());
        assert worldAccess != null;
        KList<String> replace = null;

        // Check biome
        IrisBiome biome = worldAccess.getBiome(event.getLocation().getBlockX(), event.getLocation().getBlockZ());
        for (IrisSapling sapling : biome.getSaplings()){
            for (TreeType type : sapling.getTypes()){
                if (type == event.getSpecies()){
                    replace = sapling.getReplace();
                    // If we decide to do some sort of addition (biome + region + dim for options) we can do that here
                }
            }
        }

        // Check region
        if (replace == null) {
            IrisRegion region = worldAccess.getCompound().getDefaultEngine().getRegion(event.getLocation().getBlockX(), event.getLocation().getBlockZ());
            for (IrisSapling sapling : region.getSaplings()) {
                for (TreeType type : sapling.getTypes()) {
                    if (type == event.getSpecies()) {
                        replace = sapling.getReplace();
                        // If we decide to do some sort of addition (biome + region + dim for options) we can do that here
                    }
                }
            }
        }

        // Check dimension
        if (replace == null) {
            for (IrisSapling sapling : dimension.getSaplings()) {
                for (TreeType type : sapling.getTypes()) {
                    if (type == event.getSpecies()) {
                        replace = sapling.getReplace();
                        // If we decide to do some sort of addition (biome + region + dim for options) we can do that here
                    }
                }
            }
        }

        // Check to make sure something was found
        if (replace == null || replace.size() == 0) return;

        // Pick a random object from the list of objects found
        String object = replace.get(RNG.r.i(0, replace.size() - 1));

        // Cancel vanilla event
        event.setCancelled(true);

        // Retrieve & place the object
        // TODO: Make this specific for this pack
        Iris.info("Placing tree object instead of vanilla tree: " + object);
        IrisObject obj = IrisDataManager.loadAnyObject(object);
        obj.place(event.getLocation());
    }
}
