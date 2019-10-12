package ninja.bytecode.iris;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;

import ninja.bytecode.shuriken.math.CNG;
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
	private double[][][] scatterCache;
	private CNG gen;
	private CNG fracture;
	private CNG basegen;
	private CNG faultline;
	private RNG rng;

	@Override
	public void onInit(World world, Random random)
	{
		//@builder
		rng = new RNG(world.getSeed());
		CNG scatter = new CNG(rng.nextRNG(), 1, 1)
				.scale(10);
		basegen = new CNG(rng.nextRNG(), 0.46, 3)
				.scale(0.00295)
				.fractureWith(new CNG(rng.nextRNG(), 1, 2)
						.scale(0.025), 70);
		gen = new CNG(rng.nextRNG(), 0.19D, 4)
				.scale(0.012)
				.amp(0.5)
				.freq(1.1)
				.fractureWith(new CNG(rng.nextRNG(), 1, 2)
						.scale(0.018)
						.fractureWith(new CNG(rng.nextRNG(), 1, 1)
								.scale(0.15), 24), 44);
		fracture = new CNG(rng.nextRNG(), 0.6D, 4)
				.scale(0.118);
		faultline = new CNG(rng.nextRNG(), 1D, 1)
				.scale(0.005)
				.child(new CNG(rng.nextRNG(), 1D, 1)
						.scale(0.012))
				.injectWith(CNG.MULTIPLY)
				.fractureWith(new CNG(rng.nextRNG(), 1, 1)
						.scale(0.07), 200);
		//@done

		scatterCache = new double[16][][];

		for(int i = 0; i < 16; i++)
		{
			scatterCache[i] = new double[16][];

			for(int j = 0; j < 16; j++)
			{
				scatterCache[i][j] = new double[16];

				for(int k = 0; k < 16; k++)
				{
					scatterCache[i][j][k] = scatter.noise(i, j, k);
				}
			}
		}
	}

	@Override
	public void genColumn(int wx, int wz, int x, int z)
	{
		int height = getHeight(wx, wz);
		MB mb = rock[0];

		for(int i = 0; i < Math.max(height, 63); i++)
		{
			int surfdepth = i < height ? height - i : 0;
			double scatter = scatterCache[x][z][i % 16];

			if(i == 0)
			{
				mb = bedrock;
			}

			else
			{
				if(scatter > 0.4 && i == 2)
				{
					mb = bedrock;
				}

				if(scatter > 0.6 && i == 1)
				{
					mb = bedrock;
				}

				if(i > height)
				{
					mb = water;
				}

				else if(i == height)
				{
					mb = pick(sand, scatter);
				}
				
				else if(i == height + 1 + scatter)
				{
					mb = pick(sandygrass, scatter);
				}

				else if(i == height - 1)
				{
					mb = grass;
				}

				else if(i > height - ((scatter * 2) + 1))
				{
					mb = pick(earth, scatter);
				}

				else
				{
					mb = pick(rock, scatter);
				}

				if(mb.data < 0)
				{
					mb = new MB(mb.material, pick(Math.abs(mb.data), scatter));
				}
			}

			setBlock(x, i, z, mb.material, mb.data);
		}
	}

	public int getHeight(double dx, double dz)
	{
		double fnoise = fracture.noise(dx, dz);
		double fault = faultline.noise(dx, dz);
		dx += (fnoise * 12);
		dz -= (fnoise * 12);
		double base = basegen.noise(dx, dz);
		double noise = base + gen.noise(dx, dz);
		double n = noise * 250;
		n = n > 255 ? 255 : n;
		n = n < 0 ? 0 : n;
		
		if(n > 75 && fault > 0.35)
		{
			n += (fault * (n / 19.5));
		}
		
		return (int) n;
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