package ninja.bytecode.iris.util;

import ninja.bytecode.shuriken.math.M;

public class IrisInterpolation
{
	public static double bezier(double t)
	{
		return t * t * (3.0d - 2.0d * t);
	}

	public static double parametric(double t, double alpha)
	{
		double sqt = Math.pow(t, alpha);
		return sqt / (alpha * (sqt - Math.pow(t, alpha - 1)) + 1.0d);
	}

	public static double lerp(double a, double b, double f)
	{
		return a + (f * (b - a));
	}

	public static double lerpBezier(double a, double b, double f)
	{
		return a + (bezier(f) * (b - a));
	}

	public static double sinCenter(double f)
	{
		return Math.sin(f * Math.PI);
	}

	public static double lerpCenterSinBezier(double a, double b, double f)
	{
		return lerpBezier(a, b, sinCenter(f));
	}

	public static double lerpCenterSin(double a, double b, double f)
	{
		return lerpBezier(a, b, sinCenter(f));
	}

	public static double lerpParametric(double a, double b, double f, double v)
	{
		return a + (parametric(f, v) * (b - a));
	}

	public static double blerp(double a, double b, double c, double d, double tx, double ty)
	{
		return lerp(lerp(a, b, tx), lerp(c, d, tx), ty);
	}

	public static double blerp(double a, double b, double c, double d, double tx, double ty, InterpolationType type)
	{
		if(type.equals(InterpolationType.LINEAR))
		{
			return blerp(a, b, c, d, tx, ty);
		}

		if(type.equals(InterpolationType.BEZIER))
		{
			return blerpBezier(a, b, c, d, tx, ty);
		}

		if(type.equals(InterpolationType.PARAMETRIC_2))
		{
			return blerpParametric(a, b, c, d, tx, ty, 2);
		}

		if(type.equals(InterpolationType.PARAMETRIC_4))
		{
			return blerpParametric(a, b, c, d, tx, ty, 4);
		}

		return 0;
	}

	public static double blerpBezier(double a, double b, double c, double d, double tx, double ty)
	{
		return lerpBezier(lerpBezier(a, b, tx), lerpBezier(c, d, tx), ty);
	}

	public static double blerpParametric(double a, double b, double c, double d, double tx, double ty, double v)
	{
		return lerpParametric(lerpParametric(a, b, tx, v), lerpParametric(c, d, tx, v), ty, v);
	}

	public static double hermite(double p0, double p1, double p2, double p3, double mu, double tension, double bias)
	{
		double m0, m1, mu2, mu3;
		double a0, a1, a2, a3;

		mu2 = mu * mu;
		mu3 = mu2 * mu;
		m0 = (p1 - p0) * (1 + bias) * (1 - tension) / 2;
		m0 += (p2 - p1) * (1 - bias) * (1 - tension) / 2;
		m1 = (p2 - p1) * (1 + bias) * (1 - tension) / 2;
		m1 += (p3 - p2) * (1 - bias) * (1 - tension) / 2;
		a0 = 2 * mu3 - 3 * mu2 + 1;
		a1 = mu3 - 2 * mu2 + mu;
		a2 = mu3 - mu2;
		a3 = -2 * mu3 + 3 * mu2;

		return (a0 * p1 + a1 * m0 + a2 * m1 + a3 * p2);
	}

	public static double bihermite(double p00, double p01, double p02, double p03, double p10, double p11, double p12, double p13, double p20, double p21, double p22, double p23, double p30, double p31, double p32, double p33, double mux, double muy, double tension, double bias)
	{
		return hermite(hermite(p00, p01, p02, p03, muy, tension, bias), hermite(p10, p11, p12, p13, muy, tension, bias), hermite(p20, p21, p22, p23, muy, tension, bias), hermite(p30, p31, p32, p33, muy, tension, bias), mux, tension, bias);
	}

	public static double cubic(double p0, double p1, double p2, double p3, double mu)
	{
		double a0, a1, a2, a3, mu2;

		mu2 = mu * mu;
		a0 = p3 - p2 - p0 + p1;
		a1 = p0 - p1 - a0;
		a2 = p2 - p0;
		a3 = p1;

		return a0 * mu * mu2 + a1 * mu2 + a2 * mu + a3;
	}

	public static double bicubic(double p00, double p01, double p02, double p03, double p10, double p11, double p12, double p13, double p20, double p21, double p22, double p23, double p30, double p31, double p32, double p33, double mux, double muy)
	{
		return cubic(cubic(p00, p01, p02, p03, muy), cubic(p10, p11, p12, p13, muy), cubic(p20, p21, p22, p23, muy), cubic(p30, p31, p32, p33, muy), mux);
	}

	public static double getBilinearNoise(int x, int z, double rad, NoiseProvider n)
	{
		int fx = (int) Math.floor(x / rad);
		int fz = (int) Math.floor(z / rad);
		int x1 = (int) Math.round(fx * rad);
		int z1 = (int) Math.round(fz * rad);
		int x2 = (int) Math.round((fx + 1) * rad);
		int z2 = (int) Math.round((fz + 1) * rad);
		double px = M.rangeScale(0, 1, x1, x2, x);
		double pz = M.rangeScale(0, 1, z1, z2, z);
		//@builder
		return blerpBezier( 
				n.noise(x1, z1), 
				n.noise(x2, z1), 
				n.noise(x1, z2), 
				n.noise(x2, z2),
				px, pz);
		//@done
	}

	public static double getBicubicNoise(int x, int z, double rad, NoiseProvider n)
	{
		int fx = (int) Math.floor(x / rad);
		int fz = (int) Math.floor(z / rad);
		int x0 = (int) Math.round((fx - 1) * rad);
		int z0 = (int) Math.round((fz - 1) * rad);
		int x1 = (int) Math.round(fx * rad);
		int z1 = (int) Math.round(fz * rad);
		int x2 = (int) Math.round((fx + 1) * rad);
		int z2 = (int) Math.round((fz + 1) * rad);
		int x3 = (int) Math.round((fx + 2) * rad);
		int z3 = (int) Math.round((fz + 2) * rad);
		double px = M.rangeScale(0, 1, x1, x2, x);
		double pz = M.rangeScale(0, 1, z1, z2, z);
		//@builder
		return bicubic(
				n.noise(x0, z0), 
				n.noise(x0, z1), 
				n.noise(x0, z2), 
				n.noise(x0, z3), 
				n.noise(x1, z0), 
				n.noise(x1, z1), 
				n.noise(x1, z2), 
				n.noise(x1, z3), 
				n.noise(x2, z0), 
				n.noise(x2, z1), 
				n.noise(x2, z2), 
				n.noise(x2, z3), 
				n.noise(x3, z0), 
				n.noise(x3, z1), 
				n.noise(x3, z2), 
				n.noise(x3, z3), 
				px, pz);
		//@done
	}

	public static double getHermiteNoise(int x, int z, double rad, NoiseProvider n)
	{
		int fx = (int) Math.floor(x / rad);
		int fz = (int) Math.floor(z / rad);
		int x0 = (int) Math.round((fx - 1) * rad);
		int z0 = (int) Math.round((fz - 1) * rad);
		int x1 = (int) Math.round(fx * rad);
		int z1 = (int) Math.round(fz * rad);
		int x2 = (int) Math.round((fx + 1) * rad);
		int z2 = (int) Math.round((fz + 1) * rad);
		int x3 = (int) Math.round((fx + 2) * rad);
		int z3 = (int) Math.round((fz + 2) * rad);
		double px = M.rangeScale(0, 1, x1, x2, x);
		double pz = M.rangeScale(0, 1, z1, z2, z);
		//@builder
		return bihermite(
				n.noise(x0, z0), 
				n.noise(x0, z1), 
				n.noise(x0, z2), 
				n.noise(x0, z3), 
				n.noise(x1, z0), 
				n.noise(x1, z1), 
				n.noise(x1, z2), 
				n.noise(x1, z3), 
				n.noise(x2, z0), 
				n.noise(x2, z1), 
				n.noise(x2, z2), 
				n.noise(x2, z3), 
				n.noise(x3, z0), 
				n.noise(x3, z1), 
				n.noise(x3, z2), 
				n.noise(x3, z3), 
				px, pz, 0.00001, 0.5);
		//@done
	}
}
