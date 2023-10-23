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

package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.edit.DustRevealer;
import com.volmit.iris.core.link.WorldEditLink;
import com.volmit.iris.core.wand.WandSelection;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.Cuboid;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.matter.Matter;
import com.volmit.iris.util.matter.WorldMatter;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Objects;

public class WandSVC implements IrisService {
    private static ItemStack dust;

    public static void pasteSchematic(IrisObject s, Location at) {
        s.place(at);
    }

    /**
     * Creates an Iris Object from the 2 coordinates selected with a wand
     *
     * @param p The wand player
     * @return The new object
     */
    public static IrisObject createSchematic(Player p) {
        if (!isHoldingWand(p)) {
            return null;
        }

        try {
            Location[] f = getCuboid(p);
            Cuboid c = new Cuboid(f[0], f[1]);
            IrisObject s = new IrisObject(c.getSizeX(), c.getSizeY(), c.getSizeZ());
            for (Block b : c) {
                if (b.getType().equals(Material.AIR)) {
                    continue;
                }

                BlockVector bv = b.getLocation().subtract(c.getLowerNE().toVector()).toVector().toBlockVector();
                s.setUnsigned(bv.getBlockX(), bv.getBlockY(), bv.getBlockZ(), b);
            }

            return s;
        } catch (Throwable e) {
            e.printStackTrace();
            Iris.reportError(e);
        }

        return null;
    }

    /**
     * Creates an Iris Object from the 2 coordinates selected with a wand
     *
     * @return The new object
     */
    public static Matter createMatterSchem(Player p) {
        if (!isHoldingWand(p)) {
            return null;
        }

        try {
            Location[] f = getCuboid(p);

            return WorldMatter.createMatter(p.getName(), f[0], f[1]);
        } catch (Throwable e) {
            e.printStackTrace();
            Iris.reportError(e);
        }

        return null;
    }

    /**
     * Converts a user friendly location string to an actual Location
     *
     * @param s The string
     * @return The location
     */
    public static Location stringToLocation(String s) {
        try {
            String[] f = s.split("\\Q in \\E");
            String[] g = f[0].split("\\Q,\\E");
            return new Location(Bukkit.getWorld(f[1]), Integer.parseInt(g[0]), Integer.parseInt(g[1]), Integer.parseInt(g[2]));
        } catch (Throwable e) {
            Iris.reportError(e);
            return null;
        }
    }

    /**
     * Get a user friendly string of a location
     *
     * @param loc The location
     * @return The string
     */
    public static String locationToString(Location loc) {
        if (loc == null) {
            return "<#>";
        }

        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " in " + loc.getWorld().getName();
    }

    /**
     * Create a new blank Iris wand
     *
     * @return The wand itemstack
     */
    public static ItemStack createWand() {
        return createWand(null, null);
    }

    /**
     * Create a new dust itemstack
     *
     * @return The stack
     */
    public static ItemStack createDust() {
        ItemStack is = new ItemStack(Material.GLOWSTONE_DUST);
        is.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1);
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(C.BOLD + "" + C.YELLOW + "Dust of Revealing");
        im.setUnbreakable(true);
        im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS);
        im.setLore(new KList<String>().qadd("Right click on a block to reveal it's placement structure!"));
        is.setItemMeta(im);

        return is;
    }

    /**
     * Finds an existing wand in a users inventory
     *
     * @param inventory The inventory to search
     * @return The slot number the wand is in. Or -1 if none are found
     */
    public static int findWand(Inventory inventory) {
        ItemStack wand = createWand(); //Create blank wand
        ItemMeta meta = wand.getItemMeta();
        meta.setLore(new ArrayList<>()); //We are resetting the lore as the lore differs between wands
        wand.setItemMeta(meta);

        for (int s = 0; s < inventory.getSize(); s++) {
            ItemStack stack = inventory.getItem(s);
            if (stack == null) continue;
            meta = stack.getItemMeta();
            meta.setLore(new ArrayList<>()); //Reset the lore on this too so we can compare them
            stack.setItemMeta(meta);         //We dont need to clone the item as items from .get are cloned

            if (wand.isSimilar(stack)) return s; //If the name, material and NBT is the same
        }
        return -1;
    }

    /**
     * Creates an Iris wand. The locations should be the currently selected locations, or null
     *
     * @param a Location A
     * @param b Location B
     * @return A new wand
     */
    public static ItemStack createWand(Location a, Location b) {
        ItemStack is = new ItemStack(Material.BLAZE_ROD);
        is.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1);
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(C.BOLD + "" + C.GOLD + "Wand of Iris");
        im.setUnbreakable(true);
        im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS);
        im.setLore(new KList<String>().add(locationToString(a), locationToString(b)));
        is.setItemMeta(im);

        return is;
    }

    public static Location[] getCuboidFromItem(ItemStack is) {
        ItemMeta im = is.getItemMeta();
        return new Location[]{stringToLocation(im.getLore().get(0)), stringToLocation(im.getLore().get(1))};
    }

    public static Location[] getCuboid(Player p) {
        if (isHoldingIrisWand(p)) {
            return getCuboidFromItem(p.getInventory().getItemInMainHand());
        }

        Cuboid c = WorldEditLink.getSelection(p);

        if (c != null) {
            return new Location[]{c.getLowerNE(), c.getUpperSW()};
        }

        return null;
    }

    public static boolean isHoldingWand(Player p) {
        return isHoldingIrisWand(p) || WorldEditLink.getSelection(p) != null;
    }

    public static boolean isHoldingIrisWand(Player p) {
        ItemStack is = p.getInventory().getItemInMainHand();
        return is != null && isWand(is);
    }

    /**
     * Is the itemstack passed an Iris wand
     *
     * @param is The itemstack
     * @return True if it is
     */
    public static boolean isWand(ItemStack is) {
        ItemStack wand = createWand();
        if (is.getItemMeta() == null) return false;
        return is.getType().equals(wand.getType()) &&
                is.getItemMeta().getDisplayName().equals(wand.getItemMeta().getDisplayName()) &&
                is.getItemMeta().getEnchants().equals(wand.getItemMeta().getEnchants()) &&
                is.getItemMeta().getItemFlags().equals(wand.getItemMeta().getItemFlags());
    }

    @Override
    public void onEnable() {
        ItemStack wand = createWand();
        dust = createDust();

        J.ar(() -> {
            for (Player i : Bukkit.getOnlinePlayers()) {
                tick(i);
            }
        }, 0);
    }

    @Override
    public void onDisable() {

    }

    public void tick(Player p) {
        try {
            try {
                if ((IrisSettings.get().getWorld().worldEditWandCUI && isHoldingWand(p)) || isWand(p.getInventory().getItemInMainHand())) {
                    Location[] d = getCuboid(p);
                    new WandSelection(new Cuboid(d[0], d[1]), p).draw();
                }
            } catch (Throwable e) {
                Iris.reportError(e);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Draw the outline of a selected region
     *
     * @param d The cuboid
     * @param p The player to show it to
     */
    public void draw(Cuboid d, Player p) {
        draw(new Location[]{d.getLowerNE(), d.getUpperSW()}, p);
    }

    /**
     * Draw the outline of a selected region
     *
     * @param d A pair of locations
     * @param p The player to show them to
     */
    public void draw(Location[] d, Player p) {
        Vector gx = Vector.getRandom().subtract(Vector.getRandom()).normalize().clone().multiply(0.65);
        d[0].getWorld().spawnParticle(Particle.CRIT_MAGIC, d[0], 1, 0.5 + gx.getX(), 0.5 + gx.getY(), 0.5 + gx.getZ(), 0, null, false);
        Vector gxx = Vector.getRandom().subtract(Vector.getRandom()).normalize().clone().multiply(0.65);
        d[1].getWorld().spawnParticle(Particle.CRIT, d[1], 1, 0.5 + gxx.getX(), 0.5 + gxx.getY(), 0.5 + gxx.getZ(), 0, null, false);

        if (!d[0].getWorld().equals(d[1].getWorld())) {
            return;
        }

        if (d[0].distanceSquared(d[1]) > 64 * 64) {
            return;
        }

        int minx = Math.min(d[0].getBlockX(), d[1].getBlockX());
        int miny = Math.min(d[0].getBlockY(), d[1].getBlockY());
        int minz = Math.min(d[0].getBlockZ(), d[1].getBlockZ());
        int maxx = Math.max(d[0].getBlockX(), d[1].getBlockX());
        int maxy = Math.max(d[0].getBlockY(), d[1].getBlockY());
        int maxz = Math.max(d[0].getBlockZ(), d[1].getBlockZ());

        for (double j = minx - 1; j < maxx + 1; j += 0.25) {
            for (double k = miny - 1; k < maxy + 1; k += 0.25) {
                for (double l = minz - 1; l < maxz + 1; l += 0.25) {
                    if (M.r(0.2)) {
                        boolean jj = j == minx || j == maxx;
                        boolean kk = k == miny || k == maxy;
                        boolean ll = l == minz || l == maxz;

                        if ((jj && kk) || (jj && ll) || (ll && kk)) {
                            Vector push = new Vector(0, 0, 0);

                            if (j == minx) {
                                push.add(new Vector(-0.55, 0, 0));
                            }

                            if (k == miny) {
                                push.add(new Vector(0, -0.55, 0));
                            }

                            if (l == minz) {
                                push.add(new Vector(0, 0, -0.55));
                            }

                            if (j == maxx) {
                                push.add(new Vector(0.55, 0, 0));
                            }

                            if (k == maxy) {
                                push.add(new Vector(0, 0.55, 0));
                            }

                            if (l == maxz) {
                                push.add(new Vector(0, 0, 0.55));
                            }

                            Location lv = new Location(d[0].getWorld(), j, k, l).clone().add(0.5, 0.5, 0.5).clone().add(push);
                            Color color = Color.getHSBColor((float) (0.5f + (Math.sin((j + k + l + (p.getTicksLived() / 2f)) / 20f) / 2)), 1, 1);
                            int r = color.getRed();
                            int g = color.getGreen();
                            int b = color.getBlue();
                            p.spawnParticle(Particle.REDSTONE, lv.getX(), lv.getY(), lv.getZ(), 1, 0, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(r, g, b), 0.75f));
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND)
            return;
        try {
            if (isHoldingWand(e.getPlayer())) {
                if (e.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
                    e.setCancelled(true);
                    e.getPlayer().getInventory().setItemInMainHand(update(true, Objects.requireNonNull(e.getClickedBlock()).getLocation(), e.getPlayer().getInventory().getItemInMainHand()));
                    e.getPlayer().playSound(e.getClickedBlock().getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.67f);
                    e.getPlayer().updateInventory();
                } else if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
                    e.setCancelled(true);
                    e.getPlayer().getInventory().setItemInMainHand(update(false, Objects.requireNonNull(e.getClickedBlock()).getLocation(), e.getPlayer().getInventory().getItemInMainHand()));
                    e.getPlayer().playSound(e.getClickedBlock().getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1.17f);
                    e.getPlayer().updateInventory();
                }
            }

            if (isHoldingDust(e.getPlayer())) {
                if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
                    e.setCancelled(true);
                    e.getPlayer().playSound(Objects.requireNonNull(e.getClickedBlock()).getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 2f, 1.97f);
                    DustRevealer.spawn(e.getClickedBlock(), new VolmitSender(e.getPlayer(), Iris.instance.getTag()));
                }
            }
        } catch (Throwable xx) {
            Iris.reportError(xx);
        }
    }

    /**
     * Is the player holding Dust?
     *
     * @param p The player
     * @return True if they are
     */
    public boolean isHoldingDust(Player p) {
        ItemStack is = p.getInventory().getItemInMainHand();
        return is != null && isDust(is);
    }

    /**
     * Is the itemstack passed Iris dust?
     *
     * @param is The itemstack
     * @return True if it is
     */
    public boolean isDust(ItemStack is) {
        return is.isSimilar(dust);
    }

    /**
     * Update the location on an Iris wand
     *
     * @param left True for first location, false for second
     * @param a    The location
     * @param item The wand
     * @return The updated wand
     */
    public ItemStack update(boolean left, Location a, ItemStack item) {
        if (!isWand(item)) {
            return item;
        }

        Location[] f = getCuboidFromItem(item);
        Location other = left ? f[1] : f[0];

        if (other != null && !other.getWorld().getName().equals(a.getWorld().getName())) {
            other = null;
        }

        return createWand(left ? a : other, left ? other : a);
    }
}
