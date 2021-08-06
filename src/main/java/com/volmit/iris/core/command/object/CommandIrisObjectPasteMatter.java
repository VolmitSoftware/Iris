/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.command.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.core.tools.IrisWorlds;
import com.volmit.iris.engine.object.common.IObjectPlacer;
import com.volmit.iris.engine.object.tile.TileData;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.matter.Matter;
import com.volmit.iris.util.matter.WorldMatter;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;

public class CommandIrisObjectPasteMatter extends MortarCommand {

    public CommandIrisObjectPasteMatter() {
        super("mpaste");
        requiresPermission(Iris.perm);
        setCategory("Object");
        setDescription("Paste an object");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {
        if ((args.length == 0 || args.length == 1) && sender.isPlayer() && IrisWorlds.isIrisWorld(sender.player().getWorld())) {
            IrisData data = IrisWorlds.access(sender.player().getWorld()).getData();
            if (data == null) {
                sender.sendMessage("Tab complete options only work for objects while in an Iris world.");
            } else if (args.length == 0) {
                list.add(data.getObjectLoader().getPossibleKeys());
            } else {
                list.add(data.getObjectLoader().getPossibleKeys(args[0]));
            }
        }
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio Objects, please enable studio in Iris/settings.json");
            return true;
        }

        if (!sender.isPlayer()) {
            sender.sendMessage("Only players can spawn objects with this command");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Please specify the name of of the object want to paste");
            return true;
        }

        Player p = sender.player();
        File f = new File(args[0]);
        try {
            Matter matter = Matter.read(f);
            WorldMatter.placeMatter(matter, p.getLocation());
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[name] [-edit] [-rotate [angle]] [-scale [num] [method]]";
    }

    public static IObjectPlacer createPlacer(Player player, World world, Map<Block, BlockData> futureBlockChanges) {

        return new IObjectPlacer() {
            @Override
            public int getHighest(int x, int z, IrisData data) {
                return world.getHighestBlockYAt(x, z);
            }

            @Override
            public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
                return world.getHighestBlockYAt(x, z, ignoreFluid ? HeightMap.OCEAN_FLOOR : HeightMap.MOTION_BLOCKING);
            }

            @Override
            public void set(int x, int y, int z, BlockData d) {
                Block block = world.getBlockAt(x, y, z);

                //Prevent blocks being set in or bellow bedrock
                if (y <= world.getMinHeight() || block.getType() == Material.BEDROCK) return;

                futureBlockChanges.put(block, block.getBlockData());

                block.setBlockData(d);
            }

            @Override
            public BlockData get(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getBlockData();
            }

            @Override
            public boolean isPreventingDecay() {
                return false;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getType().isSolid();
            }

            @Override
            public boolean isUnderwater(int x, int z) {
                return false;
            }

            @Override
            public int getFluidHeight() {
                return 63;
            }

            @Override
            public boolean isDebugSmartBore() {
                return false;
            }

            @Override
            public void setTile(int xx, int yy, int zz, TileData<? extends TileState> tile) {
                BlockState state = world.getBlockAt(xx, yy, zz).getState();
                tile.toBukkitTry(state);
                state.update();
            }
        };
    }
}
