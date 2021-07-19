package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.IrisWorlds;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public class TreeManager implements Listener {

    public static final int maxSaplingPlane = 5;

    public TreeManager() {
        Iris.instance.registerListener(this);
        Iris.info("Loading Sapling Manager");
    }

    /**This function does the following
     * <br>1. Is the sapling growing in an Iris world? No -> exit</br>
     * <br>2. Is the sapling overwriting setting on in that dimension? No -> exit</br>
     * <br>3. Check biome for overrides for that sapling type -> Found -> use</br>
     * <br>4. Check region ...</br>
     * <br>5. Check dimension ...</br>
     * <br>6. Exit if none are found</br>
     * @param event Checks the given event for sapling overrides
     */
    @EventHandler
    public void onStructureGrowEvent(StructureGrowEvent event) {

        Iris.debug("Sapling grew @ " + event.getLocation() + " for " + event.getSpecies().name() + " usedBoneMeal is " + event.isFromBonemeal());

        // Must be iris world
        if (!IrisWorlds.isIrisWorld(event.getWorld())) {
            return;
        }

        IrisAccess worldAccess;
        try {
            worldAccess = Objects.requireNonNull(IrisWorlds.access(event.getWorld()));
        } catch (Throwable e) {
            Iris.reportError(e);
            return;
        }
        IrisTreeSettings settings = worldAccess.getCompound().getRootDimension().getSaplingSettings();

        Iris.debug("Custom saplings are enabled: " + (settings.isEnabled() ? "Yes" : "No"));

        // Must have override enabled
        if (!settings.isEnabled()) {
            return;
        }

        KList<String> treeObjects = new KList<>();

        // Get biome and region
        IrisBiome biome = worldAccess.getBiome(event.getLocation().getBlockX(), event.getLocation().getBlockY(), event.getLocation().getBlockZ());
        IrisRegion region = worldAccess.getCompound().getDefaultEngine().getRegion(event.getLocation().getBlockX(), event.getLocation().getBlockZ());

    }
}
