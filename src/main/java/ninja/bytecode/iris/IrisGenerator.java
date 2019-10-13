package ninja.bytecode.iris;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;

import ninja.bytecode.iris.gen.GenLayerBase;
import ninja.bytecode.iris.gen.IGenLayer;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class IrisGenerator extends ParallelChunkGenerator
{
	private static final MB water = new MB(Material.STATIONARY_WATER);
	private static final MB bedrock = new MB(Material.BEDROCK);
	private static final MB air = new MB(Material.AIR);
	private static final MB grass = new MB(Material.GRASS);
	private static final MB[] earth = {new MB(Material.DIRT), new MB(Material.DIRT, 1),
	};
	private static final MB[] sand = {new MB(Material.SAND), new MB(Material.SAND), new MB(Material.SAND, 1),
	};
	private static final MB[] sandygrass = {new MB(Material.GRASS), new MB(Material.SAND, 1),
	};
	private static final MB[] rock = {new MB(Material.STONE), new MB(Material.STONE, 5), new MB(Material.COBBLESTONE),
	};
	private GList<IGenLayer> genLayers;

	@Override
	public void onInit(World world, Random random)
	{
		RNG rng = new RNG(world.getSeed());
		genLayers = new GList<>();
		genLayers.add(new GenLayerBase(world, random, rng));
		System.out.print("Gend");
	}

	public int getHeight(double dx, double dz)
	{
		double noise = 0.5;
		
		for(IGenLayer i : genLayers)
		{
			noise = i.generateLayer(noise, dx, dz);
		}
				
		double n = noise * 250;
		n = n > 255 ? 255 : n;
		n = n < 0 ? 0 : n;

		return (int) n;
	}

	@Override
	public void genColumn(final int wx, final int wz)
	{
		int height = getHeight(wx, wz);
		MB mb = rock[0];
		
		for(int i = 0; i < height; i++)
		{
			setBlock(wx, i, wz, mb.material, mb.data);
		}
	}

	public int pick(int max, double noise)
	{
		return (int) (noise * max);
	}

	public MB pick(MB[] array, double noise)
	{
		return array[pick(array.length, noise)];
	}
}