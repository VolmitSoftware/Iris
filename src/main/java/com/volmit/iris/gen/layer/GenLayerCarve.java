package com.volmit.iris.gen.layer;

import com.volmit.iris.gen.DimensionChunkGenerator;
import com.volmit.iris.util.CellGenerator;
import com.volmit.iris.util.GenLayer;
import com.volmit.iris.util.IrisInterpolation;
import com.volmit.iris.util.M;
import com.volmit.iris.util.RNG;

public class GenLayerCarve extends GenLayer
{
	private CellGenerator cell;

	public GenLayerCarve(DimensionChunkGenerator iris, RNG rng)
	{
		super(iris, rng);
		cell = new CellGenerator(rng.nextParallelRNG(-135486678));
	}

	public boolean isCarved(int xc, int y, int zc)
	{
		if(y > iris.getDimension().getCarvingMax() || y < iris.getDimension().getCarvingMin())
		{
			return false;
		}

		double x = ((double) xc / iris.getDimension().getCarvingZoom());
		double z = ((double) zc / iris.getDimension().getCarvingZoom());

		double opacity = Math.pow(IrisInterpolation.sinCenter(M.lerpInverse(iris.getDimension().getCarvingMin(), iris.getDimension().getCarvingMax(), y)), 4);

		if(cell.getDistance(x - (Math.cos(y / iris.getDimension().getCarvingRippleThickness()) + 0.5D) / 2D, y / iris.getDimension().getCarvingSliverThickness(), z + (Math.sin(y / iris.getDimension().getCarvingRippleThickness()) + 0.5D) / 2D) < opacity * iris.getDimension().getCarvingEnvelope())
		{
			return true;
		}

		return false;
	}

	@Override
	public double generate(double x, double z)
	{
		return 0;
	}
}
