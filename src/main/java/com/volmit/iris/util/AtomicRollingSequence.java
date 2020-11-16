package com.volmit.iris.util;

public class AtomicRollingSequence extends AtomicAverage
{
	private double median;
	private double max;
	private double min;
	private boolean dirtyMedian;
	private int dirtyExtremes;
	private boolean precision;

	public AtomicRollingSequence(int size)
	{
		super(size);
		median = 0;
		min = 0;
		max = 0;
		setPrecision(false);
	}

	public double addLast(int amt)
	{
		double f = 0;

		for(int i = 0; i < Math.min(values.length(), amt); i++)
		{
			f += values.get(i);
		}

		return f;
	}

	public void setPrecision(boolean p)
	{
		this.precision = p;
	}

	public boolean isPrecision()
	{
		return precision;
	}

	public double getMin()
	{
		if(dirtyExtremes > (isPrecision() ? 0 : values.length()))
		{
			resetExtremes();
		}

		return min;
	}

	public double getMax()
	{
		if(dirtyExtremes > (isPrecision() ? 0 : values.length()))
		{
			resetExtremes();
		}

		return max;
	}

	public double getMedian()
	{
		if(dirtyMedian)
		{
			recalculateMedian();
		}

		return median;
	}

	private void recalculateMedian()
	{
		median = new KList<Double>().forceAdd(values).sort().middleValue();
		dirtyMedian = false;
	}

	public void resetExtremes()
	{
		max = Integer.MIN_VALUE;
		min = Integer.MAX_VALUE;

		for(int i = 0; i < values.length(); i++)
		{
			double v = values.get(i);
			max = M.max(max, v);
			min = M.min(min, v);
		}

		dirtyExtremes = 0;
	}

	public void put(double i)
	{
		super.put(i);
		dirtyMedian = true;
		dirtyExtremes++;
		max = M.max(max, i);
		min = M.min(min, i);
	}
}
