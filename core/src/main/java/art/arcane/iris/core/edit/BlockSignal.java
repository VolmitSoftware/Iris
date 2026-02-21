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

package art.arcane.iris.core.edit;

import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.SR;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("InstantiationOfUtilityClass")
public class BlockSignal {
    public static final AtomicInteger active = new AtomicInteger(0);

    public BlockSignal(Block block, int ticks) {
        active.incrementAndGet();
        Location tg = block.getLocation().clone().add(0.5, 0, 0.5);
        FallingBlock e = block.getWorld().spawnFallingBlock(tg, block.getBlockData());
        e.setGravity(false);
        e.setInvulnerable(true);
        e.setGlowing(true);
        e.setDropItem(false);
        e.setHurtEntities(false);
        e.setSilent(true);
        e.setTicksLived(1);
        e.setVelocity(new Vector(0, 0, 0));
        Location blockLocation = block.getLocation();
        Runnable removeTask = () -> {
            if (!J.runEntity(e, e::remove) && !e.isDead()) {
                e.remove();
            }
            active.decrementAndGet();
            sendBlockRefresh(block);
        };
        if (!J.runAt(blockLocation, removeTask, ticks)) {
            if (!J.isFolia()) {
                J.s(removeTask, ticks);
            }
        }
    }

    public static void of(Block block, int ticks) {
        if (block == null) {
            return;
        }

        of(block.getWorld(), block.getX(), block.getY(), block.getZ(), ticks);
    }

    public static void of(Block block) {
        of(block, 100);
    }

    public static void of(World world, int x, int y, int z, int ticks) {
        if (world == null) {
            return;
        }

        Location location = new Location(world, x, y, z);
        Runnable createTask = () -> new BlockSignal(world.getBlockAt(x, y, z), ticks);
        if (!J.runAt(location, createTask)) {
            if (!J.isFolia()) {
                J.s(createTask);
            }
        }
    }

    public static void of(World world, int x, int y, int z) {
        of(world, x, y, z, 100);
    }

    public static Runnable forever(Block block) {
        Location tg = block.getLocation().clone().add(0.5, 0, 0.5).clone();
        FallingBlock e = block.getWorld().spawnFallingBlock(tg.clone(), block.getBlockData());
        e.setGravity(false);
        e.setInvulnerable(true);
        e.setGlowing(true);
        e.teleport(tg.clone());
        e.setDropItem(false);
        e.setHurtEntities(false);
        e.setSilent(true);
        e.setTicksLived(1);
        e.setVelocity(new Vector(0, 0, 0));

        new SR(20) {
            @Override
            public void run() {
                if (!J.runEntity(e, () -> {
                    if (e.isDead()) {
                        cancel();
                        return;
                    }

                    e.setTicksLived(1);
                    e.teleport(tg.clone());
                    e.setVelocity(new Vector(0, 0, 0));
                })) {
                    cancel();
                }
            }
        };

        return () -> {
            if (!J.runEntity(e, e::remove) && !e.isDead()) {
                e.remove();
            }
            Location blockLocation = block.getLocation();
            Runnable refreshTask = () -> sendBlockRefresh(block);
            if (!J.runAt(blockLocation, refreshTask)) {
                refreshTask.run();
            }
        };
    }

    private static void sendBlockRefresh(Block block) {
        if (block == null) {
            return;
        }

        Location location = block.getLocation();
        BlockData blockData = block.getBlockData();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(location.getWorld())) {
                continue;
            }

            J.runEntity(player, () -> player.sendBlockChange(location, blockData));
        }
    }
}
