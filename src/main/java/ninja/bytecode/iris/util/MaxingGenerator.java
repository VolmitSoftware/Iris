package ninja.bytecode.iris.util;

import java.util.function.Function;

import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class MaxingGenerator
{
	private CNG[] gen;
	private int possibilities;

	public MaxingGenerator(RNG rng, int possibilities, double scale, int octaves, Function<CNG, CNG> factory)
	{
		this.possibilities = possibilities;
		gen = new CNG[possibilities];

		for(int i = 0; i < possibilities; i++)
		{
			gen[i] = new CNG(rng.nextRNG(), 1D, 1).scale(scale);
			gen[i] = factory.apply(gen[i]);
		}
	}
	
	public double getEdge(double... dim)
	{
		double b = 0;

		for(int i = 0; i < gen.length; i++)
		{
			double g = Math.pow(gen[i].noise(dim), gen.length);

			if(g > b)
			{
				b = g;
			}
		}

		return b;
	}

	public int getIndex(double... dim)
	{
		double b = 0;
		int index = 0;

		for(int i = 0; i < gen.length; i++)
		{
			double g = Math.pow(gen[i].noise(dim), gen.length);

			if(g > b)
			{
				b = g;
				index = i;
			}
		}

		return index % possibilities;
	}

	public boolean hasBorder(int checks, double distance, double... dims)
	{
		int current = getIndex(dims);
		double ajump = 360D / (double)checks;
		
		if(dims.length == 2)
		{
			for(int i = 0; i < checks; i++)
			{
				double dx = Math.sin(Math.toRadians(ajump * i));
				double dz = Math.cos(Math.toRadians(ajump * i));
				if(current != getIndex((dx * distance) + dims[0], (dz * distance) + dims[1]))
				{
					return true;
				}
			}
		}
		
		if(dims.length == 3)
		{
			for(int i = 0; i < checks; i++)
			{
				double dx = Math.sin(Math.toRadians(ajump * i));
				double dz = Math.cos(Math.toRadians(ajump * i));
				double dy = Math.tan(Math.toRadians(ajump * i));
				if(current != getIndex((dx * distance) + dims[0], (dz * distance) + dims[1], (dy * distance) + dims[2]))
				{
					return true;
				}
			}
		}
		
		return false;
	}
	
	public static class EnumMaxingGenerator<T> extends MaxingGenerator
	{
		private T[] choices;

		public EnumMaxingGenerator(RNG rng, double scale, int octaves, T[] choices, Function<CNG, CNG> factory)
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
