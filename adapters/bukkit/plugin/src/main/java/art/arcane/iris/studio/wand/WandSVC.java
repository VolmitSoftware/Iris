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

package art.arcane.iris.studio.wand;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import art.arcane.iris.Iris;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.studio.edit.DustRevealer;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.world.storage.matter.WorldMatter;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.data.Cuboid;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.scheduling.SR;
import art.arcane.iris.world.task.jobs.Job;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.iris.platform.bukkit.registry.Particles.CRIT_MAGIC;
import static art.arcane.iris.platform.bukkit.registry.Particles.REDSTONE;

public class WandSVC implements IrisService {
    private static final int MS_PER_TICK = Integer.parseInt(System.getProperty("iris.ms_per_tick", "30"));
    private static final int PLAYER_RESCAN_INTERVAL_TICKS = 100;

    private static volatile ItemStack dust;
    private static volatile ItemStack wand;

    private final Map<UUID, Player> activePlayers = new ConcurrentHashMap<>();
    private final AtomicBoolean playerRescanScheduled = new AtomicBoolean(false);
    private volatile boolean enabled;
    private int taskId = -1;
    private int ticksUntilPlayerRescan = 0;

    public static void pasteSchematic(IrisObject s, Location at) {
        s.place(at);
    }

    /**
     * Creates an Iris Object from the 2 coordinates selected with a wand
     *
     * @param p The wand player
     * @return The new object
     */
    public static IrisObject createSchematic(Player p, boolean legacy) {
        if (!isHoldingWand(p)) {
            return null;
        }

        try {
            Location[] f = getCuboid(p);
            if (f == null || f[0] == null || f[1] == null)
                return null;
            Cuboid c = new Cuboid(f[0], f[1]);
            IrisObject s = new IrisObject(c.getSizeX(), c.getSizeY(), c.getSizeZ());

            var it = c.chunkedIterator();

            int total = c.getSizeX() * c.getSizeY() * c.getSizeZ();
            var latch = new CountDownLatch(1);
            var holder = Iris.tickets.getHolder(p.getWorld());
            new Job() {
                private volatile int i;
                private Chunk chunk;

                @Override
                public String getName() {
                    return IrisLanguage.text(RuntimeUiMessages.JOB_SCANNING_SELECTION);
                }

                @Override
                public void execute() {
                    new SR() {
                        @Override
                        public void run() {
                            var time = M.ms() + MS_PER_TICK;
                            while (time > M.ms()) {
                                if (!it.hasNext()) {
                                    if (chunk != null) {
                                        holder.removeTicket(chunk);
                                        chunk = null;
                                    }

                                    cancel();
                                    latch.countDown();
                                    return;
                                }

                                try {
                                    var b = it.next();
                                    var bChunk = b.getChunk();
                                    if (chunk == null) {
                                        chunk = bChunk;
                                        holder.addTicket(chunk);
                                    } else if (chunk != bChunk) {
                                        holder.removeTicket(chunk);
                                        holder.addTicket(bChunk);
                                        chunk = bChunk;
                                    }

                                    if (b.getType().equals(Material.AIR))
                                        continue;

                                    BlockVector bv = b.getLocation().subtract(c.getLowerNE().toVector()).toVector().toBlockVector();
                                    s.setUnsigned(bv.getBlockX(), bv.getBlockY(), bv.getBlockZ(), b, legacy);
                                } finally {
                                    i++;
                                }
                            }
                        }
                    };
                    try {
                        latch.await();
                    } catch (InterruptedException ignored) {}
                }

                @Override
                public void completeWork() {}

                @Override
                public int getTotalWork() {
                    return total;
                }

                @Override
                public int getWorkCompleted() {
                    return i;
                }
            }.execute(new VolmitSender(p), true, () -> {});
            try {
                latch.await();
            } catch (InterruptedException ignored) {}

            return s;
        } catch (Throwable e) {
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
            if (f.length != 2) return null;
            String[] g = f[0].split("\\Q,\\E");
            if (g.length != 3) return null;
            World world = WorldIdentity.resolve(f[1]).orElse(null);
            if (world == null) return null;
            return new Location(world, Integer.parseInt(g[0]), Integer.parseInt(g[1]), Integer.parseInt(g[2]));
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

        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " in " + WorldIdentity.serialize(loc.getWorld());
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
        is.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 1);
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(C.BOLD + "" + C.YELLOW + IrisLanguage.text(RuntimeUiMessages.DUST_NAME));
        im.setUnbreakable(true);
        im.addItemFlags(ItemFlag.values());
        im.setLore(new KList<String>().qadd(IrisLanguage.text(RuntimeUiMessages.DUST_LORE)));
        im.getPersistentDataContainer().set(dustKey(), PersistentDataType.BYTE, (byte) 1);
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
        for (int s = 0; s < inventory.getSize(); s++) {
            ItemStack stack = inventory.getItem(s);
            if (stack != null && isWand(stack)) {
                return s;
            }
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
        is.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 1);
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(C.BOLD + "" + C.GOLD + IrisLanguage.text(RuntimeUiMessages.WAND_NAME));
        im.setUnbreakable(true);
        im.addItemFlags(ItemFlag.values());
        im.setLore(new KList<String>().add(
                a == null ? IrisLanguage.text(RuntimeUiMessages.WAND_LORE_FIRST) : locationToString(a),
                b == null ? IrisLanguage.text(RuntimeUiMessages.WAND_LORE_SECOND) : locationToString(b)
        ));
        im.getPersistentDataContainer().set(wandKey(), PersistentDataType.BYTE, (byte) 1);
        is.setItemMeta(im);

        return is;
    }

    public static Location[] getCuboidFromItem(ItemStack is) {
        if (is == null) {
            return new Location[]{null, null};
        }
        ItemMeta im = is.getItemMeta();
        if (im == null) {
            return new Location[]{null, null};
        }
        List<String> lore = im.getLore();
        if (lore == null || lore.size() < 2) {
            return new Location[]{null, null};
        }
        return new Location[]{stringToLocation(lore.get(0)), stringToLocation(lore.get(1))};
    }

    public static Location[] getCuboid(Player p) {
        if (isHoldingWand(p)) {
            return getCuboidFromItem(p.getInventory().getItemInMainHand());
        }

        return null;
    }

    public static boolean isHoldingWand(Player p) {
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
        if (is == null) {
            return false;
        }
        ItemMeta meta = is.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(wandKey(), PersistentDataType.BYTE);
        if (marker != null && marker.byteValue() == 1) {
            return true;
        }
        ItemStack template = wand;
        if (template == null || !is.getType().equals(template.getType())) {
            return false;
        }
        ItemMeta templateMeta = template.getItemMeta();
        return templateMeta != null
                && Objects.equals(meta.getDisplayName(), templateMeta.getDisplayName())
                && meta.getEnchants().equals(templateMeta.getEnchants())
                && meta.getItemFlags().equals(templateMeta.getItemFlags());
    }

    @Override
    public void onEnable() {
        enabled = true;
        activePlayers.clear();
        ticksUntilPlayerRescan = 0;
        taskId = J.ar(this::tickAll, 1);
        J.s(() -> {
            wand = createWand();
            dust = createDust();
        });
    }

    @Override
    public void onDisable() {
        enabled = false;
        if (taskId != -1) {
            J.car(taskId);
            taskId = -1;
        }
        wand = null;
        dust = null;
        activePlayers.clear();
        playerRescanScheduled.set(false);
    }

    /**
     * Async driver tick. The online player list is only read from the thread that owns it,
     * and every wand draw is dispatched to the thread owning that player.
     */
    private void tickAll() {
        try {
            if (!enabled) {
                return;
            }
            if (ticksUntilPlayerRescan-- <= 0) {
                ticksUntilPlayerRescan = PLAYER_RESCAN_INTERVAL_TICKS;
                rescanPlayers();
            }
            for (Player player : activePlayers.values()) {
                J.runEntity(player, () -> tick(player));
            }
        } catch (Throwable e) {
            Iris.reportError(e);
        }
    }

    public void tick(Player p) {
        try {
            if (!p.isOnline()) {
                activePlayers.remove(p.getUniqueId(), p);
                return;
            }
            Location[] selection = getCuboid(p);
            if (!hasCompleteSelection(selection)) {
                activePlayers.remove(p.getUniqueId(), p);
                return;
            }
            new WandSelection(new Cuboid(selection[0], selection[1]), p).draw();
        } catch (Throwable e) {
            Iris.reportError(e);
        }
    }

    private void rescanPlayers() {
        if (!playerRescanScheduled.compareAndSet(false, true)) {
            return;
        }
        if (!J.runGlobal(() -> {
            try {
                if (!enabled) {
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    J.runEntity(player, () -> refreshPlayer(player));
                }
            } finally {
                playerRescanScheduled.set(false);
            }
        })) {
            playerRescanScheduled.set(false);
        }
    }

    private void refreshPlayer(Player player) {
        if (enabled && player.isOnline() && hasCompleteSelection(getCuboid(player))) {
            activePlayers.put(player.getUniqueId(), player);
            return;
        }
        activePlayers.remove(player.getUniqueId(), player);
    }

    private static boolean hasCompleteSelection(Location[] selection) {
        return selection != null && selection.length >= 2 && selection[0] != null && selection[1] != null;
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
        d[0].getWorld().spawnParticle(CRIT_MAGIC, d[0], 1, 0.5 + gx.getX(), 0.5 + gx.getY(), 0.5 + gx.getZ(), 0, null, false);
        Vector gxx = Vector.getRandom().subtract(Vector.getRandom()).normalize().clone().multiply(0.65);
        d[1].getWorld().spawnParticle(CRIT_MAGIC, d[1], 1, 0.5 + gxx.getX(), 0.5 + gxx.getY(), 0.5 + gxx.getZ(), 0, null, false);

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
                            p.spawnParticle(REDSTONE, lv.getX(), lv.getY(), lv.getZ(), 1, 0, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(r, g, b), 0.75f));
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
                activePlayers.put(e.getPlayer().getUniqueId(), e.getPlayer());
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

    @EventHandler
    public void on(PlayerQuitEvent event) {
        activePlayers.remove(event.getPlayer().getUniqueId());
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
        if (is == null || is.getItemMeta() == null) {
            return false;
        }
        Byte marker = is.getItemMeta().getPersistentDataContainer().get(dustKey(), PersistentDataType.BYTE);
        ItemStack template = dust;
        return (marker != null && marker == (byte) 1) || (template != null && is.isSimilar(template));
    }

    private static NamespacedKey wandKey() {
        return new NamespacedKey(Iris.instance, "wand");
    }

    private static NamespacedKey dustKey() {
        return new NamespacedKey(Iris.instance, "dust");
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

        if (other != null && !WorldIdentity.key(other.getWorld()).equals(WorldIdentity.key(a.getWorld()))) {
            other = null;
        }

        return createWand(left ? a : other, left ? other : a);
    }
}
