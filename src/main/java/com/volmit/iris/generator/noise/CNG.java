package com.volmit.iris.generator.noise;

import com.volmit.iris.scaffold.stream.ProceduralStream;
import com.volmit.iris.scaffold.stream.arithmetic.FittedStream;
import com.volmit.iris.scaffold.stream.sources.CNGStream;
import com.volmit.iris.util.*;
import lombok.Data;

import java.util.List;

@Data
public class CNG
{
	public static long hits = 0;
	public static long creates = 0;
	public static final NoiseInjector ADD = (s, v) -> new double[] {s + v, 1};
	public static final NoiseInjector SRC_SUBTRACT = (s, v) -> new double[] {s - v < 0 ? 0 : s - v, -1};
	public static final NoiseInjector DST_SUBTRACT = (s, v) -> new double[] {v - s < 0 ? 0 : s - v, -1};
	public static final NoiseInjector MULTIPLY = (s, v) -> new double[] {s * v, 0};
	public static final NoiseInjector MAX = (s, v) -> new double[] {Math.max(s, v), 0};
	public static final NoiseInjector MIN = (s, v) -> new double[] {Math.min(s, v), 0};
	public static final NoiseInjector SRC_MOD = (s, v) -> new double[] {s % v, 0};
	public static final NoiseInjector SRC_POW = (s, v) -> new double[] {Math.pow(s, v), 0};
	public static final NoiseInjector DST_MOD = (s, v) -> new double[] {v % s, 0};
	public static final NoiseInjector DST_POW = (s, v) -> new double[] {Math.pow(v, s), 0};
	private double scale;
	private double bakedScale;
	private double fscale;
	private boolean trueFracturing = false;
	private KList<CNG> children;
	private CNG fracture;
	private NoiseGenerator generator;
	private final double opacity;
	private NoiseInjector injector;
	private RNG rng;
	private boolean noscale;
	private int oct;
	private double patch;
	private double up;
	private double down;
	private double power;

	public NoiseGenerator getGen()
	{
		return generator;
	}

	public ProceduralStream<Double> stream()
	{
		return new CNGStream(this);
	}

	public ProceduralStream<Double> stream(double min, double max)
	{
		return new FittedStream<Double>(stream(), min, max);
	}

	public static CNG signature(RNG rng)
	{
		return signature(rng, NoiseType.SIMPLEX);
	}

	public static CNG signatureHalf(RNG rng)
	{
		return signatureHalf(rng, NoiseType.SIMPLEX);
	}

	public static CNG signatureThick(RNG rng)
	{
		return signatureThick(rng, NoiseType.SIMPLEX);
	}

	public static CNG signatureDouble(RNG rng)
	{
		return signatureDouble(rng, NoiseType.SIMPLEX);
	}

	public static CNG signatureDouble(RNG rng, NoiseType t)
	{
		return signatureThick(rng, t).fractureWith(signature(rng.nextParallelRNG(4956)), 93);
	}

	public static CNG signature(RNG rng, NoiseType t)
	{
		// @NoArgsConstructor
		return new CNG(rng.nextParallelRNG(17), t, 1D, 1).fractureWith(new CNG(rng.nextParallelRNG(18), 1, 1).scale(0.9).fractureWith(new CNG(rng.nextParallelRNG(20), 1, 1).scale(0.21).fractureWith(new CNG(rng.nextParallelRNG(20), 1, 1).scale(0.9), 620), 145), 44).bake();
		// @done
	}

	public static CNG signatureThick(RNG rng, NoiseType t)
	{
		// @NoArgsConstructor
		return new CNG(rng.nextParallelRNG(133), t, 1D, 1).fractureWith(new CNG(rng.nextParallelRNG(18), 1, 1).scale(0.5).fractureWith(new CNG(rng.nextParallelRNG(20), 1, 1).scale(0.11).fractureWith(new CNG(rng.nextParallelRNG(20), 1, 1).scale(0.4), 620), 145), 44).bake();
		// @done
	}

	public static CNG signatureHalf(RNG rng, NoiseType t)
	{
		// @NoArgsConstructor
		return new CNG(rng.nextParallelRNG(127), t, 1D, 1).fractureWith(new CNG(rng.nextParallelRNG(18), 1, 1).scale(0.9).fractureWith(new CNG(rng.nextParallelRNG(20), 1, 1).scale(0.21).fractureWith(new CNG(rng.nextParallelRNG(20), 1, 1).scale(0.9), 420), 99), 22).bake();
		// @done
	}

	public CNG(RNG random)
	{
		this(random, 1);
	}

	public CNG(RNG random, int octaves)
	{
		this(random, 1D, octaves);
	}

	public CNG(RNG random, double opacity, int octaves)
	{
		this(random, NoiseType.SIMPLEX, opacity, octaves);
	}

	public CNG(RNG random, NoiseType t, double opacity, int octaves)
	{
		creates++;
		noscale = t.equals(NoiseType.WHITE);
		this.oct = octaves;
		this.rng = random;
		power = 1;
		scale = 1;
		patch = 1;
		bakedScale = 1;
		fscale = 1;
		down = 0;
		up = 0;
		fracture = null;
		generator = t.create(random.nextParallelRNG(33).lmax());
		this.opacity = opacity;
		this.injector = ADD;

		if(generator instanceof OctaveNoise)
		{
			((OctaveNoise) generator).setOctaves(octaves);
		}
	}

	public CNG bake()
	{
		bakedScale *= scale;
		scale = 1;
		return this;
	}

	public CNG child(CNG c)
	{
		if(children == null)
		{
			children = new KList<>();
		}

		children.add(c);
		return this;
	}

	public RNG getRNG()
	{
		return rng;
	}

	public CNG fractureWith(CNG c, double scale)
	{
		fracture = c;
		fscale = scale;
		return this;
	}

	public CNG scale(double c)
	{
		scale = c;
		return this;
	}

	public CNG patch(double c)
	{
		patch = c;
		return this;
	}

	public CNG up(double c)
	{
		up = c;
		return this;
	}

	public CNG down(double c)
	{
		down = c;
		return this;
	}

	public CNG injectWith(NoiseInjector i)
	{
		injector = i;
		return this;
	}

	public <T extends IRare> T fitRarity(KList<T> b, double... dim)
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

		return fit(rarityMapped, dim);
	}

	public <T> T fit(T[] v, double... dim)
	{
		if(v.length == 0)
		{
			return null;
		}

		if(v.length == 1)
		{
			return v[0];
		}

		return v[fit(0, v.length - 1, dim)];
	}

	public <T> T fit(List<T> v, double... dim)
	{
		if(v.size() == 0)
		{
			return null;
		}

		if(v.size() == 1)
		{
			return v.get(0);
		}

		try
		{
			return v.get(fit(0, v.size() - 1, dim));
		}

		catch(Throwable e)
		{

		}

		return v.get(0);
	}

	public int fit(int min, int max, double... dim)
	{
		if(min == max)
		{
			return min;
		}

		double noise = noise(dim);

		return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
	}

	public int fit(double min, double max, double... dim)
	{
		if(min == max)
		{
			return (int) Math.round(min);
		}

		double noise = noise(dim);

		return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
	}

	public double fitDouble(double min, double max, double... dim)
	{
		if(min == max)
		{
			return min;
		}

		double noise = noise(dim);

		return IrisInterpolation.lerp(min, max, noise);
	}

	private double getNoise(double... dim)
	{
		if(isTrueFracturing())
		{
			if(dim.length == 2)
			{
				double scale = noscale ? 1 : this.bakedScale * this.scale;
				double f1 = noscale ? 0 : (fracture != null ? (fracture.noise(dim[0], dim[1]) - 0.5) * fscale : 0D);
				double f2 = noscale ? 0 : (fracture != null ? (fracture.noise(dim[1], dim[0]) - 0.5) * fscale : 0D);
				double x = dim[0] + f1;
				double y = dim[1] + -f1;
				double z = 0D;
				return generator.noise(x * scale, y * scale, z * scale) * opacity;
			}

			else if(dim.length == 3)
			{
				double scale = noscale ? 1 : this.bakedScale * this.scale;
				double f1 = noscale ? 0 : (fracture != null ? (fracture.noise(dim[0], dim[2], dim[1]) - 0.5) * fscale : 0D);
				double f2 = noscale ? 0 : (fracture != null ? (fracture.noise(dim[1], dim[0], dim[2]) - 0.5) * fscale : 0D);
				double f3 = noscale ? 0 : (fracture != null ? (fracture.noise(dim[2], dim[1], dim[0]) - 0.5) * fscale : 0D);
				double x = dim[0] + f1;
				double y = dim[1] + f3;
				double z = dim[2] + f2;
				return generator.noise(x * scale, y * scale, z * scale) * opacity;
			}
		}

		double scale = noscale ? 1 : this.bakedScale * this.scale;
		double f = noscale ? 0 : (fracture != null ? (fracture.noise(dim) - 0.5) * fscale : 0D);
		double x = dim.length > 0 ? dim[0] + f : 0D;
		double y = dim.length > 1 ? dim[1] + -f : 0D;
		double z = dim.length > 2 ? dim[2] + -f : 0D;
		return generator.noise(x * scale, y * scale, z * scale) * opacity;
	}

	public double noise(double... dim)
	{
		double n = getNoise(dim);
		n = power != 1D ? (n < 0 ? -Math.pow(Math.abs(n), power) : Math.pow(n, power)) : n;
		double m = 1;
		hits += oct;
		if(children == null)
		{
			return (n - down + up) * patch;
		}

		for(CNG i : children)
		{
			double[] r = injector.combine(n, i.noise(dim));
			n = r[0];
			m += r[1];
		}

		return ((n / m) - down + up) * patch;
	}

	public CNG pow(double power)
	{
		this.power = power;
		return this;
	}

	public CNG oct(int octaves)
	{
		oct = octaves;
		return this;
	}

	public double getScale()
	{
		return scale;
	}

	public boolean isStatic() {
		return generator != null && generator.isStatic();
	}
}
