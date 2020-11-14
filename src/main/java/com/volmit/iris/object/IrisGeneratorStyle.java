package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MaxNumber;
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
@Desc("A gen style")
@Data
public class IrisGeneratorStyle
{

	@Required
	@DontObfuscate
	@Desc("The chance is 1 in CHANCE per interval")
	private NoiseStyle style = NoiseStyle.IRIS;

	@DontObfuscate
	@MinNumber(0.00001)
	@Desc("The zoom of this style")
	private double zoom = 1;

	@DontObfuscate
	@MinNumber(0.00001)
	@Desc("The Output multiplier. Only used if parent is fracture.")
	private double multiplier = 1;

	@DontObfuscate
	@Desc("If set to true, each dimension will be fractured with a different order of input coordinates. This is usually 2 or 3 times slower than normal.")
	private boolean maxFractureAccuracy = false;

	@DontObfuscate
	@Desc("Apply a generator to the coordinate field fed into this parent generator. I.e. Distort your generator with another generator.")
	private IrisGeneratorStyle fracture = null;

	@DontObfuscate
	@MinNumber(0.01562)
	@MaxNumber(64)
	@Desc("The exponent")
	private double exponent = 1;

	private final transient AtomicCache<CNG> cng = new AtomicCache<CNG>();

	public IrisGeneratorStyle(NoiseStyle s)
	{
		this.style = s;
	}

	public IrisGeneratorStyle zoomed(double z)
	{
		this.zoom = z;
		return this;
	}

	public CNG create(RNG rng)
	{
		return cng.aquire(() ->
		{
			CNG cng = style.create(rng).bake().scale(1D / zoom).pow(exponent).bake();
			cng.setTrueFracturing(maxFractureAccuracy);

			if(fracture != null)
			{
				cng.fractureWith(fracture.create(rng.nextParallelRNG(2934)), fracture.getMultiplier());
			}

			return cng;
		});
	}

	public boolean isFlat()
	{
		return style.equals(NoiseStyle.FLAT);
	}
}
