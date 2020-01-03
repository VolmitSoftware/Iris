package ninja.bytecode.iris;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import ninja.bytecode.iris.schematic.Schematic;
import ninja.bytecode.iris.util.WandUtil;

public class WandManager implements Listener
{
	public WandManager()
	{
		Bukkit.getPluginManager().registerEvents(this, Iris.instance);
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
				e.getPlayer().getInventory().setItemInMainHand(WandUtil.update(true, e.getPlayer().getLocation(), e.getPlayer().getInventory().getItemInMainHand()));

				e.getPlayer().updateInventory();
			}

			else if(e.getAction().equals(Action.RIGHT_CLICK_BLOCK))
			{
				e.setCancelled(true);
				e.getPlayer().getInventory().setItemInMainHand(WandUtil.update(false, e.getPlayer().getLocation(), e.getPlayer().getInventory().getItemInMainHand()));

				e.getPlayer().updateInventory();
			}
		}
	}
}
