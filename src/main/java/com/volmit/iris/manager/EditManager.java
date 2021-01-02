package com.volmit.iris.manager;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.edit.BlockEditor;
import com.volmit.iris.manager.edit.BukkitBlockEditor;
import com.volmit.iris.manager.edit.WEBlockEditor;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.M;

import io.papermc.lib.PaperLib;

public class EditManager implements Listener
{
	private KMap<World, BlockEditor> editors;

	public EditManager()
	{
		this.editors = new KMap<>();
		Iris.instance.registerListener(this);
		Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, this::update, 0, 0);
	}

	public BlockData get(World world, int x, int y, int z)
	{
		return open(world).get(x, y, z);
	}

	public void set(World world, int x, int y, int z, BlockData d)
	{
		open(world).set(x, y, z, d);
	}

	public void setBiome(World world, int x, int y, int z, Biome d)
	{
		open(world).setBiome(x, y, z, d);
	}

	public void setBiome(World world, int x, int z, Biome d)
	{
		open(world).setBiome(x, z, d);
	}

	public Biome getBiome(World world, int x, int y, int z)
	{
		return open(world).getBiome(x, y, z);
	}

	public Biome getBiome(World world, int x, int z)
	{
		return open(world).getBiome(x, z);
	}

	@EventHandler
	public void on(WorldUnloadEvent e)
	{
		if(editors.containsKey(e.getWorld()))
		{
			editors.remove(e.getWorld()).close();
		}
	}

	public void update()
	{
		for(World i : editors.k())
		{
			if(M.ms() - editors.get(i).last() > 1000)
			{
				editors.remove(i).close();
			}
		}
	}

	public void flushNow()
	{
		for(World i : editors.k())
		{
			editors.remove(i).close();
		}
	}

	public BlockEditor open(World world)
	{
		if(editors.containsKey(world))
		{
			return editors.get(world);
		}

		BlockEditor e = null;

		if(Bukkit.getPluginManager().isPluginEnabled("WorldEdit") && !PaperLib.isPaper() && !IrisSettings.get().getGeneral().isIgnoreWorldEdit())
		{
			try
			{
				e = new WEBlockEditor(world);
			}

			catch(Throwable ex)
			{
				e = new BukkitBlockEditor(world);
			}
		}

		else
		{
			e = new BukkitBlockEditor(world);
		}

		editors.put(world, e);

		return e;
	}
}
