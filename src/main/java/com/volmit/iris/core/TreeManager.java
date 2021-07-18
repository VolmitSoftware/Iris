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

    private static final boolean debugMe = false;

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

        // Get sapling location
        KList<Location> saplingLocations = getSaplingPlane(event.getLocation());
        int saplingSize = getSaplingSize(saplingLocations);

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
        deleteSaplings(saplingLocations);

        // Rotate the object randomly
        pickedObject.rotate(new IrisObjectRotation(), 0, 90 * RNG.r.i(0, 3), 0);

        // TODO: Consider adding a translation to object placement.
        // Place the object
        pickedObject.place(event.getLocation());
    }

    /**
     * Deletes all saplings at the
     * @param locations sapling locations
     */
    private void deleteSaplings(KList<Location> locations) {
        locations.forEach(l -> {
            if (debugMe) Iris.info("Deleting block of type: " + l.getBlock().getType());
            l.getBlock().setType(Material.AIR);
        });
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
                }
            }
        }
        return objects;
    }

    /**
     * Retrieve the `size * size` area of a sapling (any sapling in the area)
     * @param saplings The locations of the saplings in the plane
     * @return The `x * x` area of saplings
     */
    private int getSaplingSize(KList<Location> saplings){
        double size = Math.sqrt(saplings.size());
        if (size % 1 != 0) {
            Iris.error("Size of sapling square array is not a power of an integer (not a square)");
            return -1;
        }
        return (int) size;
    }

    /**
     * Retrieve all saplings in a square area around the current sapling.
     * This searches around the current sapling, and the next, etc, iteratively
     * Note: This is limited by maxSaplingPlane
     * @param location The location to search from (the originating sapling)
     * @return A list of saplings in a square
     */
    private KList<Location> getSaplingPlane(Location location){
        KList<Location> locations = new KList<>();

        // TODO: Debug getBlockMap
        boolean[][] map = getBlockMap(location);

        for (boolean[] row : map)
            Iris.info(Arrays.toString(row));

        // TODO: Write logic here that checks for the largest possible square with the Location included
        // TODO: The boolean[][] map has true's where there's another sapling of the same type, and false if not.
        // TODO: Fill the locations array with the found sapling locations and return the array. The rest is hopefully done.
        // Note: I tested the system. Placing objects works. Removing saplings may not work perfectly.

        return locations;
    }

    /**
     * Get a boolean map which indicates if at positions in all directions the same block can be found
     * @param location the center location around which we search
     * @return A boolean 2d map of trues and false's
     */
    private boolean[][] getBlockMap(Location location) {
        Material blockMaterial = location.getBlock().getType();
        int size = maxSaplingPlane * 2 - 1;
        boolean[][] map = new boolean[size][size];

        for (int i = 0; i < size; i++){
            boolean[] row = new boolean[size];
            for (int j = 0; j < size; j++){
                Vector zdir = new Vector(0, 0, 1).multiply(i - maxSaplingPlane + 1);
                Vector xdir = new Vector(1, 0, 0).multiply(j - maxSaplingPlane + 1);
                Material foundBlock = location.add(xdir).add(zdir).getBlock().getType();
                row[j] = blockMaterial.name().equals(foundBlock.name());
            }
            map[i] = row;
        }
        return map;
    }
}
