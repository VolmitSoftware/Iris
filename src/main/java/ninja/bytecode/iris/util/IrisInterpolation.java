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
	
	public static double lerpParametric(double a, double b, double f, double v)
	{
		return a + (parametric(f, v) * (b - a));
	}
	
	public static double blerp(double a, double b, double c, double d, double tx, double ty)
	{
		return lerp(lerp(a, b, tx), lerp(c, d, tx), ty);
	}
	
	public static double blerpBezier(double a, double b, double c, double d, double tx, double ty)
	{
		return lerpBezier(lerpBezier(a, b, tx), lerpBezier(c, d, tx), ty);
	}
	
	public static double blerpParametric(double a, double b, double c, double d, double tx, double ty, double v)
	{
		return lerpParametric(lerpParametric(a, b, tx, v), lerpParametric(c, d, tx, v), ty, v);
	}
	
	public static double getLinearNoise(int x, int z, NoiseProvider n)
	{
		int h = 29;
		int xa = x - h;
		int za = z - h;
		int xb = x + h;
		int zb = z + h;
		double na = n.noise(xa, za);
		double nb = n.noise(xa, zb);
		double nc = n.noise(xb, za);
		double nd = n.noise(xb, zb);
		double px = M.rangeScale(0, 1, xa, xb, x);
		double pz = M.rangeScale(0, 1, za, zb, z);

		return blerpBezier(na, nc, nb, nd, px, pz);
	}
	
	public static double getBilinearNoise(int x, int z, NoiseProvider n)
	{
		int h = 1;
		int fx = x >> h;
		int fz = z >> h;
		int xa = (fx << h) - 15;
		int za = (fz << h) - 15;
		int xb = ((fx + 1) << h) + 15;
		int zb = ((fz + 1) << h) + 15;
		double na = getLinearNoise(xa, za, n);
		double nb = getLinearNoise(xa, zb, n);
		double nc = getLinearNoise(xb, za, n);
		double nd = getLinearNoise(xb, zb, n);
		double px = M.rangeScale(0, 1, xa, xb, x);
		double pz = M.rangeScale(0, 1, za, zb, z);

		return blerpBezier(na, nc, nb, nd, px, pz);
	}

	public static double getBicubicNoise(int x, int z, NoiseProvider n)
	{
		int h = 6;
		int fx = x >> h;
		int fz = z >> h;
		int xa = (fx << h);
		int za = (fz << h);
		int xb = ((fx + 1) << h);
		int zb = ((fz + 1) << h);
		double na = getBilinearNoise(xa, za, n);
		double nb = getBilinearNoise(xa, zb, n);
		double nc = getBilinearNoise(xb, za, n);
		double nd = getBilinearNoise(xb, zb, n);
		double px = M.rangeScale(0, 1, xa, xb, x);
		double pz = M.rangeScale(0, 1, za, zb, z);

		return blerpBezier(na, nc, nb, nd, px, pz);
	}
}
