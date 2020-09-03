package com.volmit.iris.noise;

import com.volmit.iris.util.RNG;

import lombok.Getter;
import lombok.Setter;

public class CellGenerator
{
	private FastNoise fn;
	private FastNoise fd;
	private CNG cng;

	@Getter
	@Setter
	private double cellScale;

	@Getter
	@Setter
	private double shuffle;

	public CellGenerator(RNG rng)
	{
		shuffle = 128;
		cellScale = 0.73;
		cng = CNG.signature(rng.nextParallelRNG(3204));
		RNG rx = rng.nextParallelRNG(8735652);
		int s = rx.nextInt();
		fn = new FastNoise(s);
		fn.setNoiseType(FastNoise.NoiseType.Cellular);
		fn.setCellularReturnType(FastNoise.CellularReturnType.CellValue);
		fn.setCellularDistanceFunction(FastNoise.CellularDistanceFunction.Natural);
		fd = new FastNoise(s);
		fd.setNoiseType(FastNoise.NoiseType.Cellular);
		fd.setCellularReturnType(FastNoise.CellularReturnType.Distance2Sub);
		fd.setCellularDistanceFunction(FastNoise.CellularDistanceFunction.Natural);
	}

	public float getDistance(double x, double z)
	{
		return ((fd.GetCellular((float) ((x * cellScale) + (cng.noise(x, z) * shuffle)), (float) ((z * cellScale) + (cng.noise(z, x) * shuffle)))) + 1f) / 2f;
	}

	public float getDistance(double x, double y, double z)
	{
		return ((fd.GetCellular((float) ((x * cellScale) + (cng.noise(x, y, z) * shuffle)), (float) ((y * cellScale) + (cng.noise(x, y, z) * shuffle)), (float) ((z * cellScale) + (cng.noise(z, y, x) * shuffle)))) + 1f) / 2f;
	}

	public float getValue(double x, double z, int possibilities)
	{
		if(possibilities == 1)
		{
			return 0;
		}

		return ((fn.GetCellular((float) ((x * cellScale) + (cng.noise(x, z) * shuffle)), (float) ((z * cellScale) + (cng.noise(z, x) * shuffle))) + 1f) / 2f) * (possibilities - 1);
	}

	public float getValue(double x, double y, double z, int possibilities)
	{
		if(possibilities == 1)
		{
			return 0;
		}

		return ((fn.GetCellular((float) ((x * cellScale) + (cng.noise(x, z) * shuffle)),

				(float) ((y * 8 * cellScale) + (cng.noise(x, y * 8) * shuffle))

				, (float) ((z * cellScale) + (cng.noise(z, x) * shuffle))) + 1f) / 2f) * (possibilities - 1);
	}

	public int getIndex(double x, double z, int possibilities)
	{
		if(possibilities == 1)
		{
			return 0;
		}

		return (int) Math.round(getValue(x, z, possibilities));
	}

	public int getIndex(double x, double y, double z, int possibilities)
	{
		if(possibilities == 1)
		{
			return 0;
		}

		return (int) Math.round(getValue(x, y, z, possibilities));
	}
}
