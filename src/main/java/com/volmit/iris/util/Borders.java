package com.volmit.iris.util;

public class Borders
{
	public static <T> double getDistanceToBorder(double x, double z, int samples, double minRadius, double maxRadius, double jump, BorderCheck<T> check)
	{
		double offset = 0;
		double fract = 1;

		for(double i = minRadius; i < maxRadius; i += jump * fract)
		{
			offset += jump / 3D;
			fract += 0.333;

			if(isBorderWithin(x, z, samples, maxRadius, offset, check))
			{
				return minRadius;
			}
		}

		return maxRadius;
	}

	public static <T> boolean isBorderWithin(double x, double z, int samples, double radius, double offset, BorderCheck<T> check)
	{
		T center = check.get(x, z);
		double ajump = Math.toRadians(360D / (double) samples) + offset;

		for(int i = 0; i < samples; i++)
		{
			double dx = M.sin((float) ajump * i) * radius;
			double dz = M.cos((float) ajump * i) * radius;

			if(!center.equals(check.get(x + dx, z + dz)))
			{
				return true;
			}
		}

		return false;
	}
}