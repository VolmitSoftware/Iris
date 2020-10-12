package com.volmit.iris.manager;

import java.util.List;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.provisions.ProvisionBukkit;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.util.BoardManager;
import com.volmit.iris.util.BoardProvider;
import com.volmit.iris.util.BoardSettings;
import com.volmit.iris.util.C;
import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.IrisStructureResult;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RollingSequence;
import com.volmit.iris.util.ScoreDirection;

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
		return (w.getGenerator() instanceof ProvisionBukkit) && ((IrisTerrainProvider) ((ProvisionBukkit) w.getGenerator()).getProvider()).isDev();
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

		IrisTerrainProvider g = (IrisTerrainProvider) ((ProvisionBukkit) player.getWorld().getGenerator()).getProvider();

		if(cl.flip())
		{
			mem = Form.memSize(g.guessMemoryUsage(), 2);
		}

		int x = player.getLocation().getBlockX();
		int y = player.getLocation().getBlockY();
		int z = player.getLocation().getBlockZ();
		IrisBiome b = g.sampleTrueBiome(x, y, z);
		IrisStructureResult st = g.getStructure(x, y, z);
		tp.put(g.getMetrics().getSpeed());
		v.add("&7&m------------------");
		v.add(C.GREEN + "Speed" + C.GRAY + ": " + C.BOLD + "" + C.GRAY + Form.f(g.getMetrics().getPerSecond().getAverage(), 0) + "/s " + Form.duration(g.getMetrics().getTotal().getAverage(), 1) + "");
		v.add(C.GREEN + "Generators" + C.GRAY + ": " + Form.f(CNG.creates));
		v.add(C.GREEN + "Noise" + C.GRAY + ": " + Form.f((int) hits.getAverage()));
		v.add(C.GREEN + "Parallax" + C.GRAY + ": " + Form.f((int) g.getParallaxMap().getLoadedChunks().size()));
		v.add(C.GREEN + "Objects" + C.GRAY + ": " + Form.f(g.getData().getObjectLoader().count()));
		v.add(C.GREEN + "Memory" + C.GRAY + ": " + mem);
		v.add(C.GREEN + "Jobs" + C.GRAY + ": " + Iris.jobCount());
		v.add("&7&m------------------");
		v.add(C.GREEN + "Heightmap" + C.GRAY + ": " + (int) g.getTerrainHeight(x, z));

		if(b != null)
		{
			v.add(C.GREEN + "Biome" + C.GRAY + ": " + b.getName());
		}

		if(st != null)
		{
			v.add(C.GREEN + "Structure" + C.GRAY + ": " + st.getStructure().getName());
			v.add(C.GREEN + "Tile" + C.GRAY + ": " + st.getTile().toString());
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
