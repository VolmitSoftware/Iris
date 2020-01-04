package ninja.bytecode.iris;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import ninja.bytecode.iris.schematic.Schematic;
import ninja.bytecode.iris.util.Cuboid;
import ninja.bytecode.iris.util.Cuboid.CuboidDirection;
import ninja.bytecode.iris.util.ParticleEffect;
import ninja.bytecode.iris.util.ParticleRedstone;
import ninja.bytecode.iris.util.WandUtil;

public class WandManager implements Listener
{
	public WandManager()
	{
		Bukkit.getPluginManager().registerEvents(this, Iris.instance);
		Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, () ->
		{
			for(Player i : Bukkit.getOnlinePlayers())
			{
				tick(i);
			}
		}, 0, 4);
	}

	@EventHandler
	public void tick(Player p)
	{
		if(WandUtil.isWand(p.getInventory().getItemInMainHand()))
		{
			Location[] d = WandUtil.getCuboid(p.getInventory().getItemInMainHand());
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

	@EventHandler
	public void on(PlayerCommandPreprocessEvent e)
	{
		if(e.getMessage().startsWith("/isave "))
		{
			e.setCancelled(true);
			Schematic s = WandUtil.createSchematic(e.getPlayer().getInventory().getItemInMainHand(), e.getPlayer().getLocation());
			File f = new File(Iris.instance.getDataFolder(), "schematics/" + e.getMessage().split("\\Q \\E")[1] + ".ish");
			f.getParentFile().mkdirs();
			try
			{
				FileOutputStream fos = new FileOutputStream(f);
				s.write(fos);
				e.getPlayer().sendMessage("Done!");
			}

			catch(Throwable e1)
			{
				e1.printStackTrace();
			}
		}

		if(e.getMessage().startsWith("/iload "))
		{
			e.setCancelled(true);
			Schematic s = new Schematic(1, 1, 1, 1, 1, 1);
			File f = new File(Iris.instance.getDataFolder(), "schematics/" + e.getMessage().split("\\Q \\E")[1] + ".ish");
			if(!f.exists())
			{
				e.getPlayer().sendMessage("Not Found");
				return;
			}

			try
			{
				FileInputStream fin = new FileInputStream(f);
				s.read(fin);
				WandUtil.pasteSchematic(s, e.getPlayer().getLocation());
				e.getPlayer().sendMessage("Done!");
			}

			catch(Throwable e1)
			{
				e1.printStackTrace();
			}
		}
		
		if(e.getMessage().startsWith("/iup"))
		{
			e.setCancelled(true);
			Location[] b = WandUtil.getCuboid(e.getPlayer().getInventory().getItemInMainHand());
			b[0].add(new Vector(0, 1, 0));
			b[1].add(new Vector(0, 1, 0));
			Location a1 = b[0].clone();
			Location a2 = b[1].clone();
			Cuboid cursor = new Cuboid(a1, a2);

			while(!cursor.containsOnly(Material.AIR))
			{
				a1.add(new Vector(0, 1, 0));
				a2.add(new Vector(0, 1, 0));
				cursor = new Cuboid(a1, a2);
			}
			
			a1.add(new Vector(0, -1, 0));
			a2.add(new Vector(0, -1, 0));
			b[0] = a1;
			a2 = b[1];
			cursor = new Cuboid(a1, a2);
			cursor = cursor.contract(CuboidDirection.North);
			cursor = cursor.contract(CuboidDirection.South);
			cursor = cursor.contract(CuboidDirection.East);
			cursor = cursor.contract(CuboidDirection.West);
			b[0] = cursor.getLowerNE();
			b[1] = cursor.getUpperSW();
			e.getPlayer().getInventory().setItemInMainHand(WandUtil.createWand(b[0], b[1]));
			e.getPlayer().updateInventory();
		}
		
		if(e.getMessage().startsWith("/ivert"))
		{
			e.setCancelled(true);
			Location[] b = WandUtil.getCuboid(e.getPlayer().getInventory().getItemInMainHand());

			Location a1 = b[0].clone();
			Location a2 = b[1].clone();
			Location a1x = b[0].clone();
			Location a2x = b[1].clone();
			Cuboid cursor = new Cuboid(a1, a2);
			Cuboid cursorx = new Cuboid(a1, a2);

			while(!cursor.containsOnly(Material.AIR))
			{
				a1.add(new Vector(0, 1, 0));
				a2.add(new Vector(0, 1, 0));
				cursor = new Cuboid(a1, a2);
			}
			
			a1.add(new Vector(0, -1, 0));
			a2.add(new Vector(0, -1, 0));
			
			while(!cursorx.containsOnly(Material.AIR))
			{
				a1x.add(new Vector(0, -1, 0));
				a2x.add(new Vector(0, -1, 0));
				cursorx = new Cuboid(a1x, a2x);
			}
			
			a1x.add(new Vector(0, 1, 0));
			a2x.add(new Vector(0, 1, 0));
			b[0] = a1;
			b[1] = a2x;
			cursor = new Cuboid(b[0], b[1]);
			cursor = cursor.contract(CuboidDirection.North);
			cursor = cursor.contract(CuboidDirection.South);
			cursor = cursor.contract(CuboidDirection.East);
			cursor = cursor.contract(CuboidDirection.West);
			b[0] = cursor.getLowerNE();
			b[1] = cursor.getUpperSW();
			e.getPlayer().getInventory().setItemInMainHand(WandUtil.createWand(b[0], b[1]));
			e.getPlayer().updateInventory();
		}

		if(e.getMessage().equals("/iris wand"))
		{
			e.setCancelled(true);
			e.getPlayer().getInventory().addItem(WandUtil.createWand());
		}
	}

	@EventHandler
	public void on(PlayerInteractEvent e)
	{
		if(e.getHand().equals(EquipmentSlot.HAND) && WandUtil.isWand(e.getPlayer().getInventory().getItemInMainHand()))
		{
			if(e.getAction().equals(Action.LEFT_CLICK_BLOCK))
			{
				e.setCancelled(true);
				e.getPlayer().getInventory().setItemInMainHand(WandUtil.update(true, e.getClickedBlock().getLocation(), e.getPlayer().getInventory().getItemInMainHand()));

				e.getPlayer().updateInventory();
			}

			else if(e.getAction().equals(Action.RIGHT_CLICK_BLOCK))
			{
				e.setCancelled(true);
				e.getPlayer().getInventory().setItemInMainHand(WandUtil.update(false, e.getClickedBlock().getLocation(), e.getPlayer().getInventory().getItemInMainHand()));

				e.getPlayer().updateInventory();
			}
		}
	}
}
