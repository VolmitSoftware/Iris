package ninja.bytecode.iris.util;

import java.util.function.Function;

public class PolygonGenerator
{
	private double[] rarity;
	private CNG[] gen;
	private int bits;
	private int possibilities;
	private boolean useRarity;

	public PolygonGenerator(RNG rng, int possibilities, double scale, int octaves, Function<CNG, CNG> factory)
	{
		useRarity = false;
		bits = 1;
		this.possibilities = possibilities;

		while(Math.pow(2, bits) <= possibilities)
		{
			bits++;
		}

		bits++;
		bits = bits > 32 ? 32 : bits;
		rarity = new double[possibilities];
		gen = new CNG[bits];

		for(int i = 0; i < bits; i++)
		{
			gen[i] = new CNG(rng.nextParallelRNG(2118 + (i * 3305)), 1D, 1).scale(scale / possibilities);
			gen[i] = factory.apply(gen[i]);
		}
	}

	public PolygonGenerator useRarity()
	{
		useRarity = true;
		return this;
	}

	public void setRarity(int index, double r)
	{
		rarity[index] = 1D - Math.pow(0.5, r);
	}

	public boolean hasBorder(int checks, double distance, double... dims)
	{
		int current = getIndex(dims);
		double ajump = 360D / (double) checks;

		if(dims.length == 2)
		{
			for(int i = 0; i < checks; i++)
			{
				double dx = M.sin((float) Math.toRadians(ajump * i));
				double dz = M.cos((float) Math.toRadians(ajump * i));
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
				double dx = M.sin((float) Math.toRadians(ajump * i));
				double dz = M.cos((float) Math.toRadians(ajump * i));
				double dy = Math.tan(Math.toRadians(ajump * i));
				if(current != getIndex((dx * distance) + dims[0], (dz * distance) + dims[1], (dy * distance) + dims[2]))
				{
					return true;
				}
			}
		}

		return false;
	}

	public boolean hasBorder3D(int checks, double distance, double... dims)
	{
		int current = getIndex(dims);
		double ajump = 360D / (double) checks;
		int hit = -1;

		if(dims.length == 3)
		{
			for(int i = 0; i < checks; i++)
			{
				double dx = M.sin((float) Math.toRadians(ajump * i));
				double dz = M.cos((float) Math.toRadians(ajump * i));
				double dy = Math.tan(Math.toRadians(ajump * i));
				int d = getIndex((dx * distance) + dims[0], (dz * distance) + dims[1], (dy * distance) + dims[2]);
				if(current != d)
				{
					if(hit >= 0 && hit != current && hit != d)
					{
						return true;
					}

					if(hit < 0)
					{
						hit = d;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Returns 0.0 to 1.0 where 0.0 is directly on the border of another region and
	 * 1.0 is perfectly in the center of a region
	 * 
	 * @param x
	 *            the x
	 * @param z
	 *            the z
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
		int adjusted = 0;
		double[] noise = new double[gen.length];

		for(int i = 0; i < gen.length; i++)
		{
			data |= (noise[i] = gen[i].noise(dim)) > 0.5 ? i == 0 ? 1 : 1 << i : 0;
		}

		if(!useRarity)
		{
			return data % possibilities;
		}

		double r = rarity[data % possibilities];

		for(int i = 0; i < gen.length; i++)
		{
			adjusted |= noise[i] > r ? i == 0 ? 1 : 1 << i : 0;
		}

		return adjusted % possibilities;
	}

	public static class EnumPolygonGenerator<T> extends PolygonGenerator
	{
		private T[] choices;


		public EnumPolygonGenerator(RNG rng, double scale, int octaves, T[] choices, Function<CNG, CNG> factory)
		{
			super(rng, choices.length, scale / (double) choices.length, octaves, factory);
			this.choices = choices;
		}

		public EnumPolygonGenerator<T> useRarity()
		{
			super.useRarity();
			return this;
		}

		@SuppressWarnings("unchecked")
		public EnumPolygonGenerator(RNG rng, double scale, int octaves, KList<T> c, KMap<T, Double> choiceRarities, Function<CNG, CNG> factory)
		{
			super(rng, choiceRarities.size(), scale / (double) choiceRarities.size(), octaves, factory);
			this.choices = (T[]) c.toArray();
			int m = 0;

			for(T i : c)
			{
				setRarity(m++, choiceRarities.get(i));
			}
		}

		public void setRarity(T t, double rarity)
		{
			for(int i = 0; i < choices.length; i++)
			{
				if(choices[i].equals(t))
				{
					setRarity(i, rarity);
					return;
				}
			}
		}

		public T getChoice(double... dim)
		{
			return choices[getIndex(dim)];
		}
	}
}