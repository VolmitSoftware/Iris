package ninja.bytecode.iris.generator.genobject;

import java.util.Collections;
import java.util.Random;

import org.bukkit.Location;

import mortar.logic.format.F;
import mortar.util.text.C;
import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.parallax.ParallaxCache;
import ninja.bytecode.iris.generator.placer.AtomicParallaxPlacer;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.IPlacer;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.SMCAVector;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.collections.GSet;
import ninja.bytecode.shuriken.execution.ChronoLatch;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class GenObjectDecorator
{
	private GList<PlacedObject> placeHistory;
	private GMap<IrisBiome, GList<GenObjectGroup>> orderCache;
	private GMap<IrisBiome, GMap<GenObjectGroup, Double>> populationCache;
	private IPlacer placer;
	private IrisGenerator g;
	private ChronoLatch cl = new ChronoLatch(250);

	public GenObjectDecorator(IrisGenerator generator)
	{
		this.g = generator;
		placeHistory = new GList<>();
		populationCache = new GMap<>();
		orderCache = new GMap<>();

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

	public void decorateParallax(int cx, int cz, Random random)
	{
		try
		{
			if(g.isDisposed())
			{
				placeHistory.clear();
				return;
			}

			ParallaxCache cache = new ParallaxCache(g);
			GSet<IrisBiome> hits = new GSet<>();

			for(int i = 0; i < Iris.settings.performance.decorationAccuracy; i++)
			{
				int x = (cx << 4) + random.nextInt(16);
				int z = (cz << 4) + random.nextInt(16);
				IrisBiome biome = cache.getBiome(x, z);

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
				populate(cx, cz, random, biome, cache);
			}
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}
	}

	private void populate(int cx, int cz, Random random, IrisBiome biome, ParallaxCache cache)
	{
		for(GenObjectGroup i : orderCache.get(biome))
		{
			if(biome.getSchematicGroups().get(i.getName()) == null)
			{
				L.w(C.YELLOW + "Cannot find chance for " + C.RED + i.getName() + C.YELLOW + " in Biome " + C.RED + biome.getName());
				continue;
			}

			for(int j = 0; j < getTries(biome.getSchematicGroups().get(i.getName())); j++)
			{
				if(M.r(Iris.settings.gen.objectDensity))
				{
					GenObject go = i.getSchematics().get(random.nextInt(i.getSchematics().size()));
					int x = (cx << 4) + random.nextInt(16);
					int z = (cz << 4) + random.nextInt(16);

					if(i.getWorldChance() >= 0D)
					{
						int rngx = (int) Math.floor(x / (double) (i.getWorldRadius() == 0 ? 32 : i.getWorldRadius()));
						int rngz = (int) Math.floor(z / (double) (i.getWorldRadius() == 0 ? 32 : i.getWorldRadius()));

						if(new RNG(new SMCAVector(rngx, rngz).hashCode()).nextDouble() < i.getWorldChance())
						{
							if(Iris.settings.performance.verbose)
							{
								L.w(C.WHITE + "Object " + C.YELLOW + i.getName() + "/*" + C.WHITE + " failed to place due to a world chance.");
							}

							break;
						}
					}

					int by = cache.getHeight(x, z);
					MB mb = cache.get(x, by, z);

					if(!Iris.settings.performance.noObjectFail)
					{
						if(!mb.material.isSolid() || !biome.isSurface(mb.material))
						{
							if(Iris.settings.performance.verbose)
							{
								L.w(C.WHITE + "Object " + C.YELLOW + i.getName() + "/*" + C.WHITE + " failed to place in " + C.YELLOW + mb.material.toString().toLowerCase() + C.WHITE + " at " + C.YELLOW + F.f(x) + " " + F.f(by) + " " + F.f(z));
							}

							return;
						}
					}

					placer = new AtomicParallaxPlacer(g, cache);
					Location start = go.place(x, by, z, placer);

					if(start != null)
					{
						if(Iris.settings.performance.verbose)
						{
							L.v(C.GRAY + "Placed " + C.DARK_GREEN + i.getName() + C.WHITE + "/" + C.DARK_GREEN + go.getName() + C.GRAY + " at " + C.DARK_GREEN + F.f(start.getBlockX()) + " " + F.f(start.getBlockY()) + " " + F.f(start.getBlockZ()));
						}

						if(Iris.settings.performance.debugMode)
						{
							placeHistory.add(new PlacedObject(start.getBlockX(), start.getBlockY(), start.getBlockZ(), i.getName() + ":" + go.getName()));

							if(placeHistory.size() > Iris.settings.performance.placeHistoryLimit)
							{
								while(placeHistory.size() > Iris.settings.performance.placeHistoryLimit)
								{
									placeHistory.remove(0);
								}
							}
						}
					}
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

	public void dispose()
	{

	}

	public PlacedObject randomObject(String string)
	{
		GList<PlacedObject> v = new GList<>();

		for(PlacedObject i : placeHistory)
		{
			if(i.getF().toLowerCase().replaceAll("\\Q:\\E", "/").startsWith(string.toLowerCase()))
			{
				v.add(i);
			}
		}

		if(v.isEmpty())
		{
			return null;
		}

		return v.getRandom();
	}
}
