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
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerCaves extends GenLayer
{
	private PolygonGenerator g;
	private CNG gincline;
	private CNG gfract;

	public GenLayerCaves(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		g = new PolygonGenerator(RNG.r, 3, 0.014, 1, (c) -> c);
		gincline = new CNG(RNG.r, 1D, 3).scale(0.00652);
		gfract = new CNG(RNG.r, 24D, 1).scale(0.0152);
		//@done
	}

	@Override
	public double generateLayer(double gnoise, double dx, double dz)
	{
		return gnoise;
	}

	public void genCaves(double xxf, double zzf, int x, int z, AtomicChunkData data, ChunkPlan plan)
	{
		int wxxf = (int) (xxf + gfract.noise(xxf, zzf));
		int wzxf = (int) (zzf - gfract.noise(zzf, xxf));
		double itr = 2;
		double level = 8;
		double incline = 157;
		double baseWidth = 11;
		double drop = 35;

		for(double m = 1; m <= itr; m += 0.65)
		{
			double w = baseWidth / m;

			if(w < 3.5)
			{
				break;
			}

			int lowest = 325;

			double n = incline * gincline.noise((wxxf + (m * 10000)), (wzxf - (m * 10000)));
			for(double i = 1; i <= w / 3D; i++)
			{
				if(Borders.isBorderWithin((wxxf + (m * 10000)), (wzxf - (m * 10000)), 17, w / 2D / i, (wxxf / 3D) + (wzxf / 3D), (xx, zz) -> g.getIndex(xx, zz)))
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
		return m.isSolid() || m.equals(Material.AIR);
	}

	public boolean can(Material m)
	{
		return m.isSolid();
	}
}
