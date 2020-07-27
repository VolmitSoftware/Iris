package ninja.bytecode.iris.object;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.Desc;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.shuriken.collections.KList;

@Desc("Represents a composite generator of noise gens")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisGenerator extends IrisRegistrant
{
	@Desc("The zoom or frequency.")
	private double zoom = 1;

	@Desc("The opacity, essentially a multiplier on the output.")
	private double opacity = 1;

	@Desc("The offset to shift this noise x")
	private double offsetX = 0;

	@Desc("The offset to shift this noise z")
	private double offsetZ = 0;

	@Desc("The seed for this generator")
	private long seed = 1;

	@Desc("The interpolation method when two biomes use different heights but this same generator")
	private InterpolationMethod interpolationFunction = InterpolationMethod.BICUBIC;

	@Desc("The interpolation distance scale (blocks) when two biomes use different heights but this same generator")
	private double interpolationScale = 7;

	@Desc("Cliff Height Max. Disable with 0 for min and max")
	private double cliffHeightMax = 0;

	@Desc("Cliff Height Min. Disable with 0 for min and max")
	private double cliffHeightMin = 0;

	@Desc("The list of noise gens this gen contains.")
	private KList<IrisNoiseGenerator> composite = new KList<IrisNoiseGenerator>();

	@Desc("The noise gen for cliff height.")
	private IrisNoiseGenerator cliffHeightGenerator = new IrisNoiseGenerator();

	public double getMax()
	{
		return opacity;
	}

	public boolean hasCliffs()
	{
		return cliffHeightMax > 0;
	}

	public double getHeight(double rx, double rz, long superSeed)
	{
		if(composite.isEmpty())
		{
			Iris.warn("Useless Generator: Composite is empty in " + getLoadKey());
			return 0;
		}

		int hc = hashCode();
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

		return hasCliffs() ? cliff(rx, rz, v, superSeed + 294596) : v;
	}

	public double getCliffHeight(double rx, double rz, double superSeed)
	{
		int hc = hashCode();
		double h = cliffHeightGenerator.getNoise((long) (seed + superSeed + hc), (rx + offsetX) / zoom, (rz + offsetZ) / zoom);
		return IrisInterpolation.lerp(cliffHeightMin, cliffHeightMax, h);
	}

	public double cliff(double rx, double rz, double v, double superSeed)
	{
		double cliffHeight = getCliffHeight(rx, rz, superSeed - 34857);
		return (Math.round((v * 255D) / cliffHeight) * cliffHeight) / 255D;
	}
}
