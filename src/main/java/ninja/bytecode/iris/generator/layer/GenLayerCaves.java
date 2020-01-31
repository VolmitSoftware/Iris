package ninja.bytecode.iris.generator.layer;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;

import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.atomics.AtomicChunkData;
import ninja.bytecode.iris.util.Borders;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.PolygonGenerator;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerCaves extends GenLayer
{
	private PolygonGenerator g;
	private CNG gincline;

	public GenLayerCaves(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		g = new PolygonGenerator(rng.nextParallelRNG(1111), 3, 0.024, 8, (c) -> c);
		gincline = new CNG(rng.nextParallelRNG(1112), 1D, 3).scale(0.00652);
		//@done
	}

	@Override
	public double generateLayer(double gnoise, double dx, double dz)
	{
		return gnoise;
	}

	public void genCaves(double wxxf, double wzxf, int x, int z, AtomicChunkData data, ChunkPlan plan)
	{
		PrecisionStopwatch s = PrecisionStopwatch.start();
		double itr = 2;
		double level = 8;
		double incline = 157;
		double baseWidth = 11;
		double drop = 46;

		for(double m = 1; m <= itr; m += 0.45)
		{
			double w = baseWidth / m;

			if(w < 5)
			{
				break;
			}

			int lowest = 325;

			double n = incline * gincline.noise((wxxf + (m * 10000)), (wzxf - (m * 10000)));
			for(double i = 1; i <= w / 3D; i++)
			{
				if(Borders.isBorderWithin((wxxf + (m * 10000)), (wzxf - (m * 10000)), 5, w / 2D / i, (wxxf / 3D) + (wzxf / 3D), (xx, zz) -> g.getIndex(xx, zz)))
				{
					int h = (int) ((level + n) - drop);
					if(dig(x, (int) (h + i), z, data) && h + i < lowest)
					{
						lowest = (int) (h + i);
					}

					if(dig(x, (int) (h - i), z, data) && h - i < lowest)
					{
						lowest = (int) (h - i);
					}

					if(i == 1)
					{
						if(dig(x, (int) (h), z, data) && h < lowest)
						{
							lowest = (int) (h);
						}
					}
				}
			}
		}

		iris.getMetrics().stop("caves:ms:x256:/chunk:..", s);
	}

	public boolean dig(int x, int y, int z, AtomicChunkData data)
	{
		Material a = data.getType(x, y, z);
		Material b = data.getType(x, y, z + 1);
		Material c = data.getType(x, y + 1, z);
		Material d = data.getType(x + 1, y, z);
		Material e = data.getType(x, y, z - 1);
		Material f = data.getType(x, y - 1, z);
		Material g = data.getType(x - 1, y, z);

		if(can(a) && cann(b) && cann(c) && cann(d) && cann(e) && cann(f) && cann(g))
		{
			data.setBlock(x, y, z, Material.AIR);
			return true;
		}

		return false;
	}

	public boolean cann(Material m)
	{
		return m.isSolid() || m.equals(Material.AIR) && !m.equals(Material.BEDROCK);
	}

	public boolean can(Material m)
	{
		return m.isSolid() && !m.equals(Material.BEDROCK);
	}
}
