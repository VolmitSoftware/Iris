package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IrisInterpolation;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A noise generator")
@Data
public class IrisNoiseGenerator
{

	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("The coordinate input zoom")
	private double zoom = 1;

	@DontObfuscate
	@Desc("Reverse the output. So that noise = -noise + opacity")
	private boolean negative = false;

	@MinNumber(0)
	@DontObfuscate
	@Desc("The output multiplier")
	private double opacity = 1;

	@DontObfuscate
	@Desc("Coordinate offset x")
	private double offsetX = 0;

	@DontObfuscate
	@Desc("Height output offset y")
	private double offsetY = 0;

	@DontObfuscate
	@Desc("Coordinate offset z")
	private double offsetZ = 0;

	@Required
	@DontObfuscate
	@Desc("The seed")
	private long seed = 0;

	@DontObfuscate
	@Desc("Apply a parametric curve on the output")
	private boolean parametric = false;

	@DontObfuscate
	@Desc("Apply a bezier curve on the output")
	private boolean bezier = false;

	@DontObfuscate
	@Desc("Apply a sin-center curve on the output (0, and 1 = 0 and 0.5 = 1.0 using a sinoid shape.)")
	private boolean sinCentered = false;

	@DontObfuscate
	@Desc("The exponent noise^EXPONENT")
	private double exponent = 1;

	@DontObfuscate
	@Desc("Enable / disable. Outputs offsetY if disabled")
	private boolean enabled = true;

	@Required
	@DontObfuscate
	@Desc("The Noise Style")
	private IrisGeneratorStyle style = NoiseStyle.IRIS.style();

	@MinNumber(1)
	@DontObfuscate
	@Desc("Multiple octaves for multple generators of changing zooms added together")
	private int octaves = 1;

	@ArrayType(min = 1, type = IrisNoiseGenerator.class)
	@DontObfuscate
	@Desc("Apply a child noise generator to fracture the input coordinates of this generator")
	private KList<IrisNoiseGenerator> fracture = new KList<>();

	private final transient AtomicCache<CNG> generator = new AtomicCache<>();

	public IrisNoiseGenerator(boolean enabled)
	{
		this();
		this.enabled = enabled;
	}

	protected CNG getGenerator(long superSeed)
	{
		return generator.aquire(() -> style.create(new RNG(superSeed + 33955677 - seed)).oct(octaves));
	}

	public double getMax()
	{
		return getOffsetY() + opacity;
	}

	public double getNoise(long superSeed, double xv, double zv)
	{
		if(!enabled)
		{
			return offsetY;
		}

		double x = xv;
		double z = zv;
		int g = 33;

		for(IrisNoiseGenerator i : fracture)
		{
			if(i.isEnabled())
			{
				x += i.getNoise(superSeed + seed + g, xv, zv) - (opacity / 2D);
				z -= i.getNoise(superSeed + seed + g, zv, xv) - (opacity / 2D);
			}
			g += 819;
		}

		double n = getGenerator(superSeed).fitDouble(0, opacity, (x / zoom) + offsetX, (z / zoom) + offsetZ);
		n = negative ? (-n + opacity) : n;
		n = (exponent != 1 ? n < 0 ? -Math.pow(-n, exponent) : Math.pow(n, exponent) : n) + offsetY;
		n = parametric ? IrisInterpolation.parametric(n, 1) : n;
		n = bezier ? IrisInterpolation.bezier(n) : n;
		n = sinCentered ? IrisInterpolation.sinCenter(n) : n;

		return n;
	}
}
