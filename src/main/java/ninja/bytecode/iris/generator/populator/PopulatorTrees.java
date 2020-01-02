package ninja.bytecode.iris.generator.populator;

import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.biome.IrisBiome;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.math.RollingSequence;

public class PopulatorTrees extends BlockPopulator
{
	public static RollingSequence timings = new RollingSequence(512);

	@Override
	public void populate(World world, Random random, Chunk source)
	{
		if(!Iris.settings.gen.doTrees)
		{
			return;
		}
		
		PrecisionStopwatch f = PrecisionStopwatch.start();
		int debuff = 0;
		
		for(int i = 0; i < 16; i++)
		{
			if(debuff > 0)
			{
				debuff--;
				continue;
			}
			
			int x = random.nextInt(15) + (source.getX() * 16);
			int z = random.nextInt(15) + (source.getZ() * 16);
			int y = world.getHighestBlockYAt(x, z);
			Location l = new Location(world, x, y, z);
			
			if(!l.getBlock().getType().isSolid())
			{
				l.getBlock().setType(Material.AIR, false);
			}
			
			IrisBiome biome = IrisBiome.find(world.getBiome(x, z));
			TreeType tt = biome.getTreeChanceSingle();
			
			if(tt != null)
			{
				world.generateTree(l, tt);
			}
			
			else
			{
				debuff += 4;
			}
		}
		
		f.end();
		timings.put(f.getMilliseconds());
	}
}
