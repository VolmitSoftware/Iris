package ninja.bytecode.iris.util;

import ninja.bytecode.iris.Iris;
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

		if(type.equals(InterpolationType.PARAMETRIC_NH))
		{
			return blerpParametric(a, b, c, d, tx, ty, -0.5);
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

	public static double getLinearNoise(int x, int z, NoiseProvider n, NoiseProvider f, InterpolationType type)
	{
		int h = 29;
		int xa = x - h;
		int za = z - h;
		int xb = x + h;
		int zb = z + h;
		double hfx = f.noise(x, z) * Iris.settings.gen.linearSampleFractureMultiplier;
		double hfz = f.noise(z, x) * Iris.settings.gen.linearSampleFractureMultiplier;
		double na = n.noise(xa + hfx, za + hfz);
		double nb = n.noise(xa + hfx, zb - hfz);
		double nc = n.noise(xb - hfx, za + hfz);
		double nd = n.noise(xb - hfx, zb - hfz);
		double px = M.rangeScale(0, 1, xa, xb, x);
		double pz = M.rangeScale(0, 1, za, zb, z);

		return blerp(na, nc, nb, nd, px, pz, type);
	}

	public static double getBilinearNoise(int x, int z, NoiseProvider n, NoiseProvider f, InterpolationType linear, InterpolationType bilinear)
	{
		int h = 1;
		int fx = x >> h;
		int fz = z >> h;
		int xa = (fx << h) - 15;
		int za = (fz << h) - 15;
		int xb = ((fx + 1) << h) + 15;
		int zb = ((fz + 1) << h) + 15;
		double na = getLinearNoise(xa, za, n, f, linear);
		double nb = getLinearNoise(xa, zb, n, f, linear);
		double nc = getLinearNoise(xb, za, n, f, linear);
		double nd = getLinearNoise(xb, zb, n, f, linear);
		double px = M.rangeScale(0, 1, xa, xb, x);
		double pz = M.rangeScale(0, 1, za, zb, z);

		return blerp(na, nc, nb, nd, px, pz, bilinear);
	}

	public static double getTrilinearNoise(int x, int z, NoiseProvider n, NoiseProvider f, InterpolationType linear, InterpolationType bilinear, InterpolationType trilinear)
	{
		int h = 6;
		int fx = x >> h;
		int fz = z >> h;
		int xa = (fx << h);
		int za = (fz << h);
		int xb = ((fx + 1) << h);
		int zb = ((fz + 1) << h);
		double na = getBilinearNoise(xa, za, n, f, linear, bilinear);
		double nb = getBilinearNoise(xa, zb, n, f, linear, bilinear);
		double nc = getBilinearNoise(xb, za, n, f, linear, bilinear);
		double nd = getBilinearNoise(xb, zb, n, f, linear, bilinear);
		double px = M.rangeScale(0, 1, xa, xb, x);
		double pz = M.rangeScale(0, 1, za, zb, z);

		return blerp(na, nc, nb, nd, px, pz, trilinear);
	}

	public static double getNoise(int x, int z, NoiseProvider n, NoiseProvider fli, InterpolationType linear, InterpolationType bilinear, InterpolationType trilinear)
	{
		if(linear.equals(InterpolationType.NONE))
		{
			return n.noise(x, z);
		}

		else if(bilinear.equals(InterpolationType.NONE))
		{
			return getLinearNoise(x, z, n, fli, linear);
		}

		else if(trilinear.equals(InterpolationType.NONE))
		{
			return getBilinearNoise(x, z, n, fli, linear, bilinear);
		}

		else
		{
			return getTrilinearNoise(x, z, n, fli, linear, bilinear, trilinear);
		}
	}
}
