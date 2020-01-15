package ninja.bytecode.iris.generator.genobject;

import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.BlockPopulator;

import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.TimingsController;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.placer.BukkitPlacer;
import ninja.bytecode.iris.generator.placer.NMSPlacer;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.IPlacer;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.collections.GSet;
import ninja.bytecode.shuriken.execution.ChronoLatch;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.M;

public class GenObjectDecorator extends BlockPopulator
{
	private GMap<Biome, IrisBiome> biomeMap;
	private GMap<Biome, GMap<GenObjectGroup, Double>> populationCache;
	private IPlacer placer;
	private ChronoLatch cl = new ChronoLatch(250);

	public GenObjectDecorator(IrisGenerator generator)
	{
		biomeMap = new GMap<>();
		populationCache = new GMap<>();

		for(IrisBiome i : generator.getLoadedBiomes())
		{
			biomeMap.put(i.getRealBiome(), i);

			GMap<GenObjectGroup, Double> gc = new GMap<>();

			for(String j : i.getSchematicGroups().k())
			{
				double c = i.getSchematicGroups().get(j);

				try
				{
					GenObjectGroup g = generator.getDimension().getObjectGroup(j);

					gc.put(g, c);
				}

				catch(Throwable e)
				{
					L.f(ChatColor.RED + "Failed to inject " + j + " into GenObjectDecorator");
					L.ex(e);
				}
			}

			if(!gc.isEmpty())
			{
				populationCache.put(i.getRealBiome(), gc);
			}
		}
	}

	@Override
	public void populate(World world, Random rnotusingyou, Chunk source)
	{
		try
		{
			Random random = new Random(((source.getX() - 32) * (source.getZ() + 54)) + world.getSeed());
			Iris.getController(TimingsController.class).started("decor");
			GSet<Biome> hits = new GSet<>();

			for(int i = 0; i < Iris.settings.performance.decorationAccuracy; i++)
			{
				int x = (source.getX() << 4) + random.nextInt(16);
				int z = (source.getZ() << 4) + random.nextInt(16);
				Biome biome = world.getBiome(x, z);

				if(hits.contains(biome))
				{
					continue;
				}

				IrisBiome ibiome = biomeMap.get(biome);

				if(ibiome == null)
				{
					continue;
				}

				GMap<GenObjectGroup, Double> objects = populationCache.get(biome);

				if(objects == null)
				{
					continue;
				}

				hits.add(biome);
				populate(world, random, source, biome, ibiome, objects);
			}

			Iris.getController(TimingsController.class).stopped("decor");
		}

		catch(Throwable e)
		{

		}
	}

	private void populate(World world, Random random, Chunk source, Biome biome, IrisBiome ibiome, GMap<GenObjectGroup, Double> objects)
	{
		for(GenObjectGroup i : objects.k())
		{
			for(int j = 0; j < getTries(objects.get(i)); j++)
			{
				int x = (source.getX() << 4) + random.nextInt(16);
				int z = (source.getZ() << 4) + random.nextInt(16);
				Block b = world.getHighestBlockAt(x, z).getRelative(BlockFace.DOWN);
				Material t = b.getType();

				if(!t.isSolid() || !ibiome.isSurface(t))
				{
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

				i.getSchematics().get(random.nextInt(i.getSchematics().size())).place(x, b.getY(), z, placer);
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
}
