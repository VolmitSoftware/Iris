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
import com.volmit.iris.core.ProjectManager;
import com.volmit.iris.core.WandManager;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.core.tools.IrisWorlds;
import com.volmit.iris.engine.object.IrisAxisRotationClamp;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.engine.object.IrisObjectPlacement;
import com.volmit.iris.engine.object.IrisObjectPlacementScaleInterpolator;
import com.volmit.iris.engine.object.IrisObjectRotation;
import com.volmit.iris.engine.object.IrisObjectScale;
import com.volmit.iris.engine.object.common.IObjectPlacer;
import com.volmit.iris.engine.object.tile.TileData;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CommandIrisObjectPaste extends MortarCommand {

    public CommandIrisObjectPaste() {
        super("paste", "pasta", "place", "p");
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
        IrisObject obj = IrisData.loadAnyObject(args[0]);

        if (obj == null || obj.getLoadFile() == null) {

            sender.sendMessage("Can't find " + args[0] + " in the " + ProjectManager.WORKSPACE_NAME + " folder");
            return true;
        }

        boolean intoWand = false;
        int rotate = 0;
        double scale = 1;
        IrisObjectPlacementScaleInterpolator interpolator = IrisObjectPlacementScaleInterpolator.NONE;

        for (int i = 0; i < args.length; i++) {
            String str = args[i];
            if (str.equalsIgnoreCase("-edit") || str.equalsIgnoreCase("-e")) {
                intoWand = true;
            } else if (str.equalsIgnoreCase("-r") || str.equalsIgnoreCase("-rotate")) {
                if (i + 1 >= args.length) {
                    sender.sendMessage("No rotation parameter provided! Usage is -rotate <degrees>");
                    return true;
                }
                try {
                    rotate = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("\"" + args[i + 1] + "\" is not a number!");
                    return true;
                }
            } else if (str.equalsIgnoreCase("-s") || str.equalsIgnoreCase("-scale")) {
                if (i + 1 >= args.length) {
                    sender.sendMessage("No scale parameter provided! Usage is -scale <size> [method=linear|cubic|hermite|none]");
                    return true;
                }
                try {
                    scale = Double.parseDouble(args[i + 1]);

                    int max = 10;

                    if (obj.getBlocks().size() > 30_000) {
                        max = 5;
                    }
                    if (obj.getBlocks().size() > 60_000) {
                        max = 3;
                    }
                    if (obj.getBlocks().size() > 90_000) {
                        max = 2;
                    }

                    if (scale > max) {
                        sender.sendMessage("Due to size restrictions, the object will only be scaled to " + max + "x size");
                        scale = max;
                    }

                    if (i + 2 >= args.length) {
                        continue; //Dont parse the method and keep it at none
                    }
                    String intpol = args[i + 2];
                    if (intpol.toLowerCase().startsWith("method=")) intpol = intpol.split("=", 2)[1];
                    if (intpol.toLowerCase().startsWith("tri")) intpol = intpol.substring(3);

                    interpolator = IrisObjectPlacementScaleInterpolator.valueOf("TRI" + intpol.toUpperCase());
                } catch (NumberFormatException e) {
                    sender.sendMessage("\"" + args[i + 1] + "\" is not a decimal number!");
                    return true;
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("\"" + args[i + 2] + "\" is not a valid interpolator method! Must be LINEAR, CUBIC, HERMITE or NONE!");
                    return true;
                }
            } else if (str.startsWith("-")) {
                sender.sendMessage("Unknown flag \"" + args[i + 1] + "\" provided! Valid flags are -edit, -rotate and -scale");
                return true;
            }
        }

        IrisObjectPlacement placement = new IrisObjectPlacement();
        if (rotate != 0) {
            IrisObjectRotation rot = IrisObjectRotation.of(0, rotate, 0);
            placement.setRotation(rot);
        }
        if (scale != 1) {
            obj = obj.scaled(scale, interpolator);
        }

        ItemStack wand = sender.player().getInventory().getItemInMainHand();

        Iris.debug("Loaded object for placement: " + "objects/" + args[0] + ".iob");

        sender.player().getWorld().playSound(sender.player().getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);

        Set<Material> skipBlocks = Set.of(Material.GRASS, Material.SNOW, Material.VINE, Material.TORCH, Material.DEAD_BUSH,
                Material.POPPY, Material.DANDELION);

        Location block = sender.player().getTargetBlock(skipBlocks, 256).getLocation().clone().add(0, 1, 0);

        //WandManager.pasteSchematic(obj, block);

        Map<Block, BlockData> futureChanges = new HashMap<>();
        obj.place(block.getBlockX(), block.getBlockY() + (int)obj.getCenter().getY(), block.getBlockZ(), createPlacer(sender.player(), block.getWorld(), futureChanges), placement, new RNG(), null);
        CommandIrisObjectUndo.addChanges(sender.player(), futureChanges);

        if (intoWand) {
            ItemStack newWand = WandManager.createWand(block.clone().subtract(obj.getCenter()).add(obj.getW() - 1,
                    obj.getH() + obj.getCenter().clone().getY() - 1, obj.getD() - 1), block.clone().subtract(obj.getCenter().clone().setY(0)));
            if (WandManager.isWand(wand)) {
                wand = newWand;
                p.getInventory().setItemInMainHand(wand);
                sender.sendMessage("Updated wand for " + "objects/" + args[0] + ".iob");
            } else {
                int slot = WandManager.findWand(sender.player().getInventory());
                if (slot == -1) {
                    p.getInventory().addItem(newWand);
                    sender.sendMessage("Given new wand for " + "objects/" + args[0] + ".iob");
                } else {
                    sender.player().getInventory().setItem(slot, newWand);
                    sender.sendMessage("Updated wand for " + "objects/" + args[0] + ".iob");
                }
            }
        } else {
            sender.sendMessage("Placed " + "objects/" + args[0] + ".iob");
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
                return world.getHighestBlockYAt(x, z, ignoreFluid ? HeightMap.OCEAN_FLOOR: HeightMap.MOTION_BLOCKING);
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
