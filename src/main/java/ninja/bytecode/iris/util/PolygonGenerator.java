package ninja.bytecode.iris.util;

import java.util.function.Function;

import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class PolygonGenerator
{
	private CNG[] gen;
	private int bits;
	private int possibilities;

	public PolygonGenerator(RNG rng, int possibilities, double scale, int octaves, Function<CNG, CNG> factory)
	{
		bits = 1;
		this.possibilities = possibilities;

		while(Math.pow(2, bits) <= possibilities)
		{
			bits++;
		}
		
		bits++;
		bits = bits > 32 ? 32 : bits;
		gen = new CNG[bits];

		for(int i = 0; i < bits; i++)
		{
			gen[i] = new CNG(rng.nextParallelRNG(2118 + (i * 3305)), 1D, 1).scale(scale);
			gen[i] = factory.apply(gen[i]);
		}
	}
	
	/**
	 * Returns 0.0 to 1.0 where 0.0 is directly on the border of another region and 1.0 is perfectly in the center of a region
	 * @param x the x
	 * @param z the z
	 * @return the closest neighbor threshold.
	 */
	public double getClosestNeighbor(double... dim)
	{
		double closest = 0.5;
		
		for(int i = 0; i < gen.length; i++)
		{
			double distance = Math.abs(gen[i].noise(dim) - 0.5);
			
			if(distance < closest)
			{
				closest = distance;
			}
		}

		return (closest * 2);
	}

	public int getIndex(double... dim)
	{
		int data = 0;

		for(int i = 0; i < gen.length; i++)
		{
			data |= gen[i].noise(dim) > 0.5 ? i == 0 ? 1 : 1 << i : 0;
		}

		return data % possibilities;
	}

	public static class EnumPolygonGenerator<T> extends PolygonGenerator
	{
		private T[] choices;

		public EnumPolygonGenerator(RNG rng, double scale, int octaves, T[] choices, Function<CNG, CNG> factory)
		{
			super(rng, choices.length, scale / (double) choices.length, octaves, factory);
			this.choices = choices;
		}

		public T getChoice(double... dim)
		{
			return choices[super.getIndex(dim)];
		}
	}
}
