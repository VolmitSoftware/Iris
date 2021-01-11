package com.volmit.iris.manager;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.*;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.List;

public class IrisBoardManager implements BoardProvider, Listener
{
	@DontObfuscate
	private BoardManager manager;
	private String mem = "...";
	public RollingSequence hits = new RollingSequence(20);
	public RollingSequence tp = new RollingSequence(100);
	private ChronoLatch cl = new ChronoLatch(1000);

	@DontObfuscate
	public IrisBoardManager()
	{
		Iris.instance.registerListener(this);
		//@builder
		manager = new BoardManager(Iris.instance, BoardSettings.builder()
				.boardProvider(this)
				.scoreDirection(ScoreDirection.DOWN)
				.build());
		//@done
	}

	@EventHandler
	public void on(PlayerChangedWorldEvent e)
	{
		J.s(() -> updatePlayer(e.getPlayer()));
	}

	@DontObfuscate
	private boolean isIrisWorld(World w)
	{
		return IrisWorlds.isIrisWorld(w) && IrisWorlds.access(w).isStudio();
	}

	public void updatePlayer(Player p)
	{
		if(isIrisWorld(p.getWorld()))
		{
			manager.remove(p);
			manager.setup(p);
		}

		else
		{
			manager.remove(p);
		}
	}

	@Override
	public String getTitle(Player player)
	{
		return C.GREEN + "Iris";
	}

	@DontObfuscate
	@Override
	public List<String> getLines(Player player)
	{
		KList<String> v = new KList<>();

		if(!isIrisWorld(player.getWorld()))
		{
			return v;
		}

		IrisAccess g = IrisWorlds.access(player.getWorld());

		if(cl.flip())
		{
			// TODO MEMORY
			mem = Form.memSize(0, 2);
		}

		int x = player.getLocation().getBlockX();
		int y = player.getLocation().getBlockY();
		int z = player.getLocation().getBlockZ();

		Engine engine = g.getCompound().getEngineForHeight(y);

		int parallaxChunks=0;
		int parallaxRegions=0;
		long memoryGuess=0;
		int loadedObjects=0;

		for(int i = 0; i < g.getCompound().getSize(); i++)
		{
			parallaxRegions += g.getCompound().getEngine(i).getParallax().getRegionCount();
			parallaxChunks += g.getCompound().getEngine(i).getParallax().getChunkCount();
			loadedObjects+= g.getCompound().getData().getObjectLoader().getSize();
			memoryGuess += g.getCompound().getData().getObjectLoader().getTotalStorage() * 225;
			memoryGuess+= parallaxChunks * 3500;
			memoryGuess += parallaxRegions * 1700000;
		}

		tp.put(0); // TODO: CHUNK SPEED



		v.add("&7&m------------------");
		v.add(C.GREEN + "Speed" + C.GRAY + ":  " + Form.f(g.getGeneratedPerSecond(), 0) + "/s " + Form.duration(1000D / g.getGeneratedPerSecond(), 0));
		v.add(C.GREEN + "Memory Use" + C.GRAY + ":  ~" + Form.memSize(memoryGuess, 0));

		if(engine != null)
		{
			v.add("&7&m------------------");
			v.add(C.AQUA + "Engine" + C.GRAY + ": " + engine.getName() + " " + engine.getMinHeight() + "-" + engine.getMaxHeight());
			v.add(C.AQUA + "Region" + C.GRAY + ": " + engine.getRegion(x, z).getName());
			v.add(C.AQUA + "Biome" + C.GRAY + ":  " + engine.getBiome(x, y, z).getName());
			v.add(C.AQUA + "Height" + C.GRAY + ": " + Math.round(engine.getHeight(x, z)));
			v.add(C.AQUA + "Slope" + C.GRAY + ":  " + Form.f(engine.getFramework().getComplex().getSlopeStream().get(x, z), 2));
		}

		if(Iris.jobCount() > 0)
		{
			v.add("&7&m------------------");
			v.add(C.LIGHT_PURPLE + "Tasks" + C.GRAY + ": " + Iris.jobCount());
		}

		v.add("&7&m------------------");

		return v;
	}

	@DontObfuscate
	public void disable()
	{
		manager.onDisable();
	}
}
