package com.volmit.iris.gen.layer;

import java.util.function.Function;

import org.bukkit.Material;

import com.volmit.iris.gen.TopographicTerrainProvider;
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
	public static final FastBlockData CAVE_AIR = B.getBlockData("CAVE_AIR");
	public static final FastBlockData AIR = B.getBlockData("AIR");
	private static final KList<CaveResult> EMPTY = new KList<>();
	private final FastNoiseDouble gg;

	public GenLayerCave(TopographicTerrainProvider iris, RNG rng)
	{
		super(iris, rng);
		gg = new FastNoiseDouble(324895 * rng.nextParallelRNG(49678).imax());
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
		Function<Integer, FastBlockData> fluid = (height) ->
		{
			if(!layer.getFluid().hasFluid(iris.getData()))
			{
				return CAVE_AIR;
			}

			if(layer.getFluid().isInverseHeight() && height >= layer.getFluid().getFluidHeight())
			{
				return layer.getFluid().getFluid(iris.getData());
			}

			else if(!layer.getFluid().isInverseHeight() && height <= layer.getFluid().getFluidHeight())
			{
				return layer.getFluid().getFluid(iris.getData());
			}

			return CAVE_AIR;
		};
		int surface = (int) Math.round(iris.getTerrainHeight((int) wxx, (int) wzz));
		double wx = wxx + layer.getHorizontalSlope().get(rng, wxx, wzz);
		double wz = wzz + layer.getHorizontalSlope().get(rng, -wzz, -wxx);
		double baseWidth = (14 * scale);
		double distanceCheck = 0.0132 * baseWidth;
		double distanceTake = 0.0022 * baseWidth;
		double caveHeightNoise = layer.getVerticalSlope().get(rng, wxx, wzz);

		if(caveHeightNoise > 259 || caveHeightNoise < -1)
		{
			return;
		}

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
					ceiling = Math.max(pu, ceiling);
					floor = Math.min(pu, floor);
					ceiling = Math.max(pd, ceiling);
					floor = Math.min(pd, floor);

					if(tunnelHeight == 1)
					{
						ceiling = Math.max(caveHeight, ceiling);
						floor = Math.min(caveHeight, floor);
					}
				}

				else
				{
					if(dig(x, pu, z, data, fluid))
					{
						ceiling = Math.max(pu, ceiling);
						floor = Math.min(pu, floor);
					}

					if(dig(x, pd, z, data, fluid))
					{
						ceiling = Math.max(pd, ceiling);
						floor = Math.min(pd, floor);
					}

					if(tunnelHeight == 1)
					{
						if(dig(x, caveHeight, z, data, fluid))
						{
							ceiling = Math.max(caveHeight, ceiling);
							floor = Math.min(caveHeight, floor);
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

	public boolean dig(int x, int y, int z, AtomicSliver data, Function<Integer, FastBlockData> caveFluid)
	{
		Material a = data.getTypeSafe(y);
		Material c = data.getTypeSafe(y + 1);
		Material d = data.getTypeSafe(y + 2);
		Material e = data.getTypeSafe(y + 3);
		Material f = data.getTypeSafe(y - 1);
		FastBlockData b = caveFluid.apply(y);
		FastBlockData b2 = caveFluid.apply(y + 1);

		if(can(a) && canAir(c, b) && canAir(f, b) && canWater(d) && canWater(e))
		{
			data.set(y, b);
			data.set(y + 1, b2);
			return true;
		}

		return false;
	}

	public boolean canAir(Material m, FastBlockData caveFluid)
	{
		return (B.isSolid(m) || (B.isDecorant(FastBlockData.of(m))) || m.equals(Material.AIR) || m.equals(caveFluid.getMaterial()) || m.equals(B.mat("CAVE_AIR").getMaterial())) && !m.equals(Material.BEDROCK);
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
