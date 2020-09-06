package com.volmit.iris.gen.layer;

import com.volmit.iris.gen.DimensionalTerrainProvider;
import com.volmit.iris.object.IrisCarveLayer;
import com.volmit.iris.util.GenLayer;
import com.volmit.iris.util.RNG;

public class GenLayerCarve extends GenLayer
{
	private boolean couldCarve;
	private int minimum;
	private int maximum;

	public GenLayerCarve(DimensionalTerrainProvider iris, RNG rng)
	{
		super(iris, rng);

		couldCarve = iris.getDimension().isCarving() && iris.getDimension().getCarveLayers().isNotEmpty();
		minimum = 512;
		maximum = -256;

		for(IrisCarveLayer i : iris.getDimension().getCarveLayers())
		{
			minimum = i.getMinHeight() < minimum ? i.getMinHeight() : minimum;
			maximum = i.getMaxHeight() > maximum ? i.getMaxHeight() : maximum;
		}
	}

	public boolean couldCarve(int x, int y, int z)
	{
		return couldCarve && y >= minimum && y <= maximum;
	}

	public boolean couldCarveBelow(int x, int y, int z)
	{
		return couldCarve && y <= maximum;
	}

	public int getSurfaceCarve(int x, int y, int z)
	{
		if(couldCarveBelow(x, y, z))
		{
			int h = y;

			while(isCarved(x, h, z))
			{
				if(h <= 0)
				{
					break;
				}

				h--;
			}

			return h;
		}

		return y;
	}

	public boolean isCarved(int xc, int y, int zc)
	{
		if(!couldCarve(xc, y, zc))
		{
			return false;
		}

		double x = ((double) xc);
		double z = ((double) zc);

		for(IrisCarveLayer i : iris.getDimension().getCarveLayers())
		{
			if(i.isCarved(rng, x, y, z))
			{
				return true;
			}
		}

		return false;
	}

	@Override
	public double generate(double x, double z)
	{
		return 0;
	}
}
