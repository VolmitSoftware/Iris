package ninja.bytecode.iris.generator.populator;

import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.BlockPopulator;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.schematic.SchematicGroup;
import ninja.bytecode.iris.spec.IrisBiome;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.collections.GSet;
import ninja.bytecode.shuriken.math.M;

public class ObjectPopulator extends BlockPopulator
{
	private GMap<Biome, IrisBiome> biomeMap;
	private GMap<Biome, GMap<SchematicGroup, Double>> populationCache;

	public ObjectPopulator(IrisGenerator generator)
	{
		biomeMap = new GMap<>();
		populationCache = new GMap<>();

		for(IrisBiome i : generator.getLoadedBiomes())
		{
			biomeMap.put(i.getRealBiome(), i);

			GMap<SchematicGroup, Double> gk = new GMap<>();

			for(String j : i.getSchematicGroups().k())
			{
				gk.put(Iris.schematics.get(j), i.getSchematicGroups().get(j));
			}

			populationCache.put(i.getRealBiome(), gk);
		}
	}

	@Override
	public void populate(World world, Random random, Chunk source)
	{
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
			
			GMap<SchematicGroup, Double> objects = populationCache.get(biome);
			
			if(objects == null)
			{
				continue;
			}
			
			hits.add(biome);
			populate(world, random, source, biome, objects);
		}
	}
	
	private void populate(World world, Random random, Chunk source, Biome biome, GMap<SchematicGroup, Double> objects)
	{
		for(SchematicGroup i : objects.k())
		{
			for(int j = 0; j < getTries(objects.get(i)); j++)
			{
				int x = (source.getX() << 4) + random.nextInt(16);
				int z = (source.getZ() << 4) + random.nextInt(16);
				Block b = world.getHighestBlockAt(x, z);
				
				if(!b.getRelative(BlockFace.DOWN).getType().isSolid())
				{
					return;
				}
				
				i.getSchematics().get(random.nextInt(i.getSchematics().size())).place(world, x, b.getY() - 1, z);
			}
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
