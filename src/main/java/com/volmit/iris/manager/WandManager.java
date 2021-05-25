package com.volmit.iris.manager;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.edit.DustRevealer;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.awt.Color;
import java.util.Objects;

public class WandManager implements Listener {

	private static ItemStack wand;
	private static ItemStack dust;

	public WandManager()
	{
		wand = createWand();
		dust = createDust();
		Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, () ->
		{
			for(Player i : Bukkit.getOnlinePlayers())
			{
				tick(i);
			}
		}, 0, 5);
	}

	public void tick(Player p)
	{
		try
		{
			if(isWand(p.getInventory().getItemInMainHand()))
			{
				Location[] d = getCuboid(p.getInventory().getItemInMainHand());
				draw(d, p);
			}
		}

		catch(Throwable ignored)
		{

		}
	}

	public void draw(Cuboid d, Player p)
	{
		draw(new Location[] {d.getLowerNE(), d.getUpperSW()}, p);
	}

	public void draw(Location[] d, Player p)
	{
		Vector gx = Vector.getRandom().subtract(Vector.getRandom()).normalize().clone().multiply(0.65);
		d[0].getWorld().spawnParticle(Particle.CRIT_MAGIC, d[0], 1, 0.5 + gx.getX(), 0.5 + gx.getY(), 0.5 + gx.getZ(), 0, null, false);
		Vector gxx = Vector.getRandom().subtract(Vector.getRandom()).normalize().clone().multiply(0.65);
		d[1].getWorld().spawnParticle(Particle.CRIT, d[1], 1, 0.5 + gxx.getX(), 0.5 + gxx.getY(), 0.5 + gxx.getZ(), 0, null, false);

		if(!d[0].getWorld().equals(d[1].getWorld()))
		{
			return;
		}

		if(d[0].distanceSquared(d[1]) > 64 * 64)
		{
			return;
		}

		int minx = Math.min(d[0].getBlockX(), d[1].getBlockX());
		int miny = Math.min(d[0].getBlockY(), d[1].getBlockY());
		int minz = Math.min(d[0].getBlockZ(), d[1].getBlockZ());
		int maxx = Math.max(d[0].getBlockX(), d[1].getBlockX());
		int maxy = Math.max(d[0].getBlockY(), d[1].getBlockY());
		int maxz = Math.max(d[0].getBlockZ(), d[1].getBlockZ());

		for(double j = minx - 1; j < maxx + 1; j += 0.25)
		{
			for(double k = miny - 1; k < maxy + 1; k += 0.25)
			{
				for(double l = minz - 1; l < maxz + 1; l += 0.25)
				{
					if(M.r(0.2))
					{
						boolean jj = j == minx || j == maxx;
						boolean kk = k == miny || k == maxy;
						boolean ll = l == minz || l == maxz;

						if((jj && kk) || (jj && ll) || (ll && kk))
						{
							Vector push = new Vector(0, 0, 0);

							if(j == minx)
							{
								push.add(new Vector(-0.55, 0, 0));
							}

							if(k == miny)
							{
								push.add(new Vector(0, -0.55, 0));
							}

							if(l == minz)
							{
								push.add(new Vector(0, 0, -0.55));
							}

							if(j == maxx)
							{
								push.add(new Vector(0.55, 0, 0));
							}

							if(k == maxy)
							{
								push.add(new Vector(0, 0.55, 0));
							}

							if(l == maxz)
							{
								push.add(new Vector(0, 0, 0.55));
							}

							Location lv = new Location(d[0].getWorld(), j, k, l).clone().add(0.5, 0.5, 0.5).clone().add(push);
							Color color = Color.getHSBColor((float) (0.5f + (Math.sin((j + k + l + (p.getTicksLived() / 2)) / 20f) / 2)), 1, 1);
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
	public void on(PlayerInteractEvent e)
	{
		try
		{
			if(isWand(e.getPlayer()))
			{
				if(e.getAction().equals(Action.LEFT_CLICK_BLOCK))
				{
					e.setCancelled(true);
					e.getPlayer().getInventory().setItemInMainHand(update(true, Objects.requireNonNull(e.getClickedBlock()).getLocation(), e.getPlayer().getInventory().getItemInMainHand()));
					e.getPlayer().playSound(e.getClickedBlock().getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.67f);
					e.getPlayer().updateInventory();
				}

				else if(e.getAction().equals(Action.RIGHT_CLICK_BLOCK))
				{
					e.setCancelled(true);
					e.getPlayer().getInventory().setItemInMainHand(update(false, Objects.requireNonNull(e.getClickedBlock()).getLocation(), e.getPlayer().getInventory().getItemInMainHand()));
					e.getPlayer().playSound(e.getClickedBlock().getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1.17f);
					e.getPlayer().updateInventory();
				}
			}

			if(isDust(e.getPlayer()))
			{
				if(e.getAction().equals(Action.RIGHT_CLICK_BLOCK))
				{
					e.setCancelled(true);
					e.getPlayer().playSound(Objects.requireNonNull(e.getClickedBlock()).getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 2f, 1.97f);
					DustRevealer.spawn(e.getClickedBlock(), new MortarSender(e.getPlayer(), Iris.instance.getTag()));

				}
			}
		}

		catch(Throwable ignored)
		{

		}
	}

	public static void pasteSchematic(IrisObject s, Location at)
	{
		s.place(at);
	}

	public static IrisObject createSchematic(ItemStack wand)
	{
		if(!isWand(wand))
		{
			return null;
		}

		try
		{
			Location[] f = getCuboid(wand);
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
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public static Location stringToLocation(String s)
	{
		try
		{
			String[] f = s.split("\\Q in \\E");
			String[] g = f[0].split("\\Q,\\E");
			return new Location(Bukkit.getWorld(f[1]), Integer.parseInt(g[0]), Integer.parseInt(g[1]), Integer.parseInt(g[2]));
		}

		catch(Throwable e)
		{
			return null;
		}
	}

	public static String locationToString(Location s)
	{
		if(s == null)
		{
			return "<#>";
		}

		return s.getBlockX() + "," + s.getBlockY() + "," + s.getBlockZ() + " in " + s.getWorld().getName();
	}

	public static ItemStack createWand()
	{
		return createWand(null, null);
	}

	public static ItemStack createDust()
	{
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

	public boolean isDust(Player p)
	{
		ItemStack is = p.getInventory().getItemInMainHand();
		return is != null && isDust(is);
	}

	public boolean isDust(ItemStack is)
	{
		return is.equals(dust);
	}

	public ItemStack update(boolean left, Location a, ItemStack item)
	{
		if(!isWand(item))
		{
			return item;
		}

		Location[] f = getCuboid(item);
		Location other = left ? f[1] : f[0];

		if(other != null && !other.getWorld().getName().equals(a.getWorld().getName()))
		{
			other = null;
		}

		return createWand(left ? a : other, left ? other : a);
	}

	public static ItemStack createWand(Location a, Location b)
	{
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

	public static Location[] getCuboid(ItemStack is)
	{
		ItemMeta im = is.getItemMeta();
		return new Location[] {stringToLocation(im.getLore().get(0)), stringToLocation(im.getLore().get(1))};
	}

	public static boolean isWand(Player p)
	{
		ItemStack is = p.getInventory().getItemInMainHand();
		return is != null && isWand(is);
	}

	public static boolean isWand(ItemStack is)
	{
		ItemStack wand = createWand();
		if (is.getItemMeta() == null) return false;
		return is.getType().equals(wand.getType()) &&
				is.getItemMeta().getDisplayName().equals(wand.getItemMeta().getDisplayName()) &&
				is.getItemMeta().getEnchants().equals(wand.getItemMeta().getEnchants()) &&
				is.getItemMeta().getItemFlags().equals(wand.getItemMeta().getItemFlags());
	}
}
