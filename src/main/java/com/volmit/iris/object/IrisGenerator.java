package com.volmit.iris.object;

import java.util.List;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.generator.noise.CellGenerator;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IRare;
import com.volmit.iris.util.IrisInterpolation;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a composite generator of noise gens")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisGenerator extends IrisRegistrant
{

	@MinNumber(0.001)
	@DontObfuscate
	@Desc("The zoom or frequency.")
	private double zoom = 1;

	@MinNumber(0)
	@DontObfuscate
	@Desc("The opacity, essentially a multiplier on the output.")
	private double opacity = 1;

	@DontObfuscate
	@Desc("Multiply the compsites instead of adding them")
	private boolean multiplicitive  = false;

	@MinNumber(0.001)
	@DontObfuscate
	@Desc("The size of the cell fractures")
	private double cellFractureZoom = 1D;

	@MinNumber(0)
	@DontObfuscate
	@Desc("Cell Fracture Coordinate Shuffling")
	private double cellFractureShuffle = 12D;

	@DontObfuscate
	@Desc("The height of fracture cells. Set to 0 to disable")
	private double cellFractureHeight = 0D;

	@MinNumber(0)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("How big are the cells (X,Z) relative to the veins that touch them. Between 0 and 1. 0.1 means thick veins, small cells.")
	private double cellPercentSize = 0.75D;

	@DontObfuscate
	@Desc("The offset to shift this noise x")
	private double offsetX = 0;

	@DontObfuscate
	@Desc("The offset to shift this noise z")
	private double offsetZ = 0;

	@Required
	@DontObfuscate
	@Desc("The seed for this generator")
	private long seed = 1;

	@Required
	@DontObfuscate
	@Desc("The interpolator to use when smoothing this generator into other regions & generators")
	private IrisInterpolator interpolator = new IrisInterpolator();

	@MinNumber(0)
	@MaxNumber(8192)
	@DontObfuscate
	@Desc("Cliff Height Max. Disable with 0 for min and max")
	private double cliffHeightMax = 0;

	@MinNumber(0)
	@MaxNumber(8192)
	@DontObfuscate
	@Desc("Cliff Height Min. Disable with 0 for min and max")
	private double cliffHeightMin = 0;

	@ArrayType(min = 1, type = IrisNoiseGenerator.class)
	@DontObfuscate
	@Desc("The list of noise gens this gen contains.")
	private KList<IrisNoiseGenerator> composite = new KList<IrisNoiseGenerator>();

	@DontObfuscate
	@Desc("The noise gen for cliff height.")
	private IrisNoiseGenerator cliffHeightGenerator = new IrisNoiseGenerator();

	private final transient AtomicCache<CellGenerator> cellGen = new AtomicCache<>();

	public double getMax()
	{
		return opacity;
	}

	public boolean hasCliffs()
	{
		return cliffHeightMax > 0;
	}

	public CellGenerator getCellGenerator(long seed)
	{
		return cellGen.aquire(() -> new CellGenerator(new RNG(seed + 239466)));
	}

	public <T extends IRare> T fitRarity(KList<T> b, long superSeed, double rx, double rz)
	{
		if(b.size() == 0)
		{
			return null;
		}

		if(b.size() == 1)
		{
			return b.get(0);
		}

		KList<T> rarityMapped = new KList<>();
		boolean o = false;
		int max = 1;
		for(T i : b)
		{
			if(i.getRarity() > max)
			{
				max = i.getRarity();
			}
		}

		max++;

		for(T i : b)
		{
			for(int j = 0; j < max - i.getRarity(); j++)
			{
				if(o = !o)
				{
					rarityMapped.add(i);
				}

				else
				{
					rarityMapped.add(0, i);
				}
			}
		}

		if(rarityMapped.size() == 1)
		{
			return rarityMapped.get(0);
		}

		if(rarityMapped.isEmpty())
		{
			throw new RuntimeException("BAD RARITY MAP! RELATED TO: " + b.toString(", or possibly "));
		}

		return fit(rarityMapped, superSeed, rx, rz);
	}

	public <T> T fit(T[] v, long superSeed, double rx, double rz)
	{
		if(v.length == 0)
		{
			return null;
		}

		if(v.length == 1)
		{
			return v[0];
		}

		return v[fit(0, v.length - 1, superSeed, rx, rz)];
	}

	public <T> T fit(List<T> v, long superSeed, double rx, double rz)
	{
		if(v.size() == 0)
		{
			return null;
		}

		if(v.size() == 1)
		{
			return v.get(0);
		}

		return v.get(fit(0, v.size() - 1, superSeed, rx, rz));
	}

	public int fit(int min, int max, long superSeed, double rx, double rz)
	{
		if(min == max)
		{
			return min;
		}

		double noise = getHeight(rx, rz, superSeed);

		return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
	}

	public int fit(double min, double max, long superSeed, double rx, double rz)
	{
		if(min == max)
		{
			return (int) Math.round(min);
		}

		double noise = getHeight(rx, rz, superSeed);

		return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
	}

	public double fitDouble(double min, double max, long superSeed, double rx, double rz)
	{
		if(min == max)
		{
			return min;
		}

		double noise = getHeight(rx, rz, superSeed);

		return IrisInterpolation.lerp(min, max, noise);
	}

	public double getHeight(double rx, double rz, long superSeed)
	{
		return getHeight(rx, 0, rz, superSeed);
	}

	public double getHeight(double rx, double ry, double rz, long superSeed)
	{
		if(composite.isEmpty())
		{
			Iris.warn("Useless Generator: Composite is empty in " + getLoadKey());
			return 0;
		}

		int hc = (int) ((cliffHeightMin * 10) + 10 + cliffHeightMax * seed + offsetX + offsetZ);
		double h = 0;
		double tp = multiplicitive ? 1 : 0;

		for(IrisNoiseGenerator i : composite)
		{
			if(multiplicitive)
			{
				tp *= i.getOpacity();
				h *= i.getNoise(seed + superSeed + hc, (rx + offsetX) / zoom, (rz + offsetZ) / zoom);
			}

			else
			{
				tp += i.getOpacity();
				h += i.getNoise(seed + superSeed + hc, (rx + offsetX) / zoom, (rz + offsetZ) / zoom);
			}
		}

		double v = multiplicitive ? h * opacity : (h / tp) * opacity;

		if(Double.isNaN(v))
		{
			Iris.warn("Nan value on gen: " + getLoadKey() + ": H = " + h + " TP = " + tp + " OPACITY = " + opacity + " ZOOM = " + zoom);
		}

		v = hasCliffs() ? cliff(rx, rz, v, superSeed + 294596 + hc) : v;
		v = hasCellCracks() ? cell(rx, rz, v, superSeed + 48622 + hc) : v;

		return v;
	}

	public double cell(double rx, double rz, double v, double superSeed)
	{
		getCellGenerator(seed + 46222).setShuffle(getCellFractureShuffle());
		return getCellGenerator(seed + 46222).getDistance(rx / getCellFractureZoom(), rz / getCellFractureZoom()) > getCellPercentSize() ? (v * getCellFractureHeight()) : v;
	}

	private boolean hasCellCracks()
	{
		return getCellFractureHeight() != 0;
	}

	public double getCliffHeight(double rx, double rz, double superSeed)
	{
		int hc = (int) ((cliffHeightMin * 10) + 10 + cliffHeightMax * seed + offsetX + offsetZ);
		double h = cliffHeightGenerator.getNoise((long) (seed + superSeed + hc), (rx + offsetX) / zoom, (rz + offsetZ) / zoom);
		return IrisInterpolation.lerp(cliffHeightMin, cliffHeightMax, h);
	}

	public double cliff(double rx, double rz, double v, double superSeed)
	{
		double cliffHeight = getCliffHeight(rx, rz, superSeed - 34857);
		return (Math.round((v * 255D) / cliffHeight) * cliffHeight) / 255D;
	}

	public IrisGenerator rescale(double scale)
	{
		zoom /= scale;
		return this;
	}
}
