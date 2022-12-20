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

package com.volmit.iris.core.commands;

import com.volmit.iris.Iris;
import com.volmit.iris.core.link.WorldEditLink;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.service.ObjectSVC;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.core.service.WandSVC;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.data.Cuboid;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.decree.specialhandlers.ObjectHandler;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.Direction;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.Queue;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;

@Decree(name = "object", aliases = "o", origin = DecreeOrigin.PLAYER, studio = true, description = "Iris object manipulation")
public class CommandObject implements DecreeExecutor {

    private static final Set<Material> skipBlocks = Set.of(Material.GRASS, Material.SNOW, Material.VINE, Material.TORCH, Material.DEAD_BUSH,
            Material.POPPY, Material.DANDELION);

    public static IObjectPlacer createPlacer(World world, Map<Block, BlockData> futureBlockChanges) {

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
            public boolean isCarved(int x, int y, int z) {
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

            @Override
            public Engine getEngine() {
                return null;
            }
        };
    }

    @Decree(description = "Check the composition of an object")
    public void analyze(
            @Param(description = "The object to analyze", customHandler = ObjectHandler.class)
            String object
    ) {
        IrisObject o = IrisData.loadAnyObject(object);
        sender().sendMessage("Object Size: " + o.getW() + " * " + o.getH() + " * " + o.getD() + "");
        sender().sendMessage("Blocks Used: " + NumberFormat.getIntegerInstance().format(o.getBlocks().size()));

        Queue<BlockData> queue = o.getBlocks().enqueueValues();
        Map<Material, Set<BlockData>> unsorted = new HashMap<>();
        Map<BlockData, Integer> amounts = new HashMap<>();
        Map<Material, Integer> materials = new HashMap<>();
        while (queue.hasNext()) {
            BlockData block = queue.next();

            //unsorted.put(block.getMaterial(), block);

            if (!amounts.containsKey(block)) {
                amounts.put(block, 1);


            } else
                amounts.put(block, amounts.get(block) + 1);

            if (!materials.containsKey(block.getMaterial())) {
                materials.put(block.getMaterial(), 1);
                unsorted.put(block.getMaterial(), new HashSet<>());
                unsorted.get(block.getMaterial()).add(block);
            } else {
                materials.put(block.getMaterial(), materials.get(block.getMaterial()) + 1);
                unsorted.get(block.getMaterial()).add(block);
            }

        }

        List<Material> sortedMatsList = amounts.keySet().stream().map(BlockData::getMaterial)
                .sorted().toList();
        Set<Material> sortedMats = new TreeSet<>(Comparator.comparingInt(materials::get).reversed());
        sortedMats.addAll(sortedMatsList);
        sender().sendMessage("== Blocks in object ==");

        int n = 0;
        for (Material mat : sortedMats) {
            int amount = materials.get(mat);
            List<BlockData> set = new ArrayList<>(unsorted.get(mat));
            set.sort(Comparator.comparingInt(amounts::get).reversed());
            BlockData data = set.get(0);
            int dataAmount = amounts.get(data);

            String string = " - " + mat.toString() + "*" + amount;
            if (data.getAsString(true).contains("[")) {
                string = string + " --> [" + data.getAsString(true).split("\\[")[1]
                        .replaceAll("true", ChatColor.GREEN + "true" + ChatColor.GRAY)
                        .replaceAll("false", ChatColor.RED + "false" + ChatColor.GRAY) + "*" + dataAmount;
            }

            sender().sendMessage(string);

            n++;

            if (n >= 10) {
                sender().sendMessage("  + " + (sortedMats.size() - n) + " other block types");
                return;
            }
        }
    }

    @Decree(description = "Get a powder that reveals objects", studio = true, aliases = "d")
    public void dust() {
        player().getInventory().addItem(WandSVC.createDust());
        sender().playSound(Sound.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS, 1f, 1.5f);
    }

    @Decree(description = "Contract a selection based on your looking direction", aliases = "-")
    public void contract(
            @Param(description = "The amount to inset by", defaultValue = "1")
            int amount
    ) {
        if (!WandSVC.isHoldingWand(player())) {
            sender().sendMessage("Hold your wand.");
            return;
        }


        Location[] b = WandSVC.getCuboid(player());
        if (b == null) {
            return;
        }
        Location a1 = b[0].clone();
        Location a2 = b[1].clone();
        Cuboid cursor = new Cuboid(a1, a2);
        Direction d = Direction.closest(player().getLocation().getDirection()).reverse();
        assert d != null;
        cursor = cursor.expand(d, -amount);
        b[0] = cursor.getLowerNE();
        b[1] = cursor.getUpperSW();
        player().getInventory().setItemInMainHand(WandSVC.createWand(b[0], b[1]));
        player().updateInventory();
        sender().playSound(Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 0.55f);
    }

    @Decree(description = "Set point 1 to look", aliases = "p1")
    public void position1(
            @Param(description = "Whether to use your current position, or where you look", defaultValue = "true")
            boolean here
    ) {
        if (!WandSVC.isHoldingWand(player())) {
            sender().sendMessage("Ready your Wand.");
            return;
        }

        if (WandSVC.isHoldingWand(player())) {
            Location[] g = WandSVC.getCuboid(player());

            if (g == null) {
                return;
            }
            if (!here) {
                // TODO: WARNING HEIGHT
                g[1] = player().getTargetBlock(null, 256).getLocation().clone();
            } else {
                g[1] = player().getLocation().getBlock().getLocation().clone().add(0, -1, 0);
            }
            player().setItemInHand(WandSVC.createWand(g[0], g[1]));
        }
    }

    @Decree(description = "Set point 2 to look", aliases = "p2")
    public void position2(
            @Param(description = "Whether to use your current position, or where you look", defaultValue = "true")
            boolean here
    ) {
        if (!WandSVC.isHoldingWand(player())) {
            sender().sendMessage("Ready your Wand.");
            return;
        }

        if (WandSVC.isHoldingIrisWand(player())) {
            Location[] g = WandSVC.getCuboid(player());

            if (g == null) {
                return;
            }

            if (!here) {
                // TODO: WARNING HEIGHT
                g[0] = player().getTargetBlock(null, 256).getLocation().clone();
            } else {
                g[0] = player().getLocation().getBlock().getLocation().clone().add(0, -1, 0);
            }
            player().setItemInHand(WandSVC.createWand(g[0], g[1]));
        }
    }

    @Decree(description = "Paste an object", sync = true)
    public void paste(
            @Param(description = "The object to paste", customHandler = ObjectHandler.class)
            String object,
            @Param(description = "Whether or not to edit the object (need to hold wand)", defaultValue = "false")
            boolean edit,
            @Param(description = "The amount of degrees to rotate by", defaultValue = "0")
            int rotate,
            @Param(description = "The factor by which to scale the object placement", defaultValue = "1")
            double scale
//            ,
//            @Param(description = "The scale interpolator to use", defaultValue = "none")
//            IrisObjectPlacementScaleInterpolator interpolator
    ) {
        IrisObject o = IrisData.loadAnyObject(object);
        double maxScale = Double.max(10 - o.getBlocks().size() / 10000d, 1);
        if (scale > maxScale) {
            sender().sendMessage(C.YELLOW + "Indicated scale exceeds maximum. Downscaled to maximum: " + maxScale);
            scale = maxScale;
        }

        sender().playSound(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);

        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setRotation(IrisObjectRotation.of(0, rotate, 0));

        ItemStack wand = player().getInventory().getItemInMainHand();
        Location block = player().getTargetBlock(skipBlocks, 256).getLocation().clone().add(0, 1, 0);

        Map<Block, BlockData> futureChanges = new HashMap<>();

        if (scale != 1) {
            o = o.scaled(scale, IrisObjectPlacementScaleInterpolator.TRICUBIC);
        }

        o.place(block.getBlockX(), block.getBlockY() + (int) o.getCenter().getY(), block.getBlockZ(), createPlacer(block.getWorld(), futureChanges), placement, new RNG(), null);

        Iris.service(ObjectSVC.class).addChanges(futureChanges);

        if (edit) {
            ItemStack newWand = WandSVC.createWand(block.clone().subtract(o.getCenter()).add(o.getW() - 1,
                    o.getH() + o.getCenter().clone().getY() - 1, o.getD() - 1), block.clone().subtract(o.getCenter().clone().setY(0)));
            if (WandSVC.isWand(wand)) {
                wand = newWand;
                player().getInventory().setItemInMainHand(wand);
                sender().sendMessage("Updated wand for " + "objects/" + o.getLoadKey() + ".iob ");
            } else {
                int slot = WandSVC.findWand(player().getInventory());
                if (slot == -1) {
                    player().getInventory().addItem(newWand);
                    sender().sendMessage("Given new wand for " + "objects/" + o.getLoadKey() + ".iob ");
                } else {
                    player().getInventory().setItem(slot, newWand);
                    sender().sendMessage("Updated wand for " + "objects/" + o.getLoadKey() + ".iob ");
                }
            }
        } else {
            sender().sendMessage("Placed " + object);
        }
    }

    @Decree(description = "Save an object")
    public void save(
            @Param(description = "The dimension to store the object in", contextual = true)
            IrisDimension dimension,
            @Param(description = "The file to store it in, can use / for subfolders")
            String name,
            @Param(description = "Overwrite existing object files", defaultValue = "false", aliases = "force")
            boolean overwrite
    ) {
        IrisObject o = WandSVC.createSchematic(player());

        if (o == null) {
            sender().sendMessage(C.YELLOW + "You need to hold your wand!");
            return;
        }

        File file = Iris.service(StudioSVC.class).getWorkspaceFile(dimension.getLoadKey(), "objects", name + ".iob");

        if (file.exists() && !overwrite) {
            sender().sendMessage(C.RED + "File already exists. Set overwrite=true to overwrite it.");
            return;
        }
        try {
            o.write(file);
        } catch (IOException e) {
            sender().sendMessage(C.RED + "Failed to save object because of an IOException: " + e.getMessage());
            Iris.reportError(e);
        }

        sender().playSound(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);
        sender().sendMessage(C.GREEN + "Successfully object to saved: " + dimension.getLoadKey() + "/objects/" + name);
    }

    @Decree(description = "Shift a selection in your looking direction", aliases = "-")
    public void shift(
            @Param(description = "The amount to shift by", defaultValue = "1")
            int amount
    ) {
        if (!WandSVC.isHoldingWand(player())) {
            sender().sendMessage("Hold your wand.");
            return;
        }

        Location[] b = WandSVC.getCuboid(player());
        Location a1 = b[0].clone();
        Location a2 = b[1].clone();
        Direction d = Direction.closest(player().getLocation().getDirection()).reverse();
        if (d == null) {
            return; // HOW DID THIS HAPPEN
        }
        a1.add(d.toVector().multiply(amount));
        a2.add(d.toVector().multiply(amount));
        Cuboid cursor = new Cuboid(a1, a2);
        b[0] = cursor.getLowerNE();
        b[1] = cursor.getUpperSW();
        player().getInventory().setItemInMainHand(WandSVC.createWand(b[0], b[1]));
        player().updateInventory();
        sender().playSound(Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 0.55f);
    }

    @Decree(description = "Undo a number of pastes", aliases = "-")
    public void undo(
            @Param(description = "The amount of pastes to undo", defaultValue = "1")
            int amount
    ) {
        ObjectSVC service = Iris.service(ObjectSVC.class);
        int actualReverts = Math.min(service.getUndos().size(), amount);
        service.revertChanges(actualReverts);
        sender().sendMessage("Reverted " + actualReverts + " pastes!");
    }

    @Decree(description = "Gets an object wand and grabs the current WorldEdit selection.", aliases = "we", origin = DecreeOrigin.PLAYER, studio = true)
    public void we() {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) {
            sender().sendMessage(C.RED + "You can't get a WorldEdit selection without WorldEdit, you know.");
            return;
        }

        Cuboid locs = WorldEditLink.getSelection(sender().player());

        if (locs == null) {
            sender().sendMessage(C.RED + "You don't have a WorldEdit selection in this world.");
            return;
        }

        sender().player().getInventory().addItem(WandSVC.createWand(locs.getLowerNE(), locs.getUpperSW()));
        sender().sendMessage(C.GREEN + "A fresh wand with your current WorldEdit selection on it!");
    }

    @Decree(description = "Get an object wand", sync = true)
    public void wand() {
        player().getInventory().addItem(WandSVC.createWand());
        sender().playSound(Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1.5f);
        sender().sendMessage(C.GREEN + "Poof! Good luck building!");
    }

    @Decree(name = "x&y", description = "Autoselect up, down & out", sync = true)
    public void xay() {
        if (!WandSVC.isHoldingWand(player())) {
            sender().sendMessage(C.YELLOW + "Hold your wand!");
            return;
        }

        Location[] b = WandSVC.getCuboid(player());
        Location a1 = b[0].clone();
        Location a2 = b[1].clone();
        Location a1x = b[0].clone();
        Location a2x = b[1].clone();
        Cuboid cursor = new Cuboid(a1, a2);
        Cuboid cursorx = new Cuboid(a1, a2);

        while (!cursor.containsOnly(Material.AIR)) {
            a1.add(new org.bukkit.util.Vector(0, 1, 0));
            a2.add(new org.bukkit.util.Vector(0, 1, 0));
            cursor = new Cuboid(a1, a2);
        }

        a1.add(new org.bukkit.util.Vector(0, -1, 0));
        a2.add(new org.bukkit.util.Vector(0, -1, 0));

        while (!cursorx.containsOnly(Material.AIR)) {
            a1x.add(new org.bukkit.util.Vector(0, -1, 0));
            a2x.add(new org.bukkit.util.Vector(0, -1, 0));
            cursorx = new Cuboid(a1x, a2x);
        }

        a1x.add(new org.bukkit.util.Vector(0, 1, 0));
        a2x.add(new Vector(0, 1, 0));
        b[0] = a1;
        b[1] = a2x;
        cursor = new Cuboid(b[0], b[1]);
        cursor = cursor.contract(Cuboid.CuboidDirection.North);
        cursor = cursor.contract(Cuboid.CuboidDirection.South);
        cursor = cursor.contract(Cuboid.CuboidDirection.East);
        cursor = cursor.contract(Cuboid.CuboidDirection.West);
        b[0] = cursor.getLowerNE();
        b[1] = cursor.getUpperSW();
        player().getInventory().setItemInMainHand(WandSVC.createWand(b[0], b[1]));
        player().updateInventory();
        sender().playSound(Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 0.55f);
        sender().sendMessage(C.GREEN + "Auto-select complete!");
    }

    @Decree(name = "x+y", description = "Autoselect up & out", sync = true)
    public void xpy() {
        if (!WandSVC.isHoldingWand(player())) {
            sender().sendMessage(C.YELLOW + "Hold your wand!");
            return;
        }

        Location[] b = WandSVC.getCuboid(player());
        b[0].add(new Vector(0, 1, 0));
        b[1].add(new Vector(0, 1, 0));
        Location a1 = b[0].clone();
        Location a2 = b[1].clone();
        Cuboid cursor = new Cuboid(a1, a2);

        while (!cursor.containsOnly(Material.AIR)) {
            a1.add(new Vector(0, 1, 0));
            a2.add(new Vector(0, 1, 0));
            cursor = new Cuboid(a1, a2);
        }

        a1.add(new Vector(0, -1, 0));
        a2.add(new Vector(0, -1, 0));
        b[0] = a1;
        a2 = b[1];
        cursor = new Cuboid(a1, a2);
        cursor = cursor.contract(Cuboid.CuboidDirection.North);
        cursor = cursor.contract(Cuboid.CuboidDirection.South);
        cursor = cursor.contract(Cuboid.CuboidDirection.East);
        cursor = cursor.contract(Cuboid.CuboidDirection.West);
        b[0] = cursor.getLowerNE();
        b[1] = cursor.getUpperSW();
        player().getInventory().setItemInMainHand(WandSVC.createWand(b[0], b[1]));
        player().updateInventory();
        sender().playSound(Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 0.55f);
        sender().sendMessage(C.GREEN + "Auto-select complete!");
    }
}
