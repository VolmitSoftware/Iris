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

package com.volmit.iris.core.edit;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.BlockPosition;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import lombok.Data;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;

@SuppressWarnings("ALL")
@Data
public class DustRevealer {
    private final Engine engine;
    private final World world;
    private final BlockPosition block;
    private final String key;
    private final KList<BlockPosition> hits;

    public DustRevealer(Engine engine, World world, BlockPosition block, String key, KList<BlockPosition> hits) {
        this.engine = engine;
        this.world = world;
        this.block = block;
        this.key = key;
        this.hits = hits;

        J.s(() -> {
            new BlockSignal(world.getBlockAt(block.getX(), block.getY(), block.getZ()), 10);
            if (M.r(0.25)) {
                world.playSound(block.toBlock(world).getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, RNG.r.f(0.2f, 2f));
            }
            J.a(() -> {
                while (BlockSignal.active.get() > 128) {
                    J.sleep(5);
                }

                try {
                    is(new BlockPosition(block.getX() + 1, block.getY(), block.getZ()));
                    is(new BlockPosition(block.getX() - 1, block.getY(), block.getZ()));
                    is(new BlockPosition(block.getX(), block.getY() + 1, block.getZ()));
                    is(new BlockPosition(block.getX(), block.getY() - 1, block.getZ()));
                    is(new BlockPosition(block.getX(), block.getY(), block.getZ() + 1));
                    is(new BlockPosition(block.getX(), block.getY(), block.getZ() - 1));
                    is(new BlockPosition(block.getX() + 1, block.getY(), block.getZ() + 1));
                    is(new BlockPosition(block.getX() + 1, block.getY(), block.getZ() - 1));
                    is(new BlockPosition(block.getX() - 1, block.getY(), block.getZ() + 1));
                    is(new BlockPosition(block.getX() - 1, block.getY(), block.getZ() - 1));
                    is(new BlockPosition(block.getX() + 1, block.getY() + 1, block.getZ()));
                    is(new BlockPosition(block.getX() + 1, block.getY() - 1, block.getZ()));
                    is(new BlockPosition(block.getX() - 1, block.getY() + 1, block.getZ()));
                    is(new BlockPosition(block.getX() - 1, block.getY() - 1, block.getZ()));
                    is(new BlockPosition(block.getX(), block.getY() + 1, block.getZ() - 1));
                    is(new BlockPosition(block.getX(), block.getY() + 1, block.getZ() + 1));
                    is(new BlockPosition(block.getX(), block.getY() - 1, block.getZ() - 1));
                    is(new BlockPosition(block.getX(), block.getY() - 1, block.getZ() + 1));
                    is(new BlockPosition(block.getX() - 1, block.getY() + 1, block.getZ() - 1));
                    is(new BlockPosition(block.getX() - 1, block.getY() + 1, block.getZ() + 1));
                    is(new BlockPosition(block.getX() - 1, block.getY() - 1, block.getZ() - 1));
                    is(new BlockPosition(block.getX() - 1, block.getY() - 1, block.getZ() + 1));
                    is(new BlockPosition(block.getX() + 1, block.getY() + 1, block.getZ() - 1));
                    is(new BlockPosition(block.getX() + 1, block.getY() + 1, block.getZ() + 1));
                    is(new BlockPosition(block.getX() + 1, block.getY() - 1, block.getZ() - 1));
                    is(new BlockPosition(block.getX() + 1, block.getY() - 1, block.getZ() + 1));
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                }
            });
        }, RNG.r.i(2, 8));
    }

    public static void spawn(Block block, VolmitSender sender) {
        World world = block.getWorld();
        Engine access = IrisToolbelt.access(world).getEngine();

        if (access != null) {
            String a = access.getObjectPlacementKey(block.getX(), block.getY() - block.getWorld().getMinHeight(), block.getZ());
            if (a != null) {
                world.playSound(block.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1f, 0.1f);

                sender.sendMessage("Found object " + a);
                J.a(() -> {
                    new DustRevealer(access, world, new BlockPosition(block.getX(), block.getY(), block.getZ()), a, new KList<>());
                });
            }
        }
    }

    private boolean is(BlockPosition a) {
        int betterY = a.getY() - world.getMinHeight();
        if (isValidTry(a) && engine.getObjectPlacementKey(a.getX(), betterY, a.getZ()) != null && engine.getObjectPlacementKey(a.getX(), betterY, a.getZ()).equals(key)) {
            hits.add(a);
            new DustRevealer(engine, world, a, key, hits);
            return true;
        }

        return false;
    }

    private boolean isValidTry(BlockPosition b) {
        return !hits.contains(b);
    }
}
