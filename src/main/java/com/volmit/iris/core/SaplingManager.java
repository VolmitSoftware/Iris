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
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class SaplingManager implements Listener {

    private static final boolean debugMe = false;

    public SaplingManager() {
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

        if (debugMe)
            Iris.info("Sapling grew @ " + event.getLocation() + " for " + event.getSpecies().name() + " usedBoneMeal is " + event.isFromBonemeal());

        // Must be iris world
        if (!IrisWorlds.isIrisWorld(event.getWorld())) return;

        IrisAccess worldAccess;
        try {
            worldAccess = Objects.requireNonNull(IrisWorlds.access(event.getWorld()));
        } catch (Throwable e) {
            Iris.reportError(e);
            return;
        }
        IrisTreeSettings settings = worldAccess.getCompound().getRootDimension().getSaplingSettings();

        if (debugMe) Iris.info("Custom saplings are enabled: " + (settings.isEnabled() ? "Yes" : "No"));

        // Must have override enabled
        if (!settings.isEnabled()) return;

        KList<String> treeObjects = new KList<>();

        // Get biome and region
        IrisBiome biome = worldAccess.getBiome(event.getLocation().getBlockX(), event.getLocation().getBlockY(), event.getLocation().getBlockZ());
        IrisRegion region = worldAccess.getCompound().getDefaultEngine().getRegion(event.getLocation().getBlockX(), event.getLocation().getBlockZ());

        if (debugMe)
            Iris.info("Biome name: " + biome.getName() + " | List of saplings: " + biome.getSaplings().toString());
        if (debugMe)
            Iris.info("Region name: " + region.getName() + " | List of saplings: " + region.getSaplings().toString());
        if (debugMe)
            Iris.info("Dimension saplings: " + settings.getSaplings().toString());

        int saplingSize = getSaplingSize(event.getLocation());

        // Check biome, region and dimension
        treeObjects.addAll(getSaplingsFrom(biome.getSaplings(), event.getSpecies(), saplingSize));

        // Check region
        if (settings.getMode() == IrisTreeModes.ALL || treeObjects.size() == 0)
            treeObjects.addAll(getSaplingsFrom(region.getSaplings(), event.getSpecies(), saplingSize));

        // Check dimension
        if (settings.getMode() == IrisTreeModes.ALL || treeObjects.size() == 0)
            treeObjects.addAll(getSaplingsFrom(settings.getSaplings(), event.getSpecies(), saplingSize));

        if (debugMe) Iris.info("List of saplings (together): " + treeObjects);

        // Check to make sure something was found
        if (treeObjects.size() == 0) return;

        // Pick a random object from the list of objects found
        String pickedObjectString = treeObjects.get(RNG.r.i(0, treeObjects.size() - 1));

        // Cancel vanilla event
        event.setCancelled(true);

        // Retrieve & place the object
        if (debugMe) Iris.info("Placing tree object instead of vanilla tree: " + pickedObjectString);
        IrisObject pickedObject = IrisDataManager.loadAnyObject(pickedObjectString);

        // Delete the saplings (some objects may not have blocks where the sapling is)
        // TODO: Rewrite this to delete the saplings that matter
        event.getBlocks().forEach(b -> b.setType(Material.AIR));

        // Rotate and place the object
        pickedObject.rotate(new IrisObjectRotation(), 0, 90 * RNG.r.i(0, 3), 0);
        pickedObject.place(event.getLocation());
    }

    /**
     * Find all sapling types of the given TreeType in the container
     * @param container Iris sapling config
     * @param tree The tree type to find
     * @param size The `size * size` area of the saplings
     * @return A list of found object name strings
     */
    @NotNull
    private KList<String> getSaplingsFrom(KList<IrisTree> container, TreeType tree, int size) {

        // Translate TreeType to Iris TreeType
        IrisTreeType eventTreeType = IrisTreeType.fromTreeType(tree);

        KList<String> objects = new KList<>();

        // Loop over all saplings in the container
        // and their entered sapling types
        // and copy the trees in the list if matching.
        for (IrisTree sapling : container) {
            for (IrisTreeType configTreeType : sapling.getTreeTypes()) {
                if (configTreeType == eventTreeType && size == sapling.getSize()) {
                    objects.addAll(sapling.getObjects());
                    if (debugMe) Iris.info("Added replacements: " + sapling.getObjects().toString());
                }
            }
        }
        return objects;
    }

    /**
     * Retrieve the `size * size` area of a sapling (any sapling in the area)
     * @param location The location to start the search from
     * @return The `x * x` area of saplings
     */
    private int getSaplingSize(Location location){
        // TODO: Write this
        return 1;
    }
}
