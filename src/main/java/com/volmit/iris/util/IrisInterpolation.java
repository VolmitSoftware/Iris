package com.volmit.iris.util;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.object.InterpolationMethod;
import com.volmit.iris.object.NoiseStyle;

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

	public static float lerpf(float a, float b, float f)
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

	public static double blerpSinCenter(double a, double b, double c, double d, double tx, double ty)
	{
		return lerpCenterSin(lerpCenterSin(a, b, tx), lerpCenterSin(c, d, tx), ty);
	}

	public static double blerpParametric(double a, double b, double c, double d, double tx, double ty, double v)
	{
		return lerpParametric(lerpParametric(a, b, tx, v), lerpParametric(c, d, tx, v), ty, v);
	}

	public static double hermiteBezier(double p0, double p1, double p2, double p3, double mu, double tension, double bias)
	{
		return bezier(hermite(p0, p1, p2, p3, mu, tension, bias));
	}

	public static double hermiteParametric(double p0, double p1, double p2, double p3, double mu, double tension, double bias, double a)
	{
		return parametric(hermite(p0, p1, p2, p3, mu, tension, bias), a);
	}

	public static double bihermiteBezier(double p00, double p01, double p02, double p03, double p10, double p11, double p12, double p13, double p20, double p21, double p22, double p23, double p30, double p31, double p32, double p33, double mux, double muy, double tension, double bias)
	{
		//@builder
		return hermiteBezier(
				hermiteBezier(p00, p01, p02, p03, muy, tension, bias), 
				hermiteBezier(p10, p11, p12, p13, muy, tension, bias), 
				hermiteBezier(p20, p21, p22, p23, muy, tension, bias), 
				hermiteBezier(p30, p31, p32, p33, muy, tension, bias), 
				mux, tension, bias);
		//@done
	}

	public static double bihermiteParametric(double p00, double p01, double p02, double p03, double p10, double p11, double p12, double p13, double p20, double p21, double p22, double p23, double p30, double p31, double p32, double p33, double mux, double muy, double tension, double bias, double a)
	{
		//@builder
		return hermiteParametric(
				hermiteParametric(p00, p01, p02, p03, muy, tension, bias, a), 
				hermiteParametric(p10, p11, p12, p13, muy, tension, bias, a), 
				hermiteParametric(p20, p21, p22, p23, muy, tension, bias, a), 
				hermiteParametric(p30, p31, p32, p33, muy, tension, bias, a), 
				mux, tension, bias, a);
		//@done
	}

	public static double cubicBezier(double p0, double p1, double p2, double p3, double mu)
	{
		return bezier(cubic(p0, p1, p2, p3, mu));
	}

	public static double cubicParametric(double p0, double p1, double p2, double p3, double mu, double a)
	{
		return parametric(cubic(p0, p1, p2, p3, mu), a);
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

	public static double getTriStarcast(int x, int y, int z, double rad, double checks, NoiseProvider n)
	{
		return (getStarcast(x, z, rad, checks, n) + getStarcast(x, y, rad, checks, n) + getStarcast(y, z, rad, checks, n)) / 3D;
	}

	public static double bicubic(double p00, double p01, double p02, double p03, double p10, double p11, double p12, double p13, double p20, double p21, double p22, double p23, double p30, double p31, double p32, double p33, double mux, double muy)
	{
		//@builder
		return cubic(
				cubic(p00, p01, p02, p03, muy), 
				cubic(p10, p11, p12, p13, muy),
				cubic(p20, p21, p22, p23, muy), 
				cubic(p30, p31, p32, p33, muy),
				mux);
		//@done
	}

	public static double bihermite(double p00, double p01, double p02, double p03, double p10, double p11, double p12, double p13, double p20, double p21, double p22, double p23, double p30, double p31, double p32, double p33, double mux, double muy, double tension, double bias)
	{
		//@builder
		return hermite(
				hermite(p00, p01, p02, p03, muy, tension, bias), 
				hermite(p10, p11, p12, p13, muy, tension, bias), 
				hermite(p20, p21, p22, p23, muy, tension, bias), 
				hermite(p30, p31, p32, p33, muy, tension, bias), 
				mux, tension, bias);
		//@done
	}

	public static double trihermite(double p000, double p001, double p002, double p003, double p010, double p011, double p012, double p013, double p020, double p021, double p022, double p023, double p030, double p031, double p032, double p033, double p100, double p101, double p102, double p103, double p110, double p111, double p112, double p113, double p120, double p121, double p122, double p123, double p130, double p131, double p132, double p133, double p200, double p201, double p202, double p203, double p210, double p211, double p212, double p213, double p220, double p221, double p222, double p223, double p230, double p231, double p232, double p233, double p300, double p301, double p302, double p303, double p310, double p311, double p312, double p313, double p320, double p321, double p322, double p323, double p330, double p331, double p332, double p333, double mux, double muy, double muz, double tension, double bias)
	{
		//@builder
		return hermite(
				bihermite(p000, p001, p002, p003,
						p010, p011, p012, p013, 
						p020, p021, p022, p023, 
						p030, p031, p032, p033,
						mux, muy, tension, bias), 
				bihermite(p100, p101, p102, p103,
						p110, p111, p112, p113, 
						p120, p121, p122, p123, 
						p130, p131, p132, p133,
						mux, muy, tension, bias), 
				bihermite(p200, p201, p202, p203,
						p210, p211, p212, p213, 
						p220, p221, p222, p223, 
						p230, p231, p232, p233,
						mux, muy, tension, bias), 
				bihermite(p300, p301, p302, p303,
						p310, p311, p312, p313, 
						p320, p321, p322, p323, 
						p330, p331, p332, p333,
						mux, muy, tension, bias), 
				muz, tension, bias);
		//@done
	}

	public static double tricubic(double p000, double p001, double p002, double p003, double p010, double p011, double p012, double p013, double p020, double p021, double p022, double p023, double p030, double p031, double p032, double p033, double p100, double p101, double p102, double p103, double p110, double p111, double p112, double p113, double p120, double p121, double p122, double p123, double p130, double p131, double p132, double p133, double p200, double p201, double p202, double p203, double p210, double p211, double p212, double p213, double p220, double p221, double p222, double p223, double p230, double p231, double p232, double p233, double p300, double p301, double p302, double p303, double p310, double p311, double p312, double p313, double p320, double p321, double p322, double p323, double p330, double p331, double p332, double p333, double mux, double muy, double muz)
	{
		//@builder
		return cubic(
				bicubic(p000, p001, p002, p003,
						p010, p011, p012, p013, 
						p020, p021, p022, p023, 
						p030, p031, p032, p033,
						mux, muy), 
				bicubic(p100, p101, p102, p103,
						p110, p111, p112, p113, 
						p120, p121, p122, p123, 
						p130, p131, p132, p133,
						mux, muy), 
				bicubic(p200, p201, p202, p203,
						p210, p211, p212, p213, 
						p220, p221, p222, p223, 
						p230, p231, p232, p233,
						mux, muy), 
				bicubic(p300, p301, p302, p303,
						p310, p311, p312, p313, 
						p320, p321, p322, p323, 
						p330, p331, p332, p333,
						mux, muy), 
				muz);
		//@done
	}

	public static double lerp(double a, double b, double f)
	{
		return a + (f * (b - a));
	}

	public static double blerp(double a, double b, double c, double d, double tx, double ty)
	{
		return lerp(lerp(a, b, tx), lerp(c, d, tx), ty);
	}

	public static double trilerp(double v1, double v2, double v3, double v4, double v5, double v6, double v7, double v8, double x, double y, double z)
	{
		return lerp(blerp(v1, v2, v3, v4, x, y), blerp(v5, v6, v7, v8, x, y), z);
	}

	public static double bicubicBezier(double p00, double p01, double p02, double p03, double p10, double p11, double p12, double p13, double p20, double p21, double p22, double p23, double p30, double p31, double p32, double p33, double mux, double muy)
	{
		//@builder
		return cubicBezier(
				cubicBezier(p00, p01, p02, p03, muy), 
				cubicBezier(p10, p11, p12, p13, muy), 
				cubicBezier(p20, p21, p22, p23, muy), 
				cubicBezier(p30, p31, p32, p33, muy),
				mux);
		//@done
	}

	public static double bicubicParametric(double p00, double p01, double p02, double p03, double p10, double p11, double p12, double p13, double p20, double p21, double p22, double p23, double p30, double p31, double p32, double p33, double mux, double muy, double a)
	{
		//@builder
		return cubicParametric(
				cubicParametric(p00, p01, p02, p03, muy,a), 
				cubicParametric(p10, p11, p12, p13, muy,a), 
				cubicParametric(p20, p21, p22, p23, muy,a), 
				cubicParametric(p30, p31, p32, p33, muy,a), 
				mux, a);
		//@done
	}

	public static CNG cng = NoiseStyle.SIMPLEX.create(new RNG());

	public static double getBilinearNoise(int x, int z, double rad, NoiseProvider n)
	{
		int fx = (int) Math.floor(x / rad);
		int fz = (int) Math.floor(z / rad);
		int x1 = (int) Math.round(fx * rad);
		int z1 = (int) Math.round(fz * rad);
		int x2 = (int) Math.round((fx + 1) * rad);
		int z2 = (int) Math.round((fz + 1) * rad);

		double px = rangeScale(0, 1, x1, x2, x);
		double pz = rangeScale(0, 1, z1, z2, z);
		//@builder
		return blerp( 
				n.noise(x1, z1), 
				n.noise(x2, z1), 
				n.noise(x1, z2), 
				n.noise(x2, z2),
				px, pz);
		//@done
	}

	public static double getStarcast(int x, int z, double rad, double checks, NoiseProvider n)
	{
		double m = (360 / checks);
		double v = 0;

		for(int i = 0; i < 360; i += m)
		{
			double sin = Math.sin(Math.toRadians(i));
			double cos = Math.cos(Math.toRadians(i));
			double cx = x + ((rad * cos) - (rad * sin));
			double cz = z + ((rad * sin) + (rad * cos));
			v += n.noise(cx, cz);
		}

		return v / checks;
	}

	public static double getBilinearBezierNoise(int x, int z, double rad, NoiseProvider n)
	{
		int fx = (int) Math.floor(x / rad);
		int fz = (int) Math.floor(z / rad);
		int x1 = (int) Math.round(fx * rad);
		int z1 = (int) Math.round(fz * rad);
		int x2 = (int) Math.round((fx + 1) * rad);
		int z2 = (int) Math.round((fz + 1) * rad);
		double px = rangeScale(0, 1, x1, x2, x);
		double pz = rangeScale(0, 1, z1, z2, z);
		//@builder
		return blerpBezier( 
				n.noise(x1, z1), 
				n.noise(x2, z1), 
				n.noise(x1, z2), 
				n.noise(x2, z2),
				px, pz);
		//@done
	}

	public static double getBilinearParametricNoise(int x, int z, double rad, NoiseProvider n, double a)
	{
		int fx = (int) Math.floor(x / rad);
		int fz = (int) Math.floor(z / rad);
		int x1 = (int) Math.round(fx * rad);
		int z1 = (int) Math.round(fz * rad);
		int x2 = (int) Math.round((fx + 1) * rad);
		int z2 = (int) Math.round((fz + 1) * rad);
		double px = rangeScale(0, 1, x1, x2, x);
		double pz = rangeScale(0, 1, z1, z2, z);
		//@builder
		return blerpParametric( 
				n.noise(x1, z1), 
				n.noise(x2, z1), 
				n.noise(x1, z2), 
				n.noise(x2, z2),
				px, pz, a);
		//@done
	}

	public static double getBilinearCenterSineNoise(int x, int z, double rad, NoiseProvider n)
	{
		int fx = (int) Math.floor(x / rad);
		int fz = (int) Math.floor(z / rad);
		int x1 = (int) Math.round(fx * rad);
		int z1 = (int) Math.round(fz * rad);
		int x2 = (int) Math.round((fx + 1) * rad);
		int z2 = (int) Math.round((fz + 1) * rad);
		double px = rangeScale(0, 1, x1, x2, x);
		double pz = rangeScale(0, 1, z1, z2, z);
		//@builder
		return blerpSinCenter( 
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
		double px = rangeScale(0, 1, x1, x2, x);
		double pz = rangeScale(0, 1, z1, z2, z);
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

	public static double getBicubicBezierNoise(int x, int z, double rad, NoiseProvider n)
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
		double px = rangeScale(0, 1, x1, x2, x);
		double pz = rangeScale(0, 1, z1, z2, z);
		//@builder
		return bicubicBezier(
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

	public static double getBicubicParametricNoise(int x, int z, double rad, NoiseProvider n, double a)
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
		double px = rangeScale(0, 1, x1, x2, x);
		double pz = rangeScale(0, 1, z1, z2, z);
		//@builder
		return bicubicParametric(
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
				px, pz, a);
		//@done
	}

	public static double getHermiteNoise(int x, int z, double rad, NoiseProvider n)
	{
		return getHermiteNoise(x, z, rad, n, 0.5, 0);
	}

	public static double getHermiteBezierNoise(int x, int z, double rad, NoiseProvider n)
	{
		return getHermiteBezierNoise(x, z, rad, n, 0.5, 0);
	}

	public static double getHermiteParametricNoise(int x, int z, double rad, NoiseProvider n, double a)
	{
		return getHermiteParametricNoise(x, z, rad, n, 0.5, 0, a);
	}

	public static double getHermiteNoise(int x, int z, double rad, NoiseProvider n, double t, double b)
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
		double px = rangeScale(0, 1, x1, x2, x);
		double pz = rangeScale(0, 1, z1, z2, z);
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
				px, pz, t, b);
		//@done
	}

	public static double getHermiteBezierNoise(int x, int z, double rad, NoiseProvider n, double t, double b)
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
		double px = rangeScale(0, 1, x1, x2, x);
		double pz = rangeScale(0, 1, z1, z2, z);
		//@builder
		return bihermiteBezier(
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
				px, pz, t, b);
		//@done
	}

	public static double getHermiteParametricNoise(int x, int z, double rad, NoiseProvider n, double t, double b, double a)
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
		double px = rangeScale(0, 1, x1, x2, x);
		double pz = rangeScale(0, 1, z1, z2, z);
		//@builder
		return bihermiteParametric(
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
				px, pz, t, b, a);
		//@done
	}

	public static double getRealRadius(InterpolationMethod method, double h)
	{
		AtomicDouble rad = new AtomicDouble(h);
		AtomicDouble accessX = new AtomicDouble();
		AtomicDouble accessZ = new AtomicDouble();
		NoiseProvider np = (x1, z1) -> {
			double d = Math.max(Math.abs(x1), Math.abs(z1));
			if(d > rad.get())
			{
				rad.set(d);
			}
			return 0;
		};
		getNoise(method, 0, 0, h, np);
		return rad.get();
	}

	public static double getNoise(InterpolationMethod method, int x, int z, double h, NoiseProvider n)
	{
		if(method.equals(InterpolationMethod.BILINEAR))
		{
			return getBilinearNoise(x, z, h, n);
		}

		else if(method.equals(InterpolationMethod.STARCAST_3))
		{
			return getStarcast(x, z, h, 3D, n);
		}

		else if(method.equals(InterpolationMethod.STARCAST_6))
		{
			return getStarcast(x, z, h, 6D, n);
		}

		else if(method.equals(InterpolationMethod.STARCAST_9))
		{
			return getStarcast(x, z, h, 9D, n);
		}

		else if(method.equals(InterpolationMethod.STARCAST_12))
		{
			return getStarcast(x, z, h, 12D, n);
		}

		else if(method.equals(InterpolationMethod.BILINEAR_STARCAST_3))
		{
			return getStarcast(x, z, h, 3D, (xx, zz) -> getBilinearNoise((int) xx, (int) zz, h, n));
		}

		else if(method.equals(InterpolationMethod.BILINEAR_STARCAST_6))
		{
			return getStarcast(x, z, h, 6D, (xx, zz) -> getBilinearNoise((int) xx, (int) zz, h, n));
		}

		else if(method.equals(InterpolationMethod.BILINEAR_STARCAST_9))
		{
			return getStarcast(x, z, h, 9D, (xx, zz) -> getBilinearNoise((int) xx, (int) zz, h, n));
		}

		else if(method.equals(InterpolationMethod.BILINEAR_STARCAST_12))
		{
			return getStarcast(x, z, h, 12D, (xx, zz) -> getBilinearNoise((int) xx, (int) zz, h, n));
		}

		else if(method.equals(InterpolationMethod.HERMITE_STARCAST_3))
		{
			return getStarcast(x, z, h, 3D, (xx, zz) -> getHermiteNoise((int) xx, (int) zz, h, n, 0D, 0D));
		}

		else if(method.equals(InterpolationMethod.HERMITE_STARCAST_6))
		{
			return getStarcast(x, z, h, 6D, (xx, zz) -> getHermiteNoise((int) xx, (int) zz, h, n, 0D, 0D));
		}

		else if(method.equals(InterpolationMethod.HERMITE_STARCAST_9))
		{
			return getStarcast(x, z, h, 9D, (xx, zz) -> getHermiteNoise((int) xx, (int) zz, h, n, 0D, 0D));
		}

		else if(method.equals(InterpolationMethod.HERMITE_STARCAST_12))
		{
			return getStarcast(x, z, h, 12D, (xx, zz) -> getHermiteNoise((int) xx, (int) zz, h, n, 0D, 0D));
		}

		else if(method.equals(InterpolationMethod.BICUBIC))
		{
			return getBicubicNoise(x, z, h, n);
		}

		else if(method.equals(InterpolationMethod.BILINEAR_BEZIER))
		{
			return getBilinearBezierNoise(x, z, h, n);
		}

		else if(method.equals(InterpolationMethod.BILINEAR_PARAMETRIC_2))
		{
			return getBilinearParametricNoise(x, z, h, n, 2);
		}

		else if(method.equals(InterpolationMethod.BILINEAR_PARAMETRIC_4))
		{
			return getBilinearParametricNoise(x, z, h, n, 4);
		}

		else if(method.equals(InterpolationMethod.BILINEAR_PARAMETRIC_1_5))
		{
			return getBilinearParametricNoise(x, z, h, n, 1.5);
		}

		else if(method.equals(InterpolationMethod.BICUBIC))
		{
			return getBilinearNoise(x, z, h, n);
		}

		else if(method.equals(InterpolationMethod.HERMITE))
		{
			return getHermiteNoise(x, z, h, n);
		}

		else if(method.equals(InterpolationMethod.HERMITE_TENSE))
		{
			return getHermiteNoise(x, z, h, n, 0.8D, 0D);
		}

		else if(method.equals(InterpolationMethod.CATMULL_ROM_SPLINE))
		{
			return getHermiteNoise(x, z, h, n, 1D, 0D);
		}

		else if(method.equals(InterpolationMethod.HERMITE_LOOSE))
		{
			return getHermiteNoise(x, z, h, n, 0D, 0D);
		}

		else if(method.equals(InterpolationMethod.HERMITE_LOOSE_HALF_NEGATIVE_BIAS))
		{
			return getHermiteNoise(x, z, h, n, 0D, -0.5D);
		}

		else if(method.equals(InterpolationMethod.HERMITE_LOOSE_HALF_POSITIVE_BIAS))
		{
			return getHermiteNoise(x, z, h, n, 0D, 0.5D);
		}

		else if(method.equals(InterpolationMethod.HERMITE_LOOSE_FULL_NEGATIVE_BIAS))
		{
			return getHermiteNoise(x, z, h, n, 0D, -1D);
		}

		else if(method.equals(InterpolationMethod.HERMITE_LOOSE_FULL_POSITIVE_BIAS))
		{
			return getHermiteNoise(x, z, h, n, 0D, 1D);
		}

		return n.noise(x, z);
	}

	public static double rangeScale(double amin, double amax, double bmin, double bmax, double b)
	{
		return amin + ((amax - amin) * ((b - bmin) / (bmax - bmin)));
	}
}