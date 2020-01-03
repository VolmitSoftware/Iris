package ninja.bytecode.iris.util;

import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;

import ninja.bytecode.shuriken.math.M;

public abstract class ChancedPopulator extends BlockPopulator
{
	private final double chance;

	public ChancedPopulator(double chance)
	{
		this.chance = chance;
	}

	@Override
	public void populate(World world, Random random, Chunk source)
	{
		if(chance == 0)
		{
			return;
		}

		if(chance > 0 && chance < 1 && M.r(chance))
		{
			doPopulate(world, random, source, (source.getX() << 4) + random.nextInt(16), (source.getZ() << 4) + random.nextInt(16));
		}

		if(chance > 1)
		{
			for(int i = 0; i < (int) chance; i++)
			{
				doPopulate(world, random, source, (source.getX() << 4) + random.nextInt(16), (source.getZ() << 4) + random.nextInt(16));
			}

			if(M.r(chance - ((int) chance)))
			{
				doPopulate(world, random, source, (source.getX() << 4) + random.nextInt(16), (source.getZ() << 4) + random.nextInt(16));
			}
		}
	}

	public abstract void doPopulate(World world, Random random, Chunk source, int x, int z);
}
