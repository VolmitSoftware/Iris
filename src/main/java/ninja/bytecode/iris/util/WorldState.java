package ninja.bytecode.iris.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.io.CustomOutputStream;
import ninja.bytecode.shuriken.logging.L;

public class WorldState implements Listener
{
	private int taskId;
	private KMap<MCAPos, MCAState> stateCache;
	private World world;

	@SuppressWarnings("deprecation")
	public WorldState(World world)
	{
		this.world = world;
		taskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(Iris.instance, this::tick, 20, 20 * 18);
		Bukkit.getPluginManager().registerEvents(this, Iris.instance);
	}
	
	public void tick()
	{
		for(MCAPos i : stateCache.k())
		{
			MCAState state = stateCache.get(i);
			
			if(state.isDirty())
			{
				try
				{
					saveMCAState(i, state);
				}
				
				catch(IOException e)
				{
					L.f(ChatColor.RED + "Failed to save MCA State " + i.toFileName());
					L.ex(e);
				}
			}
		}
	}

	public void close()
	{
		HandlerList.unregisterAll(this);
		Bukkit.getScheduler().cancelTask(taskId);
		
		for(MCAPos i : stateCache.k())
		{
			try
			{
				saveMCAState(i, stateCache.get(i));
			}
			
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public void unloadState(MCAPos pos)
	{
		stateCache.remove(pos);
		L.v(ChatColor.GRAY + "Unloaded MCA State " + pos.toFileName());
	}

	public MCAState getState(MCAPos pos)
	{
		if(!stateCache.containsKey(pos))
		{
			try
			{
				stateCache.put(pos, loadMCAState(pos));
			}

			catch(IOException e)
			{
				L.f(ChatColor.RED + "Failed to read MCA State " + pos.toFileName());
				L.ex(e);
				L.w(ChatColor.YELLOW + "Created Fallback MCA State " + pos.toString());
				stateCache.put(pos, new MCAState());
			}
		}

		return stateCache.get(pos);
	}

	private void saveMCAState(MCAPos pos, MCAState state) throws IOException
	{
		File file = new File(world.getWorldFolder(), "iris/state/" + pos.toFileName());
		file.getParentFile().mkdirs();
		FileOutputStream fos = new FileOutputStream(file);
		CustomOutputStream cos = new CustomOutputStream(fos, 9);
		state.write(cos);
		state.saved();
		L.v(ChatColor.GRAY + "Saved MCA State " + pos.toString());
	}

	private MCAState loadMCAState(MCAPos pos) throws IOException
	{
		MCAState state = new MCAState();
		File file = new File(world.getWorldFolder(), "iris/state/" + pos.toFileName());

		if(!file.exists())
		{
			file.getParentFile().mkdirs();
			state.setDirty();
			L.v(ChatColor.GRAY + "Created MCA State " + pos.toString());
			return state;
		}

		FileInputStream fin = new FileInputStream(file);
		GZIPInputStream gzi = new GZIPInputStream(fin);
		state.read(gzi);
		L.v(ChatColor.GRAY + "Loaded MCA State " + pos.toString());
		return state;
	}
}
