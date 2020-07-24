package ninja.bytecode.iris.layer;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import ninja.bytecode.iris.generator.DimensionChunkGenerator;
import ninja.bytecode.iris.util.Borders;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.HeightMap;
import ninja.bytecode.iris.util.PolygonGenerator;
import ninja.bytecode.iris.util.RNG;

public class GenLayerCave extends GenLayer
{
	private PolygonGenerator g;
	private CNG gincline;

	public GenLayerCave(DimensionChunkGenerator iris, RNG rng)
	{
		//@builder
		super(iris, rng);
		g = new PolygonGenerator(rng.nextParallelRNG(1111), 3, 0.024, 8, (c) -> c);
		gincline = new CNG(rng.nextParallelRNG(1112), 1D, 3).scale(0.00652);
		//@done
	}

	public void genCaves(double wxxf, double wzxf, int x, int z, ChunkData data, HeightMap height)
	{
		if(!iris.getDimension().isCaves())
		{
			return;
		}

		double itr = 2;
		double level = 8;
		double incline = 157;
		double baseWidth = 16 * iris.getDimension().getCaveScale();
		double drop = 44 + iris.getDimension().getCaveShift();

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
				if(Borders.isBorderWithin((wxxf + (m * 10000)), (wzxf - (m * 10000)), 32, w / 2D / i, (wxxf / 3D) + (wzxf / 3D), (xx, zz) -> g.getIndex(xx, zz)))
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

	public boolean dig(int x, int y, int z, ChunkData data)
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

	@Override
	public double generate(double x, double z)
	{
		return 0;
	}
}
