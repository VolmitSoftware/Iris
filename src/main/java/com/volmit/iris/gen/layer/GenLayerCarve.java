package com.volmit.iris.gen.layer;

import com.volmit.iris.gen.TopographicTerrainProvider;
import com.volmit.iris.object.IrisCarveLayer;
import com.volmit.iris.util.CarveResult;
import com.volmit.iris.util.GenLayer;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;

import lombok.Getter;

public class GenLayerCarve extends GenLayer
{
	private static final KList<CarveResult> EMPTY_LIST = new KList<>();

	@Getter
	private boolean couldCarve;

	@Getter
	private int minimum;

	@Getter
	private int maximum;

	public GenLayerCarve(TopographicTerrainProvider iris, RNG rng)
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

	public KList<CarveResult> getCarveLayers(int x, int z)
	{
		if(!couldCarve)
		{
			return EMPTY_LIST;
		}

		KList<CarveResult> surfaces = new KList<>();
		int terrainHeight = (int) Math.round(iris.getTerrainHeight(x, z));
		boolean carving = false;
		int lastCarve = terrainHeight + 1;

		for(int i = Math.min(maximum, terrainHeight); i >= Math.max(minimum, 0); i--)
		{
			if(i <= 0 || i > 255)
			{
				continue;
			}

			boolean nowCarving = isCarved(x, i, z);

			if(carving && !nowCarving)
			{
				if(lastCarve - i > 2 && !(i < terrainHeight && lastCarve - i > terrainHeight))
				{
					surfaces.add(new CarveResult(i, lastCarve));
				}
			}

			if(nowCarving && !carving)
			{
				lastCarve = i;
			}

			carving = nowCarving;
		}

		return surfaces;
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
