package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.CellGenerator;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IrisInterpolation;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.Data;
import lombok.EqualsAndHashCode;

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
	@Desc("The interpolation method when two biomes use different heights but this same generator")
	private InterpolationMethod interpolationFunction = InterpolationMethod.BICUBIC;

	@Required
	@MinNumber(1)
	@MaxNumber(8192)
	@DontObfuscate
	@Desc("The interpolation distance scale (blocks) when two biomes use different heights but this same generator")
	private double interpolationScale = 7;

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

	private transient AtomicCache<CellGenerator> cellGen = new AtomicCache<>();

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

	public double getHeight(double rx, double rz, long superSeed)
	{
		if(composite.isEmpty())
		{
			Iris.warn("Useless Generator: Composite is empty in " + getLoadKey());
			return 0;
		}

		int hc = (int) ((cliffHeightMin * 10) + 10 + cliffHeightMax + interpolationScale * seed + offsetX + offsetZ);
		double h = 0;
		double tp = 0;

		for(IrisNoiseGenerator i : composite)
		{
			tp += i.getOpacity();
			h += i.getNoise(seed + superSeed + hc, (rx + offsetX) / zoom, (rz + offsetZ) / zoom);
		}

		double v = (h / tp) * opacity;

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
		int hc = (int) ((cliffHeightMin * 10) + 10 + cliffHeightMax + interpolationScale * seed + offsetX + offsetZ);
		double h = cliffHeightGenerator.getNoise((long) (seed + superSeed + hc), (rx + offsetX) / zoom, (rz + offsetZ) / zoom);
		return IrisInterpolation.lerp(cliffHeightMin, cliffHeightMax, h);
	}

	public double cliff(double rx, double rz, double v, double superSeed)
	{
		double cliffHeight = getCliffHeight(rx, rz, superSeed - 34857);
		return (Math.round((v * 255D) / cliffHeight) * cliffHeight) / 255D;
	}
}
