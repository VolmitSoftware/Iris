package ninja.bytecode.iris;

import java.awt.Color;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import ninja.bytecode.iris.util.ParticleEffect;
import ninja.bytecode.iris.util.ParticleRedstone;
import ninja.bytecode.iris.util.WandUtil;

public class WandManager implements Listener
{
	@SuppressWarnings("deprecation")
	public WandManager()
	{
		Bukkit.getPluginManager().registerEvents(this, Iris.instance);
		Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, () ->
		{
			for(Player i : Bukkit.getOnlinePlayers())
			{
				tick(i);
			}
			
			for(Chunk i : Iris.refresh)
			{
				i.getWorld().refreshChunk(i.getX(), i.getZ());
			}
			
			Iris.refresh.clear();
		}, 0, 2);
	}

	@EventHandler
	public void tick(Player p)
	{
		try
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
		
		catch(Throwable e)
		{
			
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
				e.getPlayer().playSound(e.getClickedBlock().getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.67f);
				e.getPlayer().updateInventory();
			}

			else if(e.getAction().equals(Action.RIGHT_CLICK_BLOCK))
			{
				e.setCancelled(true);
				e.getPlayer().getInventory().setItemInMainHand(WandUtil.update(false, e.getClickedBlock().getLocation(), e.getPlayer().getInventory().getItemInMainHand()));
				e.getPlayer().playSound(e.getClickedBlock().getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1.17f);
				e.getPlayer().updateInventory();
			}
		}
	}
}
