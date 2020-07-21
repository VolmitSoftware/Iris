package ninja.bytecode.iris.object;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.Desc;
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

	@Desc("The list of noise gens this gen contains.")
	private KList<IrisNoiseGenerator> composite = new KList<IrisNoiseGenerator>();

	public double getMax()
	{
		return opacity;
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

		return v;
	}

}
