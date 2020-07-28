package com.volmit.iris.util;

public class WeightMap<T> extends KMap<T, Double>
{
	private static final long serialVersionUID = 87558033900969389L;
	private boolean modified = false;
	private double lastWeight = 0;

	public double getPercentChance(T t)
	{
		if(totalWeight() <= 0)
		{
			return 0;
		}

		return getWeight(t) / totalWeight();
	}

	public void clear()
	{
		modified = true;
	}

	public WeightMap<T> setWeight(T t, double weight)
	{
		modified = true;
		put(t, weight);

		return this;
	}

	public double getWeight(T t)
	{
		return get(t);
	}

	public double totalWeight()
	{
		if(!modified)
		{
			return lastWeight;
		}

		modified = false;
		Shrinkwrap<Double> s = new Shrinkwrap<Double>(0D);
		forEachKey(Integer.MAX_VALUE, (d) -> s.set(s.get() + 1));
		lastWeight = s.get();

		return lastWeight;
	}
}
