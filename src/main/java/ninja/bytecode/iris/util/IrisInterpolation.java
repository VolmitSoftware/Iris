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

	public static double getLinearNoise(int x, int z, int rad, NoiseProvider n, NoiseProvider f, InterpolationType type)
	{
		int h = rad;
		int fx = x >> h;
		int fz = z >> h;
		int xa = (fx << h);
		int za = (fz << h);
		int xb = ((fx + 1) << h);
		int zb = ((fz + 1) << h);
		double na = n.noise(xa, za);
		double nb = n.noise(xa, zb);
		double nc = n.noise(xb, za);
		double nd = n.noise(xb, zb);
		double px = M.rangeScale(0, 1, xa, xb, x);
		double pz = M.rangeScale(0, 1, za, zb, z);

		return blerp(na, nc, nb, nd, px, pz, type);
	}

	public static double getNoise(int x, int z, int lrad, NoiseProvider n, NoiseProvider fli, InterpolationType linear)
	{
		if(linear.equals(InterpolationType.NONE))
		{
			return n.noise(x, z);
		}

		return getLinearNoise(x, z, lrad, n, fli, linear);
	}
}
