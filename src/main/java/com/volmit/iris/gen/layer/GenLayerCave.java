package com.volmit.iris.gen.layer;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.DimensionChunkGenerator;
import com.volmit.iris.gen.atomics.AtomicSliver;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.noise.FastNoise;
import com.volmit.iris.noise.FastNoise.CellularDistanceFunction;
import com.volmit.iris.noise.FastNoise.CellularReturnType;
import com.volmit.iris.noise.FastNoise.NoiseType;
import com.volmit.iris.util.B;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.GenLayer;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;

public class GenLayerCave extends GenLayer
{
	public static final BlockData CAVE_AIR = B.getBlockData("CAVE_AIR");
	public static final BlockData AIR = B.getBlockData("AIR");
	private static final KList<CaveResult> EMPTY = new KList<>();
	private CNG gincline;
	private CNG shuffle;
	private FastNoise gg;

	public GenLayerCave(DimensionChunkGenerator iris, RNG rng)
	{
		//@builder
		super(iris, rng);
		shuffle = CNG.signature(rng.nextParallelRNG(1348566));
		gincline = new CNG(rng.nextParallelRNG(26512), 1D, 3).scale(0.00452);
		gg = new FastNoise(324895 * rng.nextParallelRNG(49678).imax());
		//@done
	}

	public KList<CaveResult> genCaves(double wxx, double wzz, int x, int z, AtomicSliver data)
	{
		if(!iris.getDimension().isCaves())
		{
			return EMPTY;
		}

		int surface = data.getHighestBlock();
		KList<CaveResult> result = new KList<>();
		shuffle.scale(0.01);
		double shuffleDistance = 72;
		gg.SetNoiseType(NoiseType.Cellular);
		gg.SetCellularReturnType(CellularReturnType.Distance2Sub);
		gg.SetCellularDistanceFunction(CellularDistanceFunction.Natural);

		for(int i = 0; i < 3; i++)
		{
			double wx = wxx + (shuffle.noise(wxx, wzz) * shuffleDistance);
			double wz = wzz + (shuffle.noise(wzz, wxx) * shuffleDistance);
			double incline = 157;
			double baseWidth = (14 * iris.getDimension().getCaveScale());
			double distanceCheck = 0.0132 * baseWidth;
			double distanceTake = 0.0022 * baseWidth;
			double drop = (-i * 17) + 44 + iris.getDimension().getCaveShift();
			double caveHeightNoise = incline * gincline.noise((wx + (10000 * i)), (wz - (10000 * i)));
			caveHeightNoise += shuffle.fitDouble(-1, 1, wxx - caveHeightNoise, wzz + caveHeightNoise) * 3;

			int ceiling = -256;
			int floor = 512;

			for(double tunnelHeight = 1; tunnelHeight <= baseWidth; tunnelHeight++)
			{
				double distance = (gg.GetCellular((float) wx + (10000 * i), (float) wz - (10000 * i)) + 1D) / 2D;
				if(distance < distanceCheck - (tunnelHeight * distanceTake))
				{
					int caveHeight = (int) Math.round(caveHeightNoise - drop);
					int pu = (int) (caveHeight + tunnelHeight);
					int pd = (int) (caveHeight - tunnelHeight);

					if(pd > surface + 1)
					{
						continue;
					}

					if((pu > 255 && pd > 255) || (pu < 0 && pd < 0))
					{
						continue;
					}

					if(data == null)
					{
						ceiling = pu > ceiling ? pu : ceiling;
						floor = pu < floor ? pu : floor;
						ceiling = pd > ceiling ? pd : ceiling;
						floor = pd < floor ? pd : floor;

						if(tunnelHeight == 1)
						{
							ceiling = caveHeight > ceiling ? caveHeight : ceiling;
							floor = caveHeight < floor ? caveHeight : floor;
						}
					}

					else
					{
						if(dig(x, pu, z, data))
						{
							ceiling = pu > ceiling ? pu : ceiling;
							floor = pu < floor ? pu : floor;

							if(pu > surface - 2)
							{
								if(dig(x, pu + 1, z, data))
								{
									ceiling = pu + 1 > ceiling ? pu + 1 : ceiling;
									floor = pu + 1 < floor ? pu + 1 : floor;

									if(dig(x, pu + 2, z, data))
									{
										ceiling = pu + 2 > ceiling ? pu + 2 : ceiling;
										floor = pu + 2 < floor ? pu + 2 : floor;

										if(dig(x, pu + 3, z, data))
										{
											ceiling = pu + 3 > ceiling ? pu + 3 : ceiling;
											floor = pu + 3 < floor ? pu + 3 : floor;
										}
									}
								}
							}
						}

						if(dig(x, pd, z, data))
						{
							ceiling = pd > ceiling ? pd : ceiling;
							floor = pd < floor ? pd : floor;
						}

						if(tunnelHeight == 1)
						{
							if(dig(x, (int) (caveHeight), z, data))
							{
								ceiling = caveHeight > ceiling ? caveHeight : ceiling;
								floor = caveHeight < floor ? caveHeight : floor;
							}
						}
					}
				}
			}

			if(floor >= 0 && ceiling <= 255)
			{
				result.add(new CaveResult(floor, ceiling));
			}
		}

		return result;
	}

	public boolean dig(int x, int y, int z, AtomicSliver data)
	{
		Material a = data.getType(y);
		Material c = data.getType(y + 1);
		Material d = data.getType(y + 2);
		Material e = data.getType(y + 3);
		Material f = data.getType(y - 1);

		if(can(a) && canAir(c) && canAir(f) && canWater(d) && canWater(e))
		{
			data.set(y, CAVE_AIR);
			data.set(y + 1, CAVE_AIR);
			return true;
		}

		return false;
	}

	public boolean canAir(Material m)
	{
		return (m.isSolid() || (B.isDecorant(m)) || m.equals(Material.AIR) || m.equals(B.mat("CAVE_AIR"))) && !m.equals(Material.BEDROCK);
	}

	public boolean canWater(Material m)
	{
		return !m.equals(Material.WATER);
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
