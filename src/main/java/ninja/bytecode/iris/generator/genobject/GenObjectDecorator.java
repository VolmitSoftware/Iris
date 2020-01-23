package ninja.bytecode.iris.generator.genobject;

import java.util.Collections;
import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;

import mortar.api.sched.S;
import mortar.logic.format.F;
import mortar.util.text.C;
import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.parallax.ParallaxCache;
import ninja.bytecode.iris.generator.placer.AtomicParallaxPlacer;
import ninja.bytecode.iris.generator.placer.BukkitPlacer;
import ninja.bytecode.iris.generator.placer.NMSPlacer;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.IPlacer;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.ObjectMode;
import ninja.bytecode.iris.util.SMCAVector;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.collections.KSet;
import ninja.bytecode.shuriken.execution.ChronoLatch;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class GenObjectDecorator extends BlockPopulator
{
	private KList<PlacedObject> placeHistory;
	private KMap<IrisBiome, KList<GenObjectGroup>> orderCache;
	private KMap<IrisBiome, KMap<GenObjectGroup, Double>> populationCache;
	private IPlacer placer;
	private IrisGenerator g;
	private ChronoLatch cl = new ChronoLatch(250);

	public GenObjectDecorator(IrisGenerator generator)
	{
		this.g = generator;
		placeHistory = new KList<>();
		populationCache = new KMap<>();
		orderCache = new KMap<>();

		for(IrisBiome i : generator.getDimension().getBiomes())
		{
			KMap<GenObjectGroup, Double> gc = new KMap<>();
			KMap<Integer, KList<GenObjectGroup>> or = new KMap<>();
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
						or.put(g.getPiority(), new KList<>());
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
				KList<GenObjectGroup> g = new KList<>();
				for(KList<GenObjectGroup> j : or.v())
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
	public void populate(World world, Random random, Chunk source)
	{
		Runnable m = () ->
		{
			try
			{
				if(g.isDisposed())
				{
					placeHistory.clear();
					return;
				}

				KSet<IrisBiome> hits = new KSet<>();
				int cx = source.getX();
				int cz = source.getZ();

				for(int i = 0; i < Iris.settings.performance.decorationAccuracy; i++)
				{
					int x = (cx << 4) + random.nextInt(16);
					int z = (cz << 4) + random.nextInt(16);
					IrisBiome biome = g.getBiome((int) g.getOffsetX(x, z), (int) g.getOffsetZ(x, z));

					if(hits.contains(biome))
					{
						continue;
					}

					KMap<GenObjectGroup, Double> objects = populationCache.get(biome);

					if(objects == null)
					{
						continue;
					}

					hits.add(biome);
					populate(world, cx, cz, random, biome);
				}
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		};

		if(Iris.settings.performance.objectMode.equals(ObjectMode.QUICK_N_DIRTY))
		{
			J.a(m);
		}

		else
		{
			m.run();
		}
	}

	@SuppressWarnings("deprecation")
	private void populate(World world, int cx, int cz, Random random, IrisBiome biome)
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

					int by = world.getHighestBlockYAt(x, z);
					Block b = world.getBlockAt(x, by - 1, z);
					MB mb = MB.of(b.getType(), b.getData());

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

					if(Iris.settings.performance.objectMode.equals(ObjectMode.QUICK_N_DIRTY))
					{
						placer = new NMSPlacer(world);
					}

					else if(Iris.settings.performance.objectMode.equals(ObjectMode.LIGHTING_PHYSICS))
					{
						placer = new BukkitPlacer(world, true);
					}

					else if(Iris.settings.performance.objectMode.equals(ObjectMode.LIGHTING))
					{
						placer = new BukkitPlacer(world, false);
					}

					Runnable rx = () ->
					{
						Location start = go.place(x, by, z, placer);

						if(start != null)
						{
							g.hitObject();
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
					};

					if(Iris.settings.performance.objectMode.equals(ObjectMode.QUICK_N_DIRTY))
					{
						new S(20)
						{
							@Override
							public void run()
							{
								rx.run();
							}
						};
					}

					else
					{
						rx.run();
					}
				}
			}
		}

		if(placer != null && cl.flip())
		{
			placer.flush();
		}
	}

	public void populateParallax(int cx, int cz, Random random)
	{
		try
		{
			if(g.isDisposed())
			{
				placeHistory.clear();
				return;
			}

			ParallaxCache cache = new ParallaxCache(g);
			KSet<IrisBiome> hits = new KSet<>();

			for(int i = 0; i < Iris.settings.performance.decorationAccuracy; i++)
			{
				int x = (cx << 4) + random.nextInt(16);
				int z = (cz << 4) + random.nextInt(16);
				IrisBiome biome = cache.getBiome(x, z);

				if(hits.contains(biome))
				{
					continue;
				}

				KMap<GenObjectGroup, Double> objects = populationCache.get(biome);

				if(objects == null)
				{
					continue;
				}

				hits.add(biome);
				populateParallax(cx, cz, random, biome, cache);
			}
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}
	}

	private void populateParallax(int cx, int cz, Random random, IrisBiome biome, ParallaxCache cache)
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
						g.hitObject();
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

	public KList<PlacedObject> getHistory()
	{
		return placeHistory;
	}

	public void dispose()
	{

	}

	public PlacedObject randomObject(String string)
	{
		KList<PlacedObject> v = new KList<>();

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
