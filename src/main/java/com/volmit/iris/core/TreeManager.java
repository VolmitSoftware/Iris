package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.IrisWorlds;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.engine.object.common.IObjectPlacer;
import com.volmit.iris.engine.object.tile.TileData;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import org.bukkit.Location;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
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

        Iris.debug("Sapling grew @ " + event.getLocation() + " for " + event.getSpecies().name() + " usedBoneMeal is " + event.isFromBonemeal());

        // Must be iris world
        if (!IrisWorlds.isIrisWorld(event.getWorld())) {
            return;
        }

        // Get world access
        IrisAccess worldAccess;
        try {
            worldAccess = Objects.requireNonNull(IrisWorlds.access(event.getWorld()));
        } catch (Throwable e) {
            Iris.reportError(e);
            return;
        }

        Iris.debug("Custom saplings are " + (worldAccess.getCompound().getRootDimension().getSaplingSettings().isEnabled() ? "" : "NOT") + " enabled.");

        // Calculate size, type & placement
        IrisTreeType type = IrisTreeType.fromTreeType(event.getSpecies());
        IrisTreeSize size = getTreeSize(event.getLocation(), type);
        IrisObjectPlacement placement = getObjectPlacement(worldAccess, type, event.getLocation(), size);

        // Make sure placement was found
        if (placement == null){
            return;
        }

        // Get object from placer
        IrisObject f = worldAccess.getData().getObjectLoader().load(placement.getPlace().getRandom(RNG.r));

        // TODO: Implement placer
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

        // TODO: Figure out how to place without wrecking claims, other builds, etc.
        // Especially with large object

        // Place the object with the placer
        f.place(
                event.getLocation().getBlockX(),
                event.getLocation().getBlockY(),
                event.getLocation().getBlockZ(),
                placer,
                placement,
                RNG.r,
                Objects.requireNonNull(IrisWorlds.access(event.getWorld())).getData()
        );
    }

    /**
     * Finds the tree size
     * @param location The location the event triggers from. This sapling's Material type is used to check other locations
     * @return The size of the tree
     */
    private IrisTreeSize getTreeSize(Location location, IrisTreeType type) {
        KList<IrisTreeSize> validSizes = new KList<>();

        IrisTreeSize.isSizeValid();

        return IrisTreeSize.bestSize(validSizes);
    }

    /**
     * Finds a single object placement (which may contain more than one object) for the requirements species, location & size
     * @param worldAccess The world to access (check for biome, region, dimension, etc)
     * @param type The bukkit TreeType to match
     * @param location The location of the growth event (For biome/region finding)
     * @param size The size of the sapling area
     * @return An object placement which contains the matched tree, or null if none were found / it's disabled.
     */
    private IrisObjectPlacement getObjectPlacement(IrisAccess worldAccess, IrisTreeType type, Location location, IrisTreeSize size) {
        IrisDimension dimension = worldAccess.getCompound().getRootDimension();

        // Return null if not enabled
        if (!dimension.getSaplingSettings().isEnabled()) {
            return null;
        }

        KList<IrisObjectPlacement> placements = new KList<>();

        // Retrieve objectPlacements of type `species` from biome
        IrisBiome biome = worldAccess.getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        placements.addAll(matchObjectPlacements(biome.getObjects(), size, type));

        // Add more or find any in the region
        if (dimension.getSaplingSettings().getMode().equals(IrisTreeModes.ALL) || placements.isEmpty()){
            IrisRegion region = worldAccess.getCompound().getDefaultEngine().getRegion(location.getBlockX(), location.getBlockZ());
            placements.addAll(matchObjectPlacements(region.getObjects(), size, type));
        }

        // Add more or find any in the dimension
        if (dimension.getSaplingSettings().getMode().equals(IrisTreeModes.ALL) || placements.isEmpty()){
            //TODO: Implement object placement in dimension & here
            //placements.addAll(matchObjectPlacements(dimension.getObjects(), size, type));
        }

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
