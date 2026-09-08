/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.util.project.interpolation;

import com.google.common.util.concurrent.AtomicDouble;
import art.arcane.iris.engine.object.NoiseStyle;
import art.arcane.volmlib.util.function.NoiseProvider;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.project.noise.CNG;

public class IrisInterpolation extends art.arcane.volmlib.util.interpolation.IrisInterpolation {
    private static final double[][] STARCAST_OFFSETS = createStarcastOffsets();
    public static CNG cng = NoiseStyle.SIMPLEX.create(new RNG());
    private static final ThreadLocal<NoiseSampleCache2D> NOISE_SAMPLE_CACHE_2D = ThreadLocal.withInitial(() -> new NoiseSampleCache2D(64));
    private static final ThreadLocal<NoiseBoundsSampleCache2D> NOISE_BOUNDS_SAMPLE_CACHE_2D = ThreadLocal.withInitial(() -> new NoiseBoundsSampleCache2D(64));

    public static double bezier(double t) {
        return t * t * (3.0d - 2.0d * t);
    }

    public static double parametric(double t, double alpha) {
        double forward = Math.pow(t, alpha);
        return forward / (forward + Math.pow(1D - t, alpha));
    }

    public static float lerpf(float a, float b, float f) {
        return a + (f * (b - a));
    }

    public static double lerpBezier(double a, double b, double f) {
        return a + (bezier(f) * (b - a));
    }

    public static double sinCenter(double f) {
        return Math.sin(f * Math.PI);
    }

    public static double lerpCenterSinBezier(double a, double b, double f) {
        return lerpBezier(a, b, sinCenter(f));
    }

    public static double lerpCenterSin(double a, double b, double f) {
        return lerpBezier(a, b, sinCenter(f));
    }

    public static double lerpParametric(double a, double b, double f, double v) {
        return a + (parametric(f, v) * (b - a));
    }

    public static double blerpBezier(double a, double b, double c, double d, double tx, double ty) {
        return lerpBezier(lerpBezier(a, b, tx), lerpBezier(c, d, tx), ty);
    }

    public static double blerpSinCenter(double a, double b, double c, double d, double tx, double ty) {
        return lerpCenterSin(lerpCenterSin(a, b, tx), lerpCenterSin(c, d, tx), ty);
    }

    public static double blerpParametric(double a, double b, double c, double d, double tx, double ty, double v) {
        return lerpParametric(lerpParametric(a, b, tx, v), lerpParametric(c, d, tx, v), ty, v);
    }

    public static double hermiteBezier(double p0, double p1, double p2, double p3, double mu, double tension, double bias) {
        return bezier(hermite(p0, p1, p2, p3, mu, tension, bias));
    }

    public static double hermiteParametric(double p0, double p1, double p2, double p3, double mu, double tension, double bias, double a) {
        return parametric(hermite(p0, p1, p2, p3, mu, tension, bias), a);
    }

    public static double bihermiteBezier(double p00, double p01, double p02, double p03, double p10, double p11, double p12, double p13, double p20, double p21, double p22, double p23, double p30, double p31, double p32, double p33, double mux, double muy, double tension, double bias) {
        //@builder
        return hermiteBezier(
                hermiteBezier(p00, p01, p02, p03, muy, tension, bias),
                hermiteBezier(p10, p11, p12, p13, muy, tension, bias),
                hermiteBezier(p20, p21, p22, p23, muy, tension, bias),
                hermiteBezier(p30, p31, p32, p33, muy, tension, bias),
                mux, tension, bias);
        //@done
    }

    public static double bihermiteParametric(double p00, double p01, double p02, double p03, double p10, double p11, double p12, double p13, double p20, double p21, double p22, double p23, double p30, double p31, double p32, double p33, double mux, double muy, double tension, double bias, double a) {
        //@builder
        return hermiteParametric(
                hermiteParametric(p00, p01, p02, p03, muy, tension, bias, a),
                hermiteParametric(p10, p11, p12, p13, muy, tension, bias, a),
                hermiteParametric(p20, p21, p22, p23, muy, tension, bias, a),
                hermiteParametric(p30, p31, p32, p33, muy, tension, bias, a),
                mux, tension, bias, a);
        //@done
    }

    public static double cubicBezier(double p0, double p1, double p2, double p3, double mu) {
        return bezier(cubic(p0, p1, p2, p3, mu));
    }

    public static double cubicParametric(double p0, double p1, double p2, double p3, double mu, double a) {
        return parametric(cubic(p0, p1, p2, p3, mu), a);
    }

    public static double bicubicBezier(double p00, double p01, double p02, double p03, double p10, double p11, double p12, double p13, double p20, double p21, double p22, double p23, double p30, double p31, double p32, double p33, double mux, double muy) {
        //@builder
        return cubicBezier(
                cubicBezier(p00, p01, p02, p03, muy),
                cubicBezier(p10, p11, p12, p13, muy),
                cubicBezier(p20, p21, p22, p23, muy),
                cubicBezier(p30, p31, p32, p33, muy),
                mux);
        //@done
    }

    public static double bicubicParametric(double p00, double p01, double p02, double p03, double p10, double p11, double p12, double p13, double p20, double p21, double p22, double p23, double p30, double p31, double p32, double p33, double mux, double muy, double a) {
        //@builder
        return cubicParametric(
                cubicParametric(p00, p01, p02, p03, muy, a),
                cubicParametric(p10, p11, p12, p13, muy, a),
                cubicParametric(p20, p21, p22, p23, muy, a),
                cubicParametric(p30, p31, p32, p33, muy, a),
                mux, a);
        //@done
    }

    public static int getRadiusFactor(int coord, double radius) {
        int radiusInt = (int) radius;
        if (radius == radiusInt && radiusInt > 0 && (radiusInt & (radiusInt - 1)) == 0) {
            return coord >> Integer.numberOfTrailingZeros(radiusInt);
        }
        return (int) Math.floor(coord / radius);
    }

    public static int getRadiusFactor(double coord, double radius) {
        return (int) Math.floor(coord / radius);
    }

    public static double getBilinearNoise(double x, double z, double rad, NoiseProvider n) {
        double fx = Math.floor(x / rad);
        double fz = Math.floor(z / rad);
        double x1 = fx * rad;
        double z1 = fz * rad;
        double x2 = (fx + 1) * rad;
        double z2 = (fz + 1) * rad;

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

    public static double getBilinearBezierNoise(double x, double z, double rad, NoiseProvider n) {
        double fx = Math.floor(x / rad);
        double fz = Math.floor(z / rad);
        double x1 = fx * rad;
        double z1 = fz * rad;
        double x2 = (fx + 1) * rad;
        double z2 = (fz + 1) * rad;
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

    public static double getBilinearParametricNoise(double x, double z, double rad, NoiseProvider n, double a) {
        double fx = Math.floor(x / rad);
        double fz = Math.floor(z / rad);
        double x1 = fx * rad;
        double z1 = fz * rad;
        double x2 = (fx + 1) * rad;
        double z2 = (fz + 1) * rad;
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

    public static double getBilinearCenterSineNoise(double x, double z, double rad, NoiseProvider n) {
        double fx = Math.floor(x / rad);
        double fz = Math.floor(z / rad);
        double x1 = fx * rad;
        double z1 = fz * rad;
        double x2 = (fx + 1) * rad;
        double z2 = (fz + 1) * rad;
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

    public static double getBicubicNoise(double x, double z, double rad, NoiseProvider n) {
        double fx = Math.floor(x / rad);
        double fz = Math.floor(z / rad);
        double x0 = (fx - 1) * rad;
        double z0 = (fz - 1) * rad;
        double x1 = fx * rad;
        double z1 = fz * rad;
        double x2 = (fx + 1) * rad;
        double z2 = (fz + 1) * rad;
        double x3 = (fx + 2) * rad;
        double z3 = (fz + 2) * rad;
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

    public static double getBicubicBezierNoise(double x, double z, double rad, NoiseProvider n) {
        double fx = Math.floor(x / rad);
        double fz = Math.floor(z / rad);
        double x0 = (fx - 1) * rad;
        double z0 = (fz - 1) * rad;
        double x1 = fx * rad;
        double z1 = fz * rad;
        double x2 = (fx + 1) * rad;
        double z2 = (fz + 1) * rad;
        double x3 = (fx + 2) * rad;
        double z3 = (fz + 2) * rad;
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

    public static double getBicubicParametricNoise(double x, double z, double rad, NoiseProvider n, double a) {
        double fx = Math.floor(x / rad);
        double fz = Math.floor(z / rad);
        double x0 = (fx - 1) * rad;
        double z0 = (fz - 1) * rad;
        double x1 = fx * rad;
        double z1 = fz * rad;
        double x2 = (fx + 1) * rad;
        double z2 = (fz + 1) * rad;
        double x3 = (fx + 2) * rad;
        double z3 = (fz + 2) * rad;
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

    public static double getHermiteNoise(double x, double z, double rad, NoiseProvider n) {
        return getHermiteNoise(x, z, rad, n, 0.5, 0);
    }

    public static double getHermiteBezierNoise(double x, double z, double rad, NoiseProvider n) {
        return getHermiteBezierNoise(x, z, rad, n, 0.5, 0);
    }

    public static double getHermiteParametricNoise(double x, double z, double rad, NoiseProvider n, double a) {
        return getHermiteParametricNoise(x, z, rad, n, 0.5, 0, a);
    }

    public static double getHermiteNoise(double x, double z, double rad, NoiseProvider n, double t, double b) {
        double fx = Math.floor(x / rad);
        double fz = Math.floor(z / rad);
        double x0 = (fx - 1) * rad;
        double z0 = (fz - 1) * rad;
        double x1 = fx * rad;
        double z1 = fz * rad;
        double x2 = (fx + 1) * rad;
        double z2 = (fz + 1) * rad;
        double x3 = (fx + 2) * rad;
        double z3 = (fz + 2) * rad;
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

    public static double getHermiteBezierNoise(double x, double z, double rad, NoiseProvider n, double t, double b) {
        double fx = Math.floor(x / rad);
        double fz = Math.floor(z / rad);
        double x0 = (fx - 1) * rad;
        double z0 = (fz - 1) * rad;
        double x1 = fx * rad;
        double z1 = fz * rad;
        double x2 = (fx + 1) * rad;
        double z2 = (fz + 1) * rad;
        double x3 = (fx + 2) * rad;
        double z3 = (fz + 2) * rad;
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

    public static double getHermiteParametricNoise(double x, double z, double rad, NoiseProvider n, double t, double b, double a) {
        double fx = Math.floor(x / rad);
        double fz = Math.floor(z / rad);
        double x0 = (fx - 1) * rad;
        double z0 = (fz - 1) * rad;
        double x1 = fx * rad;
        double z1 = fz * rad;
        double x2 = (fx + 1) * rad;
        double z2 = (fz + 1) * rad;
        double x3 = (fx + 2) * rad;
        double z3 = (fz + 2) * rad;
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

    public static double getRealRadius(InterpolationMethod method, double h) {
        AtomicDouble rad = new AtomicDouble(h);
        AtomicDouble accessX = new AtomicDouble();
        AtomicDouble accessZ = new AtomicDouble();
        NoiseProvider np = (x1, z1) -> {
            double d = Math.max(Math.abs(x1), Math.abs(z1));
            if (d > rad.get()) {
                rad.set(d);
            }
            return 0;
        };
        getNoise(method, 0, 0, h, np);
        return rad.get();
    }

    public static double getNoise(InterpolationMethod method, double x, double z, double h, NoiseProvider noise) {
        if (usesSampleCache(method)) {
            NoiseSampleCache2D cache = NOISE_SAMPLE_CACHE_2D.get();
            if (!cache.isInUse()) {
                cache.beginUse();
                try {
                    return dispatch(method, x, z, h, (x1, z1) -> cache.getOrSample(x1 - x, z1 - z, x1, z1, noise));
                } finally {
                    cache.endUse();
                }
            }
            // Nested interpolation on this thread (image maps, interpolated noise styles):
            // run unmemoized. Sharing the outer table would clear it mid-flight and serve
            // this provider's samples to the outer pass. Providers are pure, so the
            // unmemoized result is bit-identical.
        }
        return dispatch(method, x, z, h, noise);
    }

    private static double dispatch(InterpolationMethod method, double x, double z, double h, NoiseProvider n) {
        return switch (method) {
            case BILINEAR -> getBilinearNoise(x, z, h, n);
            case STARCAST_3 -> starcast(x, z, h, 3, n);
            case STARCAST_6 -> starcast(x, z, h, 6, n);
            case STARCAST_9 -> starcast(x, z, h, 9, n);
            case STARCAST_12 -> starcast(x, z, h, 12, n);
            case BILINEAR_STARCAST_3 ->
                    starcast(x, z, h, 3, (xx, zz) -> getBilinearNoise(xx, zz, h, n));
            case BILINEAR_STARCAST_6 ->
                    starcast(x, z, h, 6, (xx, zz) -> getBilinearNoise(xx, zz, h, n));
            case BILINEAR_STARCAST_9 ->
                    starcast(x, z, h, 9, (xx, zz) -> getBilinearNoise(xx, zz, h, n));
            case BILINEAR_STARCAST_12 ->
                    starcast(x, z, h, 12, (xx, zz) -> getBilinearNoise(xx, zz, h, n));
            case HERMITE_STARCAST_3 ->
                    starcast(x, z, h, 3, (xx, zz) -> getHermiteNoise(xx, zz, h, n, 0D, 0D));
            case HERMITE_STARCAST_6 ->
                    starcast(x, z, h, 6, (xx, zz) -> getHermiteNoise(xx, zz, h, n, 0D, 0D));
            case HERMITE_STARCAST_9 ->
                    starcast(x, z, h, 9, (xx, zz) -> getHermiteNoise(xx, zz, h, n, 0D, 0D));
            case HERMITE_STARCAST_12 ->
                    starcast(x, z, h, 12, (xx, zz) -> getHermiteNoise(xx, zz, h, n, 0D, 0D));
            case BILINEAR_BEZIER -> getBilinearBezierNoise(x, z, h, n);
            case BILINEAR_PARAMETRIC_2 -> getBilinearParametricNoise(x, z, h, n, 2);
            case BILINEAR_PARAMETRIC_4 -> getBilinearParametricNoise(x, z, h, n, 4);
            case BILINEAR_PARAMETRIC_1_5 -> getBilinearParametricNoise(x, z, h, n, 1.5);
            case BICUBIC -> getBicubicNoise(x, z, h, n);
            case HERMITE -> getHermiteNoise(x, z, h, n);
            case HERMITE_TENSE -> getHermiteNoise(x, z, h, n, 0.8D, 0D);
            case CATMULL_ROM_SPLINE -> getHermiteNoise(x, z, h, n, 0D, 0D);
            case HERMITE_LOOSE -> getHermiteNoise(x, z, h, n, 0D, 0D);
            case HERMITE_LOOSE_HALF_NEGATIVE_BIAS -> getHermiteNoise(x, z, h, n, 0D, -0.5D);
            case HERMITE_LOOSE_HALF_POSITIVE_BIAS -> getHermiteNoise(x, z, h, n, 0D, 0.5D);
            case HERMITE_LOOSE_FULL_NEGATIVE_BIAS -> getHermiteNoise(x, z, h, n, 0D, -1D);
            case HERMITE_LOOSE_FULL_POSITIVE_BIAS -> getHermiteNoise(x, z, h, n, 0D, 1D);
            case NONE -> n.noise(x, z);
        };
    }

    public static NoiseBounds getNoiseBounds(InterpolationMethod method, double x, double z, double h, NoiseBoundsProvider noise) {
        NoiseBoundsSampleCache2D cache = NOISE_BOUNDS_SAMPLE_CACHE_2D.get();
        if (cache.isInUse()) {
            // Nested bounds interpolation: a fresh table on the rare nested path, never the
            // outer pass's table — its entries belong to a different bound provider.
            cache = new NoiseBoundsSampleCache2D(64);
        }
        NoiseSampleCache2D scalarCache = usesSampleCache(method) ? NOISE_SAMPLE_CACHE_2D.get() : null;
        boolean ownsScalarCache = scalarCache != null && !scalarCache.isInUse();
        cache.beginUse();
        NoiseBoundsProvider previous = cache.bindProvider(noise);
        if (ownsScalarCache) {
            scalarCache.beginUse();
        }
        try {
            double min = dispatch(method, x, z, h, cache.minView());
            double max = dispatch(method, x, z, h, cache.replayMaxView());
            return new NoiseBounds(min, max);
        } finally {
            if (ownsScalarCache) {
                scalarCache.endUse();
            }
            cache.bindProvider(previous);
            cache.endUse();
        }
    }

    private static double[][] createStarcastOffsets() {
        double[][] offsets = new double[4][];
        for (int index = 0; index < offsets.length; index++) {
            int checks = (index + 1) * 3;
            double[] samples = new double[checks * 2];
            for (int sample = 0; sample < checks; sample++) {
                double angle = sample * (Math.PI * 2D / checks);
                double sin = Math.sin(angle);
                double cos = Math.cos(angle);
                samples[sample * 2] = cos - sin;
                samples[sample * 2 + 1] = sin + cos;
            }
            offsets[index] = samples;
        }
        return offsets;
    }

    private static double starcast(double x, double z, double radius, int checks, NoiseProvider noise) {
        double[] offsets = STARCAST_OFFSETS[checks / 3 - 1];
        double sum = 0D;
        for (int sample = 0; sample < offsets.length; sample += 2) {
            sum += noise.noise(x + radius * offsets[sample], z + radius * offsets[sample + 1]);
        }
        return sum / checks;
    }

    private static boolean usesSampleCache(InterpolationMethod method) {
        return switch (method) {
            case BILINEAR_STARCAST_3,
                 BILINEAR_STARCAST_6,
                 BILINEAR_STARCAST_9,
                 BILINEAR_STARCAST_12,
                 HERMITE_STARCAST_3,
                 HERMITE_STARCAST_6,
                 HERMITE_STARCAST_9,
                 HERMITE_STARCAST_12 -> true;
            default -> false;
        };
    }

    public static double rangeScale(double amin, double amax, double bmin, double bmax, double b) {
        return amin + ((amax - amin) * ((b - bmin) / (bmax - bmin)));
    }
}
