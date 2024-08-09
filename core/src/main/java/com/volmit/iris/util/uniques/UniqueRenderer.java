/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.uniques;

import com.volmit.iris.engine.object.NoiseStyle;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.function.NoiseProvider;
import com.volmit.iris.util.interpolation.InterpolationMethod;
import com.volmit.iris.util.interpolation.IrisInterpolation;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import com.volmit.iris.util.stream.ProceduralStream;
import com.volmit.iris.util.uniques.features.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class UniqueRenderer {
    static final List<UFeature> backgrounds = List.of(new UFWarpedBackground());
    static final List<UFeature> interpolators = List.of(new UFInterpolator(), new UFNOOP());
    static final List<UFeature> features = List.of(new UFWarpedLines(), new UFWarpedDisc(), new UFWarpedDots(), new UFWarpedCircle());
    static UniqueRenderer renderer;
    private final String seed;
    private final ProceduralStream<RNG> spatialSeed;
    private final int width;
    private final int height;
    private final KMap<String, String> writing = new KMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    private final KList<NoiseStyle> sortedStyles = new KList<NoiseStyle>();
    private final KList<InterpolationMethod> sortedInterpolators = new KList<InterpolationMethod>();
    int cores = Runtime.getRuntime().availableProcessors();

    public UniqueRenderer(String seed, int width, int height) {
        renderer = this;
        computeNoiseStyles(3000, 2);
        computeInterpolationMethods(3000, 2);
        this.seed = seed;
        this.width = width;
        this.height = height;
        spatialSeed = NoiseStyle.FRACTAL_WATER.stream(new RNG(seed)).convert((d) -> new RNG(Math.round(seed.hashCode() + (d * 934321234D))));
        new Thread(() -> {
            while (true) {
                J.sleep(5000);

                if (!writing.isEmpty()) {
                    System.out.println(Form.repeat("\n", 60));
                    System.out.println(Form.memSize(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(), 2) + " of " + Form.memSize(Runtime.getRuntime().totalMemory(), 2));
                    KMap<String, String> c = writing.copy();

                    for (String i : writing.k().sort()) {
                        String prog = "";
                        String f = writing.get(i);

                        if (f.contains("%")) {
                            String v = f.split("\\Q%\\E")[0];
                            try {
                                prog = drawProgress(Double.valueOf(Integer.parseInt(v.substring(v.length() - 2))) / 100D, 30);
                            } catch (Throwable e) {
                                try {
                                    prog = drawProgress(Double.valueOf(Integer.parseInt(v.substring(v.length() - 1))) / 100D, 30);
                                } catch (Throwable ee) {
                                    try {
                                        prog = drawProgress(Double.valueOf(Integer.parseInt(v.substring(v.length() - 3))) / 100D, 30);
                                    } catch (Throwable eee) {

                                    }
                                }
                            }
                        }

                        System.out.println(prog + " " + i + " => " + f);
                    }
                }
            }
        }).start();
    }

    public UMeta renderFrameBuffer(long id, double t) {
        UMeta meta = new UMeta();
        meta.setId(id);
        meta.setTime(t);
        RNG rng = spatialSeed.get(id, id + ((id * id) % (id / 3D)));
        RNG rngbg = spatialSeed.get(id, -id + ((id * id) % (id / 4D)));
        BufferedImage buf = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        BufferedImage bufFG = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        UImage image = new UBufferedImage(buf);
        UImage imageFG = new UBufferedImage(bufFG);
        ChronoLatch cl = new ChronoLatch(250);
        UFeature background = rng.pick(backgrounds);
        UFeature interpolator = rng.pick(interpolators);
        UFeature foreground = rng.pick(features);
        UFeature foregroundInterpolator = rng.pick(interpolators);
        UFeatureMeta backgroundMeta = new UFeatureMeta();
        UFeatureMeta foregroundMeta = new UFeatureMeta();
        UFeatureMeta backgroundInterpolatorMeta = new UFeatureMeta();
        UFeatureMeta foregroundInterpolatorMeta = new UFeatureMeta();
        background.render(image, rngbg, t, (p) -> {
            if (cl.flip()) {
                writing.put("#" + id + ":" + t, Form.pc(p / 4D) + " [" + background.getClass().getSimpleName() + " " + Form.pc(p) + "]");
            }
        }, backgroundMeta);
        backgroundMeta.setFeature(background.getClass().getSimpleName());
        meta.registerFeature("background", backgroundMeta);
        interpolator.render(image, rng, t, (p) -> {
            if (cl.flip()) {
                writing.put("#" + id + ":" + t, Form.pc(0.25 + (p / 4d)) + " [" + interpolator.getClass().getSimpleName() + " " + Form.pc(p) + "]");
            }
        }, backgroundInterpolatorMeta);
        backgroundInterpolatorMeta.setFeature(interpolator.getClass().getSimpleName());
        meta.registerFeature("backgroundInterpolator", backgroundInterpolatorMeta);
        foreground.render(imageFG, rng, t, (p) -> {
            if (cl.flip()) {
                writing.put("#" + id + ":" + t, Form.pc(0.5 + (p / 4d)) + " [" + foreground.getClass().getSimpleName() + " " + Form.pc(p) + "]");
            }
        }, foregroundMeta);
        foregroundMeta.setFeature(foreground.getClass().getSimpleName());
        meta.registerFeature("foreground", foregroundMeta);
        overlay(imageFG, bufFG, image);
        foregroundInterpolator.render(image, rng, t, (p) -> {
            if (cl.flip()) {
                writing.put("#" + id + ":" + t, Form.pc(0.75 + (p / 4d)) + " [" + interpolator.getClass().getSimpleName() + " " + Form.pc(p) + "]");
            }
        }, foregroundInterpolatorMeta);
        foregroundInterpolatorMeta.setFeature(foregroundInterpolator.getClass().getSimpleName());
        meta.registerFeature("foregroundInterpolator", foregroundInterpolatorMeta);
        overlay(imageFG, bufFG, image);
        meta.setImage(buf);
        writing.remove("#" + id + ":" + t);
        return meta;
    }

    private void overlay(UImage layer, BufferedImage layerBuf, UImage onto) {
        for (int i = 0; i < onto.getWidth(); i++) {
            for (int j = 0; j < onto.getHeight(); j++) {
                if (layerBuf.getRGB(i, j) != 0) {
                    onto.set(i, j, layer.get(i, j));
                }
            }
        }
    }

    private String drawProgress(double progress, int len) {
        int max = len;
        int in = (int) Math.round(progress * max);
        max -= in;

        return "[" + Form.repeat("=", in) + Form.repeat(" ", max) + "]";
    }

    private void computeNoiseStyles(double time, double scope) {
        List<NoiseStyle> allowedStyles = new KList<>(NoiseStyle.values());
        allowedStyles.remove(NoiseStyle.FLAT);
        KMap<NoiseStyle, Integer> speeds = new KMap<>();
        double allocateMS = time;
        double maxTestDuration = allocateMS / allowedStyles.size();
        System.out.println("Running Noise Style Benchmark for " + Form.duration(allocateMS, 0) + ".");
        System.out.println("Benchmarking " + allowedStyles.size() + " + Noise Styles for " + Form.duration(maxTestDuration, 1) + " each.");
        System.out.println();

        for (NoiseStyle i : allowedStyles) {
            int score = 0;
            CNG cng = i.create(new RNG("renderspeedtest"));
            PrecisionStopwatch p = PrecisionStopwatch.start();
            double g = 0;
            while (p.getMilliseconds() < maxTestDuration) {
                cng.noise(g, -g * 2);
                g += 0.1;
                g *= 1.25;
                score++;
            }

            speeds.put(i, score);
        }

        for (NoiseStyle i : speeds.sortKNumber()) {
            System.out.println(Form.capitalizeWords(i.name().toLowerCase(Locale.ROOT).replaceAll("\\Q_\\E", " ")) + " => " + Form.f(speeds.get(i)));
        }
        System.out.println();
        int takeUpTo = (int) Math.max(1, scope * speeds.size());
        System.out.println("Choosing the fastest " + Form.pc(scope) + " styles (" + takeUpTo + ")");

        for (NoiseStyle i : speeds.sortKNumber().reverse()) {
            if (takeUpTo-- <= 0) {
                break;
            }

            sortedStyles.add(i);
            System.out.println("- " + Form.capitalizeWords(i.name().toLowerCase(Locale.ROOT).replaceAll("\\Q_\\E", " ")));
        }
    }

    private void computeInterpolationMethods(double time, double scope) {
        List<InterpolationMethod> allowedStyles = new KList<>(InterpolationMethod.values());
        allowedStyles.remove(InterpolationMethod.NONE);
        KMap<InterpolationMethod, Integer> speeds = new KMap<>();
        double allocateMS = time;
        double maxTestDuration = allocateMS / allowedStyles.size();
        System.out.println("Running Interpolation Method Benchmark for " + Form.duration(allocateMS, 0) + ".");
        System.out.println("Benchmarking " + allowedStyles.size() + " + Interpolation Methods for " + Form.duration(maxTestDuration, 1) + " each.");
        System.out.println();

        RNG r = new RNG("renderspeedtestinterpolation");
        CNG cng = NoiseStyle.SIMPLEX.create(r);
        NoiseProvider np = (x, z) -> cng.noise(x, z);

        for (InterpolationMethod i : allowedStyles) {
            int score = 0;

            PrecisionStopwatch p = PrecisionStopwatch.start();
            double g = 0;
            while (p.getMilliseconds() < maxTestDuration) {
                IrisInterpolation.getNoise(i, (int) g, (int) (-g * 2.225), r.d(4, 64), np);
                cng.noise(g, -g * 2);
                g += 1.1;
                g *= 1.25;
                score++;
            }

            speeds.put(i, score);
        }

        for (InterpolationMethod i : speeds.sortKNumber()) {
            System.out.println(Form.capitalizeWords(i.name().toLowerCase(Locale.ROOT).replaceAll("\\Q_\\E", " ")) + " => " + Form.f(speeds.get(i)));
        }
        System.out.println();
        int takeUpTo = (int) Math.max(1, scope * speeds.size());
        System.out.println("Choosing the fastest " + Form.pc(scope) + " interpolators (" + takeUpTo + ")");

        for (InterpolationMethod i : speeds.sortKNumber().reverse()) {
            if (takeUpTo-- <= 0) {
                break;
            }

            sortedInterpolators.add(i);
            System.out.println("- " + Form.capitalizeWords(i.name().toLowerCase(Locale.ROOT).replaceAll("\\Q_\\E", " ")));
        }
    }

    public void writeCollectionFrames(File folder, int fromId, int toId) {
        folder.mkdirs();
        BurstExecutor burst = new BurstExecutor(executor, Math.min(toId - fromId, 1000));
        burst.setMulticore(true);
        AtomicInteger ai = new AtomicInteger(0);
        int max = toId - fromId;

        for (int i = fromId; i <= toId; i++) {
            int ii = i;
            burst.queue(() -> {

                writing.put("!#[" + fromId + "-" + toId + "] Collection", ai.get() + " of " + max + " (" + Form.pc(ai.get() / (double) max, 0) + ")");
                writeFrame(new File(folder, ii + ".png"), ii, 0);
                ai.incrementAndGet();
                writing.put("!#[" + fromId + "-" + toId + "] Collection", ai.get() + " of " + max + " (" + Form.pc(ai.get() / (double) max, 0) + ")");
            });
        }

        burst.complete();
        writing.remove("!#[" + fromId + "-" + toId + "] Collection");
    }

    public void writeFrame(File destination, long id, double t) {
        try {
            renderFrameBuffer(id, t).export(destination);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void report(String s) {
        System.out.println(s);
    }

    public KList<NoiseStyle> getStyles() {
        return sortedStyles;
    }

    public List<InterpolationMethod> getInterpolators() {
        return sortedInterpolators;
    }
}
