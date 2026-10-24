/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.service;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.core.modules.PlatformBlockStates;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineLifecycleTasks;
import art.arcane.iris.engine.framework.TreeBlockMaterial;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.data.Cuboid;
import com.volmit.placement.PlacedBlock;
import com.volmit.service.SaplingGrowth;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.iris.util.common.plugin.IrisService;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Sapling;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public class TreeSVC implements IrisService {
    // Re-entrancy guard for the synthetic StructureGrowEvent this service fires from inside
    // its own handler. callEvent dispatches inline on the calling thread, so a ThreadLocal
    // is exact; a shared boolean let one region's growth suppress another's on Folia.
    private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }

    /**
     * This function does the following
     * <br>1. Is the sapling growing in an Iris world? No -> exit</br>
     * <br>2. Is the Iris world accessible? No -> exit</br>
     * <br>3. Is the sapling overwriting setting on in that dimension? No -> exit</br>
     * <br>4. Check biome, region and dimension for overrides for that sapling type -> Found -> use</br>
     * <br>5. Exit if none are found, cancel event if one or more are.</br>
     *
     * @param event Checks the given event for sapling overrides
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(StructureGrowEvent event) {
        if (REENTRANT.get() || event.isCancelled()) {
            return;
        }
        IrisLogging.debug(this.getClass().getName() + " received a structure grow event");

        if (!IrisToolbelt.isIrisWorld(event.getWorld())) {
            IrisLogging.debug(this.getClass().getName() + " passed grow event off to vanilla since not an Iris world");
            return;
        }

        PlatformChunkGenerator worldAccess = IrisToolbelt.access(event.getWorld());
        if (worldAccess == null) {
            IrisLogging.debug(this.getClass().getName() + " passed it off to vanilla because could not get IrisAccess for this world");
            IrisLogging.reportError(new NullPointerException(event.getWorld().getName() + " could not be accessed despite being an Iris world"));
            return;
        }

        Engine engine = worldAccess.getEngine();

        if (engine == null) {
            IrisLogging.debug(this.getClass().getName() + " passed it off to vanilla because could not get Engine for this world");
            IrisLogging.reportError(new NullPointerException(event.getWorld().getName() + " could not be accessed despite being an Iris world"));
            return;
        }

        if (engine.getDimension() == null) {
            IrisLogging.debug(this.getClass().getName() + " passed it off to vanilla because could not get Dimension for this world");
            IrisLogging.reportError(new NullPointerException(event.getWorld().getName() + " could not be accessed despite being an Iris world"));
            return;
        }

        BlockData first = event.getLocation().getBlock().getBlockData().clone();
        Cuboid saplingPlane = getSaplings(event.getLocation(), blockData -> blockData instanceof Sapling && blockData.getMaterial().equals(first.getMaterial()), event.getWorld());

        IrisLogging.debug("Sapling grew @ " + event.getLocation() + " for " + event.getSpecies().name() + " usedBoneMeal is " + event.isFromBonemeal());
        IrisLogging.debug("Sapling plane is: " + saplingPlane.getSizeX() + " by " + saplingPlane.getSizeZ());
        int minHeight = event.getWorld().getMinHeight();
        SaplingGrowth.Sapling sapling = new SaplingGrowth.Sapling(
                relativePosition(event.getLocation(), minHeight),
                relativePosition(saplingPlane.getCenter(), minHeight),
                event.getSpecies().name());
        Optional<SaplingGrowth.Growth> growth = ModuleServices.growSapling(engine, sapling, new WorldSurroundings(event.getWorld(), saplingPlane));

        if (growth.isEmpty()) {
            IrisLogging.debug(this.getClass().getName() + " had options but did not manage to find objectPlacements for them");
            return;
        }

        saplingPlane.forEach(block -> block.setType(Material.AIR));
        String treeMarker = growth.get().object() + "@" + ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
        List<BlockState> blockStateList = new KList<>();
        KMap<Location, BlockData> dataCache = new KMap<>();
        for (PlacedBlock placed : growth.get().blocks()) {
            Block b = event.getWorld().getBlockAt(placed.position().x(), placed.position().y() + minHeight, placed.position().z());
            BlockData d = (BlockData) IrisPlatforms.get().registries().block(placed.state().key()).nativeHandle();
            // Listeners get the post-growth tree, per the StructureGrowEvent contract;
            // a fresh snapshot here handed every plugin an all-air "tree".
            BlockState state = b.getState();
            state.setBlockData(d);
            blockStateList.add(state);
            dataCache.put(b.getLocation(), d);
        }

        event.setCancelled(true);

        Runnable growTask = () -> {

            StructureGrowEvent iGrow = new StructureGrowEvent(event.getLocation(), event.getSpecies(), event.isFromBonemeal(), event.getPlayer(), blockStateList);
            REENTRANT.set(true);
            try {
                Bukkit.getServer().getPluginManager().callEvent(iGrow);
            } finally {
                REENTRANT.set(false);
            }

            if (!iGrow.isCancelled()) {
                for (BlockState state : iGrow.getBlocks()) {
                    Location l = state.getLocation();

                    BlockData d = dataCache.get(l);
                    if (d == null) continue;
                    Block block = l.getBlock();

                    block.setBlockData(d, false);
                    int mantleY = block.getY() - event.getWorld().getMinHeight();
                    // Lease-gated: the grow task can land after the engine started closing, and
                    // an unleased mantle write would race the region flush.
                    EngineLifecycleTasks.run(engine, "tree_grow_mantle", () -> {
                        engine.getMantle().getMantle().set(block.getX(), mantleY, block.getZ(), treeMarker);
                        engine.getMantle().getMantle().set(
                                block.getX(),
                                mantleY,
                                block.getZ(),
                                TreeBlockMaterial.of(block.getBlockData().getAsString())
                        );
                    });
                }
            }
        };
        if (!J.runAt(event.getLocation(), growTask) && !J.isFolia()) {
            J.s(growTask);
        }
    }

    private static com.volmit.geometry.BlockPosition relativePosition(Location location, int minHeight) {
        return new com.volmit.geometry.BlockPosition(location.getBlockX(), location.getBlockY() - minHeight, location.getBlockZ());
    }

    /** The world around the saplings as the modules read it; the saplings that grow count as air, as they are about to. */
    private static final class WorldSurroundings implements SaplingGrowth.Surroundings {
        private static final com.volmit.block.BlockState AIR = com.volmit.block.BlockState.parse("minecraft:air");

        private final World world;
        private final Cuboid saplingPlane;

        private WorldSurroundings(World world, Cuboid saplingPlane) {
            this.world = world;
            this.saplingPlane = saplingPlane;
        }

        @Override
        public int highest(int x, int z, boolean ignoreFluid) {
            int worldY = ignoreFluid ? world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR) : world.getHighestBlockYAt(x, z);
            return worldY - world.getMinHeight();
        }

        @Override
        public com.volmit.block.BlockState blockAt(int x, int y, int z) {
            int worldY = y + world.getMinHeight();
            if (saplingPlane.contains(x, worldY, z)) {
                return AIR;
            }
            return com.volmit.block.BlockState.parse(PlatformBlockStates.namespaced(BukkitBlockState.of(world.getBlockAt(x, worldY, z).getBlockData()).key()));
        }
    }

    /**
     * Get the Cuboid of sapling sizes at a location & blockData predicate
     *
     * @param at    this location
     * @param valid with this blockData predicate
     * @param world the world to check in
     * @return A cuboid containing only saplings
     */
    public Cuboid getSaplings(Location at, Predicate<BlockData> valid, World world) {
        KList<BlockPosition> blockPositions = new KList<>();
        grow(at.getWorld(), new BlockPosition(at.getBlockX(), at.getBlockY(), at.getBlockZ()), valid, blockPositions);
        if (blockPositions.isEmpty()) {
            // No matching saplings (e.g. a giant-mushroom grow event): a 1x1 plane at the
            // event location, never the MIN/MAX sentinel cuboid below.
            return new Cuboid(at, at);
        }
        BlockPosition a = new BlockPosition(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        BlockPosition b = new BlockPosition(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

        // Maximise the block position in x and z to get max cuboid bounds
        for (BlockPosition blockPosition : blockPositions) {
            a.max(blockPosition);
            b.min(blockPosition);
        }

        IrisLogging.debug("Blocks: " + blockPositions.size());
        IrisLogging.debug("Min: " + a + " Max: " + b);

        // Create a cuboid with the size calculated before
        Cuboid cuboid = new Cuboid(a.toBlock(world).getLocation(), b.toBlock(world).getLocation());
        boolean cuboidIsValid = true;

        // Loop while the cuboid is larger than 2
        while (Math.min(cuboid.getSizeX(), cuboid.getSizeZ()) > 0) {
            checking:
            for (int i = cuboid.getLowerX(); i < cuboid.getUpperX(); i++) {
                for (int j = cuboid.getLowerY(); j < cuboid.getUpperY(); j++) {
                    for (int k = cuboid.getLowerZ(); k < cuboid.getUpperZ(); k++) {
                        if (!blockPositions.contains(new BlockPosition(i, j, k))) {
                            cuboidIsValid = false;
                            break checking;
                        }
                    }
                }
            }

            // Return this cuboid if it's valid
            if (cuboidIsValid) {
                return cuboid;
            }

            // Inset the cuboid and try again (revalidate)
            cuboid = cuboid.inset(Cuboid.CuboidDirection.Horizontal, 1);
            cuboidIsValid = true;
        }

        return new Cuboid(at, at);
    }

    /**
     * Grows the blockPosition list by means of checking neighbours in
     *
     * @param world  the world to check in
     * @param center the location of this position
     * @param valid  validation on blockData to check block with
     * @param l      list of block positions to add new neighbors too
     */
    private void grow(World world, BlockPosition center, Predicate<BlockData> valid, KList<BlockPosition> l) {
        // Make sure size is less than 50, the block to check isn't already in, and make sure the blockData still matches
        if (l.size() <= 50 && !l.contains(center) && valid.test(center.toBlock(world).getBlockData())) {
            l.add(center);
            grow(world, center.add(1, 0, 0), valid, l);
            grow(world, center.add(-1, 0, 0), valid, l);
            grow(world, center.add(0, 0, 1), valid, l);
            grow(world, center.add(0, 0, -1), valid, l);
        }
    }
}
