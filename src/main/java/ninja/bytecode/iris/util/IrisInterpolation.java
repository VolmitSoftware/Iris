package ninja.bytecode.iris.util;

import ninja.bytecode.shuriken.math.M;

public class IrisInterpolation
{
	public static double lerp(double a, double b, double f)
	{
		return a + (f * (b - a));
	}
	
	public static double blerp(double a, double b, double c, double d, double tx, double ty)
	{
		return lerp(lerp(a, b, tx), lerp(c, d, tx), ty);
	}
	
	public static double getBilinearNoise(int x, int z, NoiseProvider n)
	{
		int h = 5;
		int fx = x >> h;
		int fz = z >> h;
		int xa = (fx << h) - 2;
		int za = (fz << h) - 2;
		int xb = ((fx + 1) << h) + 2;
		int zb = ((fz + 1) << h) + 2;
		double na = n.noise(xa, za);
		double nb = n.noise(xa, zb);
		double nc = n.noise(xb, za);
		double nd = n.noise(xb, zb);
		double px = M.rangeScale(0, 1, xa, xb, x);
		double pz = M.rangeScale(0, 1, za, zb, z);

		return blerp(na, nc, nb, nd, px, pz);
	}

	public static double getBicubicNoise(int x, int z, NoiseProvider n)
	{
		int h = 5;
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

		return blerp(na, nc, nb, nd, px, pz);
	}
}
