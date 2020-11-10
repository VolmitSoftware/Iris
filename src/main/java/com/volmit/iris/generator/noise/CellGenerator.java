package com.volmit.iris.generator.noise;

import com.volmit.iris.util.RNG;

import lombok.Getter;
import lombok.Setter;

public class CellGenerator
{
	private FastNoiseDouble fn;
	private FastNoiseDouble fd;
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
		long s = rx.lmax();
		fn = new FastNoiseDouble(s);
		fn.setNoiseType(FastNoiseDouble.NoiseType.Cellular);
		fn.setCellularReturnType(FastNoiseDouble.CellularReturnType.CellValue);
		fn.setCellularDistanceFunction(FastNoiseDouble.CellularDistanceFunction.Natural);
		fd = new FastNoiseDouble(s);
		fd.setNoiseType(FastNoiseDouble.NoiseType.Cellular);
		fd.setCellularReturnType(FastNoiseDouble.CellularReturnType.Distance2Sub);
		fd.setCellularDistanceFunction(FastNoiseDouble.CellularDistanceFunction.Natural);
	}

	public double getDistance(double x, double z)
	{
		return ((fd.GetCellular(((x * cellScale) + (cng.noise(x, z) * shuffle)), ((z * cellScale) + (cng.noise(z, x) * shuffle)))) + 1f) / 2f;
	}

	public double getDistance(double x, double y, double z)
	{
		return ((fd.GetCellular(((x * cellScale) + (cng.noise(x, y, z) * shuffle)), ((y * cellScale) + (cng.noise(x, y, z) * shuffle)), ((z * cellScale) + (cng.noise(z, y, x) * shuffle)))) + 1f) / 2f;
	}

	public double getValue(double x, double z, int possibilities)
	{
		if(possibilities == 1)
		{
			return 0;
		}

		return ((fn.GetCellular(((x * cellScale) + (cng.noise(x, z) * shuffle)), ((z * cellScale) + (cng.noise(z, x) * shuffle))) + 1f) / 2f) * (possibilities - 1);
	}

	public double getValue(double x, double y, double z, int possibilities)
	{
		if(possibilities == 1)
		{
			return 0;
		}

		return ((fn.GetCellular(((x * cellScale) + (cng.noise(x, z) * shuffle)), ((y * 8 * cellScale) + (cng.noise(x, y * 8) * shuffle)), ((z * cellScale) + (cng.noise(z, x) * shuffle))) + 1f) / 2f) * (possibilities - 1);
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
