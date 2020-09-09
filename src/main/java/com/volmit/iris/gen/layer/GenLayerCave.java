package com.volmit.iris.gen.layer;

import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.DimensionalTerrainProvider;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.atomics.AtomicSliver;
import com.volmit.iris.noise.FastNoiseDouble;
import com.volmit.iris.noise.FastNoiseDouble.CellularDistanceFunction;
import com.volmit.iris.noise.FastNoiseDouble.CellularReturnType;
import com.volmit.iris.noise.FastNoiseDouble.NoiseType;
import com.volmit.iris.object.IrisCaveLayer;
import com.volmit.iris.util.B;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.GenLayer;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;

public class GenLayerCave extends GenLayer
{
	public static boolean bad = false;
	public static final BlockData CAVE_AIR = B.getBlockData("CAVE_AIR");
	public static final BlockData AIR = B.getBlockData("AIR");
	private static final KList<CaveResult> EMPTY = new KList<>();
	private FastNoiseDouble gg;

	public GenLayerCave(DimensionalTerrainProvider iris, RNG rng)
	{
		//@builder
		super(iris, rng);
		gg = new FastNoiseDouble(324895 * rng.nextParallelRNG(49678).imax());
		//@done
	}

	public KList<CaveResult> genCaves(double wxx, double wzz, int x, int z, AtomicSliver data)
	{
		if(!iris.getDimension().isCaves())
		{
			return EMPTY;
		}

		KList<CaveResult> result = new KList<>();
		gg.setNoiseType(NoiseType.Cellular);
		gg.setCellularReturnType(CellularReturnType.Distance2Sub);
		gg.setCellularDistanceFunction(CellularDistanceFunction.Natural);

		for(int i = 0; i < iris.getDimension().getCaveLayers().size(); i++)
		{
			IrisCaveLayer layer = iris.getDimension().getCaveLayers().get(i);
			generateCave(result, wxx, wzz, x, z, data, layer, i);
		}

		return result;
	}

	public void generateCave(KList<CaveResult> result, double wxx, double wzz, int x, int z, AtomicSliver data, IrisCaveLayer layer, int seed)
	{
		double scale = layer.getCaveZoom();
		Function<Integer, BlockData> fluid = (height) ->
		{
			if(!layer.getFluid().hasFluid())
			{
				return CAVE_AIR;
			}

			if(layer.getFluid().isInverseHeight() && height >= layer.getFluid().getFluidHeight())
			{
				return layer.getFluid().getFluid();
			}

			else if(!layer.getFluid().isInverseHeight() && height <= layer.getFluid().getFluidHeight())
			{
				return layer.getFluid().getFluid();
			}

			return CAVE_AIR;
		};
		int surface = (int) Math.round(((IrisTerrainProvider) iris).getTerrainHeight((int) wxx, (int) wzz));
		double wx = wxx + layer.getHorizontalSlope().get(rng, wxx, wzz);
		double wz = wzz + layer.getHorizontalSlope().get(rng, -wzz, -wxx);
		double baseWidth = (14 * scale);
		double distanceCheck = 0.0132 * baseWidth;
		double distanceTake = 0.0022 * baseWidth;
		double caveHeightNoise = layer.getVerticalSlope().get(rng, wxx, wzz);

		int ceiling = -256;
		int floor = 512;

		for(double tunnelHeight = 1; tunnelHeight <= baseWidth; tunnelHeight++)
		{
			double distance = (gg.GetCellular(((wx + (10000 * seed)) / layer.getCaveZoom()), ((wz - (10000 * seed)) / layer.getCaveZoom())) + 1D) / 2D;
			if(distance < distanceCheck - (tunnelHeight * distanceTake))
			{
				int caveHeight = (int) Math.round(caveHeightNoise);
				int pu = (int) (caveHeight + tunnelHeight);
				int pd = (int) (caveHeight - tunnelHeight);

				if(pd > surface + 1)
				{
					continue;
				}

				if(!layer.isCanBreakSurface() && pu > surface - 3)
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
					if(dig(x, pu, z, data, fluid))
					{
						ceiling = pu > ceiling ? pu : ceiling;
						floor = pu < floor ? pu : floor;
					}

					if(dig(x, pd, z, data, fluid))
					{
						ceiling = pd > ceiling ? pd : ceiling;
						floor = pd < floor ? pd : floor;
					}

					if(tunnelHeight == 1)
					{
						if(dig(x, (int) (caveHeight), z, data, fluid))
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

	public boolean dig(int x, int y, int z, AtomicSliver data, Function<Integer, BlockData> caveFluid)
	{
		Material a = data.getType(y);
		Material c = data.getType(y + 1);
		Material d = data.getType(y + 2);
		Material e = data.getType(y + 3);
		Material f = data.getType(y - 1);
		BlockData b = caveFluid.apply(y);
		BlockData b2 = caveFluid.apply(y + 1);

		if(can(a) && canAir(c, b) && canAir(f, b) && canWater(d) && canWater(e))
		{
			data.set(y, b);
			data.set(y + 1, b2);
			return true;
		}

		return false;
	}

	public boolean canAir(Material m, BlockData caveFluid)
	{
		return (B.isSolid(m) || (B.isDecorant(m)) || m.equals(Material.AIR) || m.equals(caveFluid.getMaterial()) || m.equals(B.mat("CAVE_AIR"))) && !m.equals(Material.BEDROCK);
	}

	public boolean canWater(Material m)
	{
		return !m.equals(Material.WATER);
	}

	public boolean can(Material m)
	{
		return B.isSolid(m) && !m.equals(Material.BEDROCK);
	}

	@Override
	public double generate(double x, double z)
	{
		return 0;
	}
}
