package ninja.bytecode.iris.layer;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import ninja.bytecode.iris.generator.DimensionChunkGenerator;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.FastNoise;
import ninja.bytecode.iris.util.FastNoise.CellularDistanceFunction;
import ninja.bytecode.iris.util.FastNoise.CellularReturnType;
import ninja.bytecode.iris.util.FastNoise.NoiseType;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.HeightMap;
import ninja.bytecode.iris.util.RNG;

public class GenLayerCave extends GenLayer
{
	private CNG gincline;
	private CNG shuffle;
	private FastNoise gg;

	public GenLayerCave(DimensionChunkGenerator iris, RNG rng)
	{
		//@builder
		super(iris, rng);
		shuffle = CNG.signature(rng.nextParallelRNG(2348566));
		gincline = new CNG(rng.nextParallelRNG(1112), 1D, 3).scale(0.00452);
		gg = new FastNoise(324895 * rng.nextParallelRNG(45678).imax());
		//@done
	}

	public void genCaves(double wxx, double wzz, int x, int z, ChunkData data, HeightMap height)
	{
		if(!iris.getDimension().isCaves())
		{
			return;
		}

		shuffle.scale(0.01);
		double shuffleDistance = 72;
		gg.SetNoiseType(NoiseType.Cellular);
		gg.SetCellularReturnType(CellularReturnType.Distance2Sub);
		gg.SetCellularDistanceFunction(CellularDistanceFunction.Natural);

		for(int i = 0; i < 4; i++)
		{
			double wx = wxx + (shuffle.noise(wxx, wzz) * shuffleDistance);
			double wz = wzz + (shuffle.noise(wzz, wxx) * shuffleDistance);
			double incline = 157;
			double baseWidth = (9 * iris.getDimension().getCaveScale());
			double distanceCheck = 0.0132 * baseWidth;
			double distanceTake = 0.0032 * baseWidth;
			double drop = (-i * 7) + 44 + iris.getDimension().getCaveShift();
			double caveHeightNoise = incline * gincline.noise((wx + (10000 * i)), (wz - (10000 * i)));
			caveHeightNoise += shuffle.fitDoubleD(-1, 1, wxx - caveHeightNoise, wzz + caveHeightNoise) * 3;
			for(double tunnelHeight = 1; tunnelHeight <= baseWidth; tunnelHeight++)
			{
				double distance = (gg.GetCellular((float) wx + (10000 * i), (float) wz - (10000 * i)) + 1D) / 2D;
				if(distance < distanceCheck - (tunnelHeight * distanceTake))
				{
					int caveHeight = (int) Math.round(caveHeightNoise - drop);
					dig(x, (int) (caveHeight + tunnelHeight), z, data);
					dig(x, (int) (caveHeight - tunnelHeight), z, data);

					if(tunnelHeight == 1)
					{
						dig(x, (int) (caveHeight), z, data);
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
