package ninja.bytecode.iris.generator.genobject;

import java.util.Collections;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.BlockPopulator;

import mortar.logic.format.F;
import mortar.util.text.C;
import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.placer.BukkitPlacer;
import ninja.bytecode.iris.generator.placer.NMSPlacer;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.IPlacer;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.collections.GSet;
import ninja.bytecode.shuriken.execution.ChronoLatch;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.M;

public class GenObjectDecorator extends BlockPopulator
{
	private GList<PlacedObject> placeHistory;
	private GMap<IrisBiome, GList<GenObjectGroup>> orderCache;
	private GMap<IrisBiome, GMap<GenObjectGroup, Double>> populationCache;
	private IPlacer placer;
	private Executor ex;
	private IrisGenerator g;
	private ChronoLatch cl = new ChronoLatch(250);

	public GenObjectDecorator(IrisGenerator generator)
	{
		this.g = generator;
		placeHistory = new GList<>();
		populationCache = new GMap<>();
		orderCache = new GMap<>();
		ex = Executors.newSingleThreadExecutor();

		for(IrisBiome i : generator.getDimension().getBiomes())
		{
			GMap<GenObjectGroup, Double> gc = new GMap<>();
			GMap<Integer, GList<GenObjectGroup>> or = new GMap<>();
			int ff = 0;
			for(String j : i.getSchematicGroups().k())
			{
				double c = i.getSchematicGroups().get(j);

				try
				{
					GenObjectGroup g = generator.getDimension().getObjectGroup(j);
					ff += g.size();
					gc.put(g, c);

					if(!or.containsKey(g.getPiority()))
					{
						or.put(g.getPiority(), new GList<>());
					}

					or.get(g.getPiority()).add(g);
				}

				catch(Throwable e)
				{
					L.f(ChatColor.RED + "Failed to inject " + j + " into GenObjectDecorator");
					L.ex(e);
				}
			}

			if(!gc.isEmpty())
			{
				GList<GenObjectGroup> g = new GList<>();
				for(GList<GenObjectGroup> j : or.v())
				{
					g.addAll(j);
				}

				Collections.sort(g, (a, b) -> a.getPiority() - b.getPiority());
				orderCache.put(i, g);
				populationCache.put(i, gc);

				if(Iris.settings.performance.verbose)
				{
					L.v(C.DARK_GREEN + i.getName() + ": " + C.DARK_AQUA + F.f(ff) + " Objects");
				}
			}
		}

		L.i("Population Cache is " + populationCache.size());
	}

	@Override
	public void populate(World world, Random rnotusingyou, Chunk source)
	{
		if(g.isDisposed())
		{
			placeHistory.clear();
			return;
		}

		ex.execute(() ->
		{
			Random random = new Random(((source.getX() - 32) * (source.getZ() + 54)) + world.getSeed());
			GSet<IrisBiome> hits = new GSet<>();

			for(int i = 0; i < Iris.settings.performance.decorationAccuracy; i++)
			{
				int x = (source.getX() << 4) + random.nextInt(16);
				int z = (source.getZ() << 4) + random.nextInt(16);
				IrisBiome biome = g.getBiome((int) g.getOffsetX(x), (int) g.getOffsetX(z));

				if(hits.contains(biome))
				{
					continue;
				}

				GMap<GenObjectGroup, Double> objects = populationCache.get(biome);

				if(objects == null)
				{
					continue;
				}

				hits.add(biome);

				populate(world, random, source, biome, objects, orderCache.get(biome));
			}

			if(Iris.settings.performance.verbose)
			{
				L.flush();
			}
		});
	}

	private void populate(World world, Random random, Chunk source, IrisBiome biome, GMap<GenObjectGroup, Double> objects, GList<GenObjectGroup> order)
	{
		for(GenObjectGroup i : order)
		{
			for(int j = 0; j < getTries(objects.get(i)); j++)
			{
				if(M.r(Iris.settings.gen.objectDensity))
				{
					int x = (source.getX() << 4) + random.nextInt(16);
					int z = (source.getZ() << 4) + random.nextInt(16);
					Block b = world.getHighestBlockAt(x, z).getRelative(BlockFace.DOWN);
					Material t = b.getType();

					if(!t.isSolid() || !biome.isSurface(t))
					{
						if(Iris.settings.performance.verbose)
						{
							L.w(C.WHITE + "Object " + C.YELLOW + i.getName() + "/*" + C.WHITE + " failed to place in " + C.YELLOW + t.toString().toLowerCase() + C.WHITE + " at " + C.YELLOW + F.f(b.getX()) + " " + F.f(b.getY()) + " " + F.f(b.getZ()));
						}

						continue;
					}

					if(placer == null)
					{
						if(Iris.settings.performance.fastDecoration)
						{
							placer = new NMSPlacer(world);
						}

						else
						{
							placer = new BukkitPlacer(world, false);
						}
					}

					GenObject g = i.getSchematics().get(random.nextInt(i.getSchematics().size()));
					Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
					{
						Location start = g.place(x, b.getY(), z, placer);

						if(start != null)
						{
							if(Iris.settings.performance.verbose)
							{
								L.v(C.GRAY + "Placed " + C.DARK_GREEN + i.getName() + C.WHITE + "/" + C.DARK_GREEN + g.getName() + C.GRAY + " at " + C.DARK_GREEN + F.f(start.getBlockX()) + " " + F.f(start.getBlockY()) + " " + F.f(start.getBlockZ()));
							}

							if(Iris.settings.performance.debugMode)
							{
								placeHistory.add(new PlacedObject(start.getBlockX(), start.getBlockY(), start.getBlockZ(), i.getName() + ":" + g.getName()));

								if(placeHistory.size() > Iris.settings.performance.placeHistoryLimit)
								{
									while(placeHistory.size() > Iris.settings.performance.placeHistoryLimit)
									{
										placeHistory.remove(0);
									}
								}
							}
						}
					});
				}
			}
		}

		if(placer != null && cl.flip())
		{
			placer.flush();
		}
	}

	public int getTries(double chance)
	{
		if(chance <= 0)
		{
			return 0;
		}

		if(Math.floor(chance) == chance)
		{
			return (int) chance;
		}

		int floor = (int) Math.floor(chance);

		if(chance - floor > 0 && M.r(chance - floor))
		{
			floor++;
		}

		return floor;
	}

	public GList<PlacedObject> getHistory()
	{
		return placeHistory;
	}
}
