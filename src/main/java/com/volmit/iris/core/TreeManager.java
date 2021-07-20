package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.IrisWorlds;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.math.RNG;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.StructureGrowEvent;

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

        Iris.debug(this.getClass().getName() + " received a structure grow event");

        // Must be iris world
        if (!IrisWorlds.isIrisWorld(event.getWorld())) {
            Iris.debug(this.getClass().getName() + " passed it off to vanilla since not an Iris world");
            return;
        }

        // Get world access
        IrisAccess worldAccess;
        try {
            worldAccess = Objects.requireNonNull(IrisWorlds.access(event.getWorld()));
        } catch (Throwable e) {
            Iris.debug(this.getClass().getName() + " passed it off to vanilla because could not get IrisAccess for this world");
            Iris.reportError(e);
            return;
        }

        // Return null if not enabled
        if (!worldAccess.getCompound().getRootDimension().getSaplingSettings().isEnabled()) {
            Iris.debug(this.getClass().getName() + "cancelled because not");
            return;
        }

        Iris.debug("Sapling grew @ " + event.getLocation() + " for " + event.getSpecies().name() + " usedBoneMeal is " + event.isFromBonemeal());

        // Calculate size, type & placement
        IrisTreeType type = IrisTreeType.fromTreeType(event.getSpecies());
        KMap<IrisTreeSize, KList<KList<Location>>> sizes = IrisTreeSize.getValidSizes(event.getLocation());
        KList<IrisTreeSize> keys = sizes.k();

        // Find best object placement based on size
        IrisObjectPlacement placement = null;
        while (placement == null && keys.isNotEmpty()){
            IrisTreeSize bestSize = IrisTreeSize.bestSizeInSizes(keys);
            keys.remove(bestSize);
            placement = getObjectPlacement(worldAccess, event.getLocation(), type, bestSize);
        }

        // If none was found, just exit
        if (placement == null) {
            return;
        }

        // Cancel the placement
        event.setCancelled(true);

        // Get object from placer
        IrisObject f = worldAccess.getData().getObjectLoader().load(placement.getPlace().getRandom(RNG.r));

        // TODO: Implement placer
        /*
        IObjectPlacer placer = new IObjectPlacer(){

            @Override
            public int getHighest(int x, int z) {
                return 0;
            }

            @Override
            public int getHighest(int x, int z, boolean ignoreFluid) {
                return 0;
            }

            @Override
            public void set(int x, int y, int z, BlockData d) {

            }

            @Override
            public BlockData get(int x, int y, int z) {
                return null;
            }

            @Override
            public boolean isPreventingDecay() {
                return false;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isUnderwater(int x, int z) {
                return false;
            }

            @Override
            public int getFluidHeight() {
                return 0;
            }

            @Override
            public boolean isDebugSmartBore() {
                return false;
            }

            @Override
            public void setTile(int xx, int yy, int zz, TileData<? extends TileState> tile) {

            }
        };
        */

        // TODO: Figure out how to place without wrecking claims, other builds, etc.
        // Especially with large object

        // Place the object with the placer
        /*
        f.place(
                event.getLocation().getBlockX(),
                event.getLocation().getBlockY(),
                event.getLocation().getBlockZ(),
                placer,
                placement,
                RNG.r,
                Objects.requireNonNull(IrisWorlds.access(event.getWorld())).getData()
        );
        */
        // TODO: Place the object at the right location (one of the center positions)
        f.place(event.getLocation());
    }

    /**
     * Finds a single object placement (which may contain more than one object) for the requirements species, location & size
     * @param worldAccess The world to access (check for biome, region, dimension, etc)
     * @param location The location of the growth event (For biome/region finding)
     * @param type The bukkit TreeType to match
     * @param size The size of the sapling area
     * @return An object placement which contains the matched tree, or null if none were found / it's disabled.
     */
    private IrisObjectPlacement getObjectPlacement(IrisAccess worldAccess, Location location, IrisTreeType type, IrisTreeSize size) {

        KList<IrisObjectPlacement> placements = new KList<>();

        // Retrieve objectPlacements of type `species` from biome
        IrisBiome biome = worldAccess.getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        placements.addAll(matchObjectPlacements(biome.getObjects(), size, type));

        // Add more or find any in the region
        if (worldAccess.getCompound().getRootDimension().getSaplingSettings().getMode().equals(IrisTreeModes.ALL) || placements.isEmpty()){
            IrisRegion region = worldAccess.getCompound().getDefaultEngine().getRegion(location.getBlockX(), location.getBlockZ());
            placements.addAll(matchObjectPlacements(region.getObjects(), size, type));
        }

        // Add more or find any in the dimension
        /* TODO: Implement object placement in dimension & here
        if (worldAccess.getCompound().getRootDimension().getSaplingSettings().getMode().equals(IrisTreeModes.ALL) || placements.isEmpty()){
            placements.addAll(matchObjectPlacements(worldAccess.getCompound().getRootDimension().getObjects(), size, type));
        }
         */

        // Check if no matches were found, return a random one if they are
        return placements.isNotEmpty() ? placements.getRandom(RNG.r) : null;
    }

    /**
     * Filters out mismatches and returns matches
     * @param objects The object placements to check
     * @param size The size of the sapling area to filter with
     * @param type The type of the tree to filter with
     * @return A list of objectPlacements that matched. May be empty.
     */
    private KList<IrisObjectPlacement> matchObjectPlacements(KList<IrisObjectPlacement> objects, IrisTreeSize size, IrisTreeType type) {
        KList<IrisObjectPlacement> objectPlacements = new KList<>();
        objects.stream()
                .filter(objectPlacement -> objectPlacement.getTreeOptions().isEnabled())
                .filter(objectPlacement -> objectPlacement.getTreeOptions().getTrees().stream().anyMatch(irisTree ->
                        irisTree.getSizes().stream().anyMatch(treeSize -> treeSize == size) &&
                        irisTree.getTreeTypes().stream().anyMatch(treeType -> treeType == type)))
                .forEach(objectPlacements::add);
        return objectPlacements;
    }
}
