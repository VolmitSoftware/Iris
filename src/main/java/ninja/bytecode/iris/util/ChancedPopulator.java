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

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(chance);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		ChancedPopulator other = (ChancedPopulator) obj;
		if(Double.doubleToLongBits(chance) != Double.doubleToLongBits(other.chance))
			return false;
		return true;
	}

	public abstract void doPopulate(World world, Random random, Chunk source, int x, int z);
}
