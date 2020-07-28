package com.volmit.iris.util;

import com.volmit.iris.util.FastNoise.CellularDistanceFunction;
import com.volmit.iris.util.FastNoise.CellularReturnType;
import com.volmit.iris.util.FastNoise.NoiseType;

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
		fn.SetNoiseType(NoiseType.Cellular);
		fn.SetCellularReturnType(CellularReturnType.CellValue);
		fn.SetCellularDistanceFunction(CellularDistanceFunction.Natural);
		fd = new FastNoise(s);
		fd.SetNoiseType(NoiseType.Cellular);
		fd.SetCellularReturnType(CellularReturnType.Distance2Sub);
		fd.SetCellularDistanceFunction(CellularDistanceFunction.Natural);
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
		return ((fn.GetCellular((float) ((x * cellScale) + (cng.noise(x, z) * shuffle)), (float) ((z * cellScale) + (cng.noise(z, x) * shuffle))) + 1f) / 2f) * (possibilities - 1);
	}

	public int getIndex(double x, double z, int possibilities)
	{
		return (int) Math.round(getValue(x, z, possibilities));
	}
}
