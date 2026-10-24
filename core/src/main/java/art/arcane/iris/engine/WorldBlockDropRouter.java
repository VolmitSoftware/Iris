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

package art.arcane.iris.engine;

import art.arcane.iris.core.service.tree.BlockDropRouter;
import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.core.modules.PlatformBlockStates;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.platform.bukkit.BukkitItemStacks;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.matter.MatterMarker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import com.volmit.block.BlockState;
import com.volmit.service.BlockDrops;
import com.volmit.service.EntitySpawns;
import com.volmit.geometry.BlockPosition;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Biome, region and dimension block drops for a Bukkit Iris world. The drop list is resolved on the
 * event thread, the marker cleanup runs asynchronously and the drops themselves are handed to the
 * event's own router when it offers one so a deferred break keeps its inventory destination.
 */
final class WorldBlockDropRouter {
    private final IrisWorldManager manager;

    WorldBlockDropRouter(IrisWorldManager manager) {
        this.manager = manager;
    }

    void onBlockBreak(BlockBreakEvent e) {
        if (e.getBlock().getWorld().equals(BukkitWorldBinding.world(manager.getTarget().getWorld()))) {
            int blockX = e.getBlock().getX();
            int mantleY = toMantleY(e.getBlock().getY(), manager.getEngine().getWorld().minHeight());
            int blockZ = e.getBlock().getZ();

            BlockDrops.Result result = ModuleServices.blockDrops(manager.getEngine(), brokenBlock(e));
            KList<ItemStack> d = BukkitItemStacks.createAll(result.drops());

            if (result.replacesVanillaDrops()) {
                e.setDropItems(false);
            }

            World w = e.getBlock().getWorld();
            Location blockLocation = e.getBlock().getLocation();
            Location dropLocation = blockLocation.clone().add(.5, .5, .5);
            BlockDropRouter dropRouter = e instanceof BlockDropRouter router ? router : null;
            Runnable finalizedBreak = manager.managedTask("bukkit_world_manager_block_break_finalize", () -> {
                if (e.isCancelled()) {
                    return;
                }
                J.a(manager.managedTask("bukkit_world_manager_block_break_marker", () -> {
                    MatterMarker marker = manager.getMantle().get(blockX, mantleY, blockZ, MatterMarker.class);
                    if (marker == null || marker.getTag().equals("cave_floor") || marker.getTag().equals("cave_ceiling")) {
                        return;
                    }

                    Optional<EntitySpawns.MarkerRule> rule = ModuleServices.markerRule(manager.getEngine(), marker.getTag());
                    if (rule.isEmpty() || rule.get().removeOnChange()) {
                        manager.getMantle().remove(blockX, mantleY, blockZ, MatterMarker.class);
                    }
                }));
                routeDrops(d, dropRouter, item -> w.dropItemNaturally(dropLocation, item));
            });
            if (!J.runAt(blockLocation, finalizedBreak, 1) && !J.isFolia()) {
                J.s(finalizedBreak, 1);
            }
        }
    }

    void onBlockPlace(BlockPlaceEvent e) {

    }

    static int toMantleY(int worldY, int minHeight) {
        return worldY - minHeight;
    }

    static <T> void routeDrops(Iterable<T> drops, BlockDropRouter router, Consumer<T> fallback) {
        for (T drop : drops) {
            boolean routed = false;
            if (router != null) {
                try {
                    routed = router.routeDrop(drop);
                } catch (Throwable error) {
                    IrisLogging.reportError("Failed to route a deferred Iris block drop.", error);
                }
            }
            if (!routed) {
                fallback.accept(drop);
            }
        }
    }

    static int toWorldY(int mantleY, int minHeight) {
        return mantleY + minHeight;
    }

    private BlockDrops.Broken brokenBlock(BlockBreakEvent e) {
        int relativeY = toMantleY(e.getBlock().getY(), manager.getEngine().getWorld().minHeight());
        BlockPosition position = new BlockPosition(e.getBlock().getX(), relativeY, e.getBlock().getZ());
        BlockState block = BlockState.parse(PlatformBlockStates.namespaced(BukkitBlockState.of(e.getBlock().getBlockData()).key()));
        return new BlockDrops.Broken(position, block);
    }
}
