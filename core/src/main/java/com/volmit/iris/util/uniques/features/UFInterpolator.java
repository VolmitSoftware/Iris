package com.volmit.iris.util.uniques.features;

import com.volmit.iris.util.function.NoiseProvider;
import com.volmit.iris.util.interpolation.InterpolationMethod;
import com.volmit.iris.util.interpolation.IrisInterpolation;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.uniques.UFeature;
import com.volmit.iris.util.uniques.UFeatureMeta;
import com.volmit.iris.util.uniques.UImage;

import java.awt.*;
import java.util.function.Consumer;

public class UFInterpolator implements UFeature {
    @Override
    public void render(UImage image, RNG rng, double t, Consumer<Double> progressor, UFeatureMeta meta) {
        UImage ref = image.copy();
        CNG rmod = generator("interpolator_radius", rng, 1, 33004, meta);

        NoiseProvider nHue = (x, y) -> {
            int ix = Math.abs(((int) x) % ref.getWidth());
            int iy = Math.abs(((int) y) % ref.getHeight());
            Color color = ref.get(ix, iy);
            float[] hsv = new float[3];
            Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getGreen(), hsv);
            return hsv[0];
        };
        NoiseProvider nSat = (x, y) -> {
            int ix = Math.abs(((int) x) % ref.getWidth());
            int iy = Math.abs(((int) y) % ref.getHeight());
            Color color = ref.get(ix, iy);
            float[] hsv = new float[3];
            Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getGreen(), hsv);
            return hsv[1];
        };
        NoiseProvider nBri = (x, y) -> {
            int ix = Math.abs(((int) x) % ref.getWidth());
            int iy = Math.abs(((int) y) % ref.getHeight());
            Color color = ref.get(ix, iy);
            float[] hsv = new float[3];
            Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getGreen(), hsv);
            return hsv[2];
        };
        InterpolationMethod method = interpolator(rng);
        int sizeMin = Math.min(image.getWidth(), image.getHeight());
        double radius = Math.max(4, rmod.fit(sizeMin / 256, sizeMin / 4, t * rng.d(0.03, 1.25), t * rng.d(0.01, 2.225)));
        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < image.getHeight(); j++) {
                image.set(i, j, Color.getHSBColor(
                        (float) Math.max(Math.min(1D, IrisInterpolation.getNoise(method, i, j, radius, nHue)), 0D),
                        (float) Math.max(Math.min(1D, IrisInterpolation.getNoise(method, i, j, radius, nSat)), 0D),
                        (float) Math.max(Math.min(1D, IrisInterpolation.getNoise(method, i, j, radius, nBri)), 0D)
                ));
            }

            progressor.accept(i / (double) image.getWidth());
        }
    }
}
