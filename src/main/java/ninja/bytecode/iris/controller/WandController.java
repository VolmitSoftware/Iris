package ninja.bytecode.iris.controller;

import java.awt.Color;
import java.util.Iterator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import mortar.compute.math.M;
import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.genobject.GenObject;
import ninja.bytecode.iris.generator.genobject.GenObjectGroup;
import ninja.bytecode.iris.generator.genobject.PlacedObject;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.Cuboid;
import ninja.bytecode.iris.util.IrisController;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.ParticleEffect;
import ninja.bytecode.iris.util.ParticleRedstone;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.format.Form;

public class WandController implements IrisController
{
	private KMap<String, GenObject> goc;
	private KMap<String, GenObjectGroup> gog;

	@Override
	public void onStart()
	{
		goc = new KMap<>();
		gog = new KMap<>();
		// TODO: Optimize
		Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, () ->
		{
			for(Player i : Bukkit.getOnlinePlayers())
			{
				tick(i);
			}
		}, 0, 5);
	}

	@Override
	public void onStop()
	{

	}

	@EventHandler
	public void tick(Player p)
	{
		try
		{
			if(isWand(p.getInventory().getItemInMainHand()))
			{
				if(Iris.settings.performance.debugMode && p.getWorld().getGenerator() instanceof IrisGenerator)
				{
					tickHighlight(p, (IrisGenerator) p.getWorld().getGenerator());
				}

				Location[] d = getCuboid(p.getInventory().getItemInMainHand());
				draw(d, p);
			}
		}

		catch(Throwable e)
		{

		}
	}

	private void draw(Location[] d, Player p)
	{
		ParticleEffect.CRIT_MAGIC.display(0.1f, 1, d[0].clone().add(0.5, 0.5, 0.5).clone().add(Vector.getRandom().subtract(Vector.getRandom()).normalize().clone().multiply(0.65)), p);
		ParticleEffect.CRIT.display(0.1f, 1, d[1].clone().add(0.5, 0.5, 0.5).clone().add(Vector.getRandom().subtract(Vector.getRandom()).normalize().clone().multiply(0.65)), p);

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
					if(M.r(0.25))
					{
						boolean jj = j == minx || j == maxx;
						boolean kk = k == miny || k == maxy;
						boolean ll = l == minz || l == maxz;
						double aa = j;
						double bb = k;
						double cc = l;

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

							Location lv = new Location(d[0].getWorld(), aa, bb, cc).clone().add(0.5, 0.5, 0.5).clone().add(push);
							int color = Color.getHSBColor((float) (0.5f + (Math.sin((aa + bb + cc + (p.getTicksLived() / 2)) / 20f) / 2)), 1, 1).getRGB();
							new ParticleRedstone().setColor(new Color(color)).play(lv, p);
						}
					}
				}
			}
		}
	}

	private void tickHighlight(Player p, IrisGenerator generator)
	{
		Location l = p.getTargetBlock(null, 32).getLocation();
		PlacedObject po = generator.nearest(l, 12);

		if(po != null)
		{
			if(!goc.containsKey(po.getF()))
			{
				String root = po.getF().split("\\Q:\\E")[0];
				String n = po.getF().split("\\Q:\\E")[1];
				GenObjectGroup gg = generator.getDimension().getObjectGroup(root);
				gog.put(root, gg);

				for(GenObject i : gg.getSchematics())
				{
					if(i.getName().equals(n))
					{
						goc.put(po.getF(), i);
						break;
					}
				}

				if(!goc.containsKey(po.getF()))
				{
					goc.put(po.getF(), new GenObject(0, 0, 0));
				}
			}

			GenObjectGroup ggg = gog.get(po.getF().split("\\Q:\\E")[0]);
			GenObject g = goc.get(po.getF());

			if(g != null)
			{
				Location point = new Location(l.getWorld(), po.getX(), po.getY(), po.getZ());
				IrisBiome biome = generator.getBiome((int) generator.getOffsetX(po.getX(), po.getZ()), (int) generator.getOffsetZ(po.getX(), po.getZ()));
				String gg = po.getF().split("\\Q:\\E")[0];

				for(int j = 0; j < 10; j++)
				{
					p.sendMessage(" ");
				}

				p.sendMessage(C.DARK_GREEN + C.BOLD.toString() + gg + C.GRAY + "/" + C.RESET + C.ITALIC + C.GRAY + g.getName() + C.RESET + C.WHITE + " (1 of " + Form.f(generator.getDimension().getObjectGroup(gg).size()) + " variants)");

				if(biome.getSchematicGroups().containsKey(gg))
				{
					String f = "";
					double percent = biome.getSchematicGroups().get(gg);

					if(percent > 1D)
					{
						f = (int) percent + " + " + Form.pc(percent - (int) percent, percent - (int) percent >= 0.01 ? 0 : 3);
					}

					else
					{
						f = Form.pc(percent, percent >= 0.01 ? 0 : 3);
					}

					p.sendMessage(C.GOLD + "Spawn Chance in " + C.YELLOW + biome.getName() + C.RESET + ": " + C.BOLD + C.WHITE + f);
				}

				try
				{
					int a = 0;
					int b = 0;
					double c = 0;

					for(GenObject i : ggg.getSchematics())
					{
						a += i.getSuccesses();
						b += i.getPlaces();
					}

					c = ((double) a / (double) b);
					p.sendMessage(C.GRAY + "Grp: " + C.DARK_AQUA + Form.f(a) + C.GRAY + " of " + C.AQUA + Form.f(b) + C.GRAY + " placements (" + C.DARK_AQUA + Form.pc(c, 0) + C.GRAY + ")");
				}

				catch(Throwable e)
				{
					e.printStackTrace();
				}

				p.sendMessage(C.GRAY + "Var: " + C.DARK_AQUA + Form.f(g.getSuccesses()) + C.GRAY + " of " + C.AQUA + Form.f(g.getPlaces()) + C.GRAY + " placements (" + C.DARK_AQUA + Form.pc(g.getSuccess(), 0) + C.GRAY + ")");

				for(String i : ggg.getFlags())
				{
					p.sendMessage(C.GRAY + "- " + C.DARK_PURPLE + i);
				}

				draw(new Location[] {point.clone().add(g.getW() / 2, g.getH() / 2, g.getD() / 2), point.clone().subtract(g.getW() / 2, g.getH() / 2, g.getD() / 2)
				}, p);
			}
		}
	}

	@EventHandler
	public void on(PlayerInteractEvent e)
	{
		if(e.getHand().equals(EquipmentSlot.HAND) && isWand(e.getPlayer().getInventory().getItemInMainHand()))
		{
			if(e.getAction().equals(Action.LEFT_CLICK_BLOCK))
			{
				e.setCancelled(true);
				e.getPlayer().getInventory().setItemInMainHand(update(true, e.getClickedBlock().getLocation(), e.getPlayer().getInventory().getItemInMainHand()));
				e.getPlayer().playSound(e.getClickedBlock().getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.67f);
				e.getPlayer().updateInventory();
			}

			else if(e.getAction().equals(Action.RIGHT_CLICK_BLOCK))
			{
				e.setCancelled(true);
				e.getPlayer().getInventory().setItemInMainHand(update(false, e.getClickedBlock().getLocation(), e.getPlayer().getInventory().getItemInMainHand()));
				e.getPlayer().playSound(e.getClickedBlock().getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1.17f);
				e.getPlayer().updateInventory();
			}
		}
	}

	public static void pasteSchematic(GenObject s, Location at)
	{
		s.place(at);
	}

	@SuppressWarnings("deprecation")
	public static GenObject createSchematic(ItemStack wand, Location at)
	{
		if(!isWand(wand))
		{
			return null;
		}

		try
		{
			Location[] f = getCuboid(wand);
			Cuboid c = new Cuboid(f[0], f[1]);
			GenObject s = new GenObject(c.getSizeX(), c.getSizeY(), c.getSizeZ());
			Iterator<Block> bb = c.iterator();
			while(bb.hasNext())
			{
				Block b = bb.next();

				if(b.getType().equals(Material.AIR))
				{
					continue;
				}

				byte data = b.getData();

				BlockVector bv = b.getLocation().subtract(c.getCenter()).toVector().toBlockVector();
				s.put(bv.getBlockX(), bv.getBlockY(), bv.getBlockZ(),

						new MB(b.getType(), data));
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
			return new Location(Bukkit.getWorld(f[1]), Integer.valueOf(g[0]), Integer.valueOf(g[1]), Integer.valueOf(g[2]));
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

	public static ItemStack update(boolean left, Location a, ItemStack item)
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

	public void dispose()
	{
		goc.clear();
		gog.clear();
	}

	public static ItemStack createWand(Location a, Location b)
	{
		ItemStack is = new ItemStack(Material.BLAZE_ROD);
		is.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1);
		ItemMeta im = is.getItemMeta();
		im.setDisplayName(ChatColor.BOLD + "" + ChatColor.GOLD + "Wand of Iris");
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

	public static boolean isWand(ItemStack item)
	{
		if(!item.getType().equals(createWand().getType()))
		{
			return false;
		}

		if(!item.getItemMeta().getEnchants().equals(createWand().getItemMeta().getEnchants()))
		{
			return false;
		}

		if(!item.getItemMeta().getDisplayName().equals(createWand().getItemMeta().getDisplayName()))
		{
			return false;
		}

		return true;
	}
}
