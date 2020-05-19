package ninja.bytecode.iris.object;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.shuriken.collections.KList;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisGenerator extends IrisRegistrant
{
	private double zoom = 1;
	private double opacity = 1;
	private double offsetX = 0;
	private double offsetZ = 0;
	private long seed = 1;
	private InterpolationMethod interpolationFunction = InterpolationMethod.BICUBIC;
	private double interpolationScale = 7;
	private KList<IrisNoiseGenerator> composite = new KList<IrisNoiseGenerator>();

	public double getMax()
	{
		return opacity;
	}

	public double getHeight(double rx, double rz, long superSeed)
	{
		if(composite.isEmpty())
		{
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

		return (h / tp) * opacity;
	}

}
