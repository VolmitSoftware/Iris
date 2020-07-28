package com.volmit.iris.util;

/**
 * Provides an incredibly fast averaging object. It swaps values from a sum
 * using an array. Averages do not use any form of looping. An average of 10,000
 * entries is the same speed as an average with 5 entries.
 * 
 * @author cyberpwn
 *
 */
public class Average {
	protected double[] values;
	private double average;
	private double lastSum;
	private boolean dirty;
	protected int cursor;
	private boolean brandNew;

	/**
	 * Create an average holder
	 * 
	 * @param size the size of entries to keep
	 */
	public Average(int size) {
		values = new double[size];
		DoubleArrayUtils.fill(values, 0);
		brandNew = true;
		average = 0;
		cursor = 0;
		lastSum = 0;
		dirty = false;
	}

	/**
	 * Put a value into the average (rolls over if full)
	 * 
	 * @param i the value
	 */
	public void put(double i) {

		dirty = true;
		
		if(brandNew)
		{
			DoubleArrayUtils.fill(values, i);
			lastSum = size() * i;
			brandNew = false;
			return;
		}
		
		double current = values[cursor];
		lastSum = (lastSum - current) + i;
		values[cursor] = i;
		cursor = cursor + 1 < size() ? cursor + 1 : 0;
	}

	/**
	 * Get the current average
	 * 
	 * @return the average
	 */
	public double getAverage() {
		if (dirty) {
			calculateAverage();
			return getAverage();
		}

		return average;
	}

	private void calculateAverage() {
		average = lastSum / (double) size();
		dirty = false;
	}
	
	public int size()
	{
		return values.length;
	}
	
	public boolean isDirty()
	{
		return dirty;
	}
}
