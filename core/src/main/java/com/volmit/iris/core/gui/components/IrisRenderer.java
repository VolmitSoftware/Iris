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

package com.volmit.iris.core.gui.components;

import com.volmit.iris.core.gui.GuiHost;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomeGeneratorLink;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.stream.ProceduralStream;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class IrisRenderer {
    private static final int BLUE = new Color(45, 91, 156).getRGB();
    private static final int YELLOW = new Color(211, 164, 67).getRGB();
    private static final int GREEN = new Color(78, 137, 83).getRGB();
    private static final int DEEP_WATER = new Color(20, 48, 92).getRGB();
    private static final int SHALLOW_WATER = new Color(50, 112, 154).getRGB();
    private static final int LOWLAND = new Color(78, 128, 76).getRGB();
    private static final int HIGHLAND = new Color(151, 139, 92).getRGB();
    private static final int ROCK = new Color(126, 119, 112).getRGB();
    private static final int SNOW = new Color(226, 230, 232).getRGB();

    private final Engine renderer;

    public IrisRenderer(Engine renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public BufferedImage render(double sx, double sz, double size, int resolution, RenderType currentType) {
        return render(sx, sz, size, resolution, currentType, () -> false);
    }

    public BufferedImage render(
            double sx,
            double sz,
            double size,
            int resolution,
            RenderType currentType,
            BooleanSupplier cancelled
    ) {
        return render(sx, sz, size, resolution, currentType, cancelled, false);
    }

    public BufferedImage renderStudio(
            double sx,
            double sz,
            double size,
            int resolution,
            RenderType currentType,
            BooleanSupplier cancelled
    ) {
        return render(sx, sz, size, resolution, currentType, cancelled, true);
    }

    private BufferedImage render(
            double sx,
            double sz,
            double size,
            int resolution,
            RenderType currentType,
            BooleanSupplier cancelled,
            boolean studio
    ) {
        if (!Double.isFinite(sx) || !Double.isFinite(sz) || !Double.isFinite(size) || size <= 0D) {
            throw new IllegalArgumentException("Vision render coordinates and size must be finite");
        }
        if (resolution < 1) {
            throw new IllegalArgumentException("Vision render resolution must be positive");
        }
        Objects.requireNonNull(currentType, "currentType");
        Objects.requireNonNull(cancelled, "cancelled");
        checkCancelled(cancelled);

        int[] pixels = renderPixels(sx, sz, size, resolution, currentType, cancelled, studio);
        return GuiHost.uncachedImage(pixels, resolution, resolution);
    }

    private int[] renderPixels(
            double sx,
            double sz,
            double size,
            int resolution,
            RenderType currentType,
            BooleanSupplier cancelled,
            boolean studio
    ) {
        int[] pixels = new int[resolution * resolution];
        double step = size / resolution;
        if (studio && currentType == RenderType.HEIGHT) {
            renderHeightAtlas(pixels, resolution, sx, sz, step, renderer, cancelled);
            return pixels;
        }
        PixelShader shader = shader(currentType, studio);
        if (studio && adaptiveStudioType(currentType)) {
            renderAdaptiveAtlas(pixels, resolution, sx, sz, step, shader, cancelled);
            return pixels;
        }
        int groupSize = sampleGroup(step, resolution);

        for (int groupZ = 0; groupZ < resolution; groupZ += groupSize) {
            checkCancelled(cancelled);
            int maximumZ = Math.min(resolution, groupZ + groupSize);
            for (int groupX = 0; groupX < resolution; groupX += groupSize) {
                checkCancelled(cancelled);
                int maximumX = Math.min(resolution, groupX + groupSize);
                for (int pixelZ = groupZ; pixelZ < maximumZ; pixelZ++) {
                    double z = sz + step * pixelZ;
                    int row = pixelZ * resolution;
                    for (int pixelX = groupX; pixelX < maximumX; pixelX++) {
                        checkCancelled(cancelled);
                        double x = sx + step * pixelX;
                        pixels[row + pixelX] = shader.color(x, z);
                    }
                }
            }
        }

        return pixels;
    }

    public static int heightColor(double height, double maximumHeight, double fluidHeight) {
        double boundedMaximum = Math.max(1D, maximumHeight);
        double boundedHeight = clamp(height, 0D, boundedMaximum);
        double boundedFluid = clamp(fluidHeight, 0D, boundedMaximum);
        if (boundedHeight <= boundedFluid && boundedFluid > 0D) {
            return blend(DEEP_WATER, SHALLOW_WATER, boundedHeight / boundedFluid);
        }

        double landRange = Math.max(1D, boundedMaximum - boundedFluid);
        double land = clamp((boundedHeight - boundedFluid) / landRange, 0D, 1D);
        if (land < 0.45D) {
            return blend(LOWLAND, HIGHLAND, land / 0.45D);
        }
        if (land < 0.78D) {
            return blend(HIGHLAND, ROCK, (land - 0.45D) / 0.33D);
        }
        return blend(ROCK, SNOW, (land - 0.78D) / 0.22D);
    }

    static int sampleGroup(double step, int resolution) {
        double absoluteStep = Math.abs(step);
        if (!Double.isFinite(absoluteStep) || absoluteStep <= 0D) {
            return 1;
        }
        return Math.max(1, Math.min(resolution, (int) Math.floor(16D / absoluteStep)));
    }

    private PixelShader shader(RenderType currentType, boolean studio) {
        IrisComplex complex = renderer.getComplex();
        return switch (currentType) {
            case BIOME, DECORATOR_LOAD, OBJECT_LOAD, LAYER_LOAD -> biomeShader(
                    studio ? complex.getBaseBiomeStream() : complex.getTrueBiomeStream(), currentType);
            case BIOME_LAND -> biomeShader(complex.getLandBiomeStream(), currentType);
            case BIOME_SEA -> biomeShader(complex.getSeaBiomeStream(), currentType);
            case REGION -> regionShader(complex, currentType);
            case CAVE_LAND -> biomeShader(complex.getCaveBiomeStream(), currentType);
            case HEIGHT -> heightShader(complex.getHeightStream());
            case CONTINENT -> studio
                    ? continentShader(complex.getBaseBiomeStream())
                    : this::continentColor;
        };
    }

    private static boolean adaptiveStudioType(RenderType type) {
        return switch (type) {
            case BIOME, DECORATOR_LOAD, OBJECT_LOAD, LAYER_LOAD, BIOME_LAND, BIOME_SEA, REGION, CAVE_LAND,
                    CONTINENT -> true;
            case HEIGHT -> false;
        };
    }

    private static void renderAdaptiveAtlas(
            int[] pixels,
            int resolution,
            double startX,
            double startZ,
            double step,
            PixelShader shader,
            BooleanSupplier cancelled
    ) {
        int maximumPixels = Math.max(1, Math.min(16, (int) Math.floor(64D / step)));
        int blockPixels = Integer.highestOneBit(maximumPixels);
        AdaptiveSampler sampler = new AdaptiveSampler(pixels, resolution, startX, startZ, step, shader, cancelled);
        for (int pixelZ = 0; pixelZ < resolution; pixelZ += blockPixels) {
            int height = Math.min(blockPixels, resolution - pixelZ);
            for (int pixelX = 0; pixelX < resolution; pixelX += blockPixels) {
                sampler.render(pixelX, pixelZ, Math.min(blockPixels, resolution - pixelX), height);
            }
        }
    }

    private static void renderHeightAtlas(
            int[] pixels,
            int resolution,
            double startX,
            double startZ,
            double step,
            Engine engine,
            BooleanSupplier cancelled
    ) {
        IrisComplex complex = engine.getComplex();
        IrisDimension dimension = engine.getDimension();
        double fluidHeight = dimension == null ? 0D : dimension.getFluidHeight();
        int maximumPixels = Math.max(1, Math.min(16, (int) Math.floor(64D / step)));
        int blockPixels = Integer.highestOneBit(maximumPixels);
        HeightSampler sampler = new HeightSampler(
                pixels,
                resolution,
                startX,
                startZ,
                step,
                complex.getHeightStream(),
                engine.getHeight(),
                fluidHeight,
                cancelled
        );
        for (int pixelZ = 0; pixelZ < resolution; pixelZ += blockPixels) {
            int height = Math.min(blockPixels, resolution - pixelZ);
            for (int pixelX = 0; pixelX < resolution; pixelX += blockPixels) {
                sampler.render(pixelX, pixelZ, Math.min(blockPixels, resolution - pixelX), height);
            }
        }
    }

    private PixelShader biomeShader(ProceduralStream<IrisBiome> stream, RenderType currentType) {
        IdentityHashMap<IrisBiome, Integer> colors = new IdentityHashMap<>();
        return (double x, double z) -> {
            IrisBiome biome = stream.get(x, z);
            Integer color = colors.get(biome);
            if (color == null) {
                color = biome.getColor(renderer, currentType).getRGB();
                colors.put(biome, color);
            }
            return color;
        };
    }

    private PixelShader regionShader(IrisComplex complex, RenderType currentType) {
        ProceduralStream<IrisRegion> stream = complex.getRegionStream();
        IdentityHashMap<IrisRegion, Integer> colors = new IdentityHashMap<>();
        return (double x, double z) -> {
            IrisRegion region = stream.get(x, z);
            Integer color = colors.get(region);
            if (color == null) {
                color = region.getColor(complex, currentType).getRGB();
                colors.put(region, color);
            }
            return color;
        };
    }

    private PixelShader heightShader(ProceduralStream<Double> stream) {
        double maximumHeight = renderer.getHeight();
        IrisDimension dimension = renderer.getDimension();
        double fluidHeight = dimension == null ? 0D : dimension.getFluidHeight();
        return (double x, double z) -> heightColor(stream.getDouble(x, z), maximumHeight, fluidHeight);
    }

    private int continentColor(double x, double z) {
        IrisBiome biome = renderer.getBiome(
                (int) Math.round(x),
                renderer.getMaxHeight() - 1,
                (int) Math.round(z)
        );
        return continentColor(biome);
    }

    private PixelShader continentShader(ProceduralStream<IrisBiome> stream) {
        IdentityHashMap<IrisBiome, Integer> colors = new IdentityHashMap<>();
        return (double x, double z) -> {
            IrisBiome biome = stream.get(x, z);
            Integer color = colors.get(biome);
            if (color == null) {
                color = continentColor(biome);
                colors.put(biome, color);
            }
            return color;
        };
    }

    private int continentColor(IrisBiome biome) {
        if (biome == null) {
            return GREEN;
        }
        List<IrisBiomeGeneratorLink> generators = biome.getGenerators();
        if (generators.isEmpty()) {
            return GREEN;
        }
        IrisBiomeGeneratorLink generator = generators.get(0);
        if (generator.getMax() <= 0D) {
            return BLUE;
        }
        if (generator.getMin() < 0D) {
            return YELLOW;
        }
        return GREEN;
    }

    private static int blend(int first, int second, double progress) {
        double bounded = clamp(progress, 0D, 1D);
        int red = (int) Math.round(((first >> 16) & 0xFF) * (1D - bounded) + ((second >> 16) & 0xFF) * bounded);
        int green = (int) Math.round(((first >> 8) & 0xFF) * (1D - bounded) + ((second >> 8) & 0xFF) * bounded);
        int blue = (int) Math.round((first & 0xFF) * (1D - bounded) + (second & 0xFF) * bounded);
        return (red << 16) | (green << 8) | blue;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Vision render cancelled");
        }
    }

    @FunctionalInterface
    private interface PixelShader {
        int color(double x, double z);
    }

    private static final class AdaptiveSampler {
        private final int[] pixels;
        private final boolean[] sampled;
        private final int resolution;
        private final double startX;
        private final double startZ;
        private final double step;
        private final PixelShader shader;
        private final BooleanSupplier cancelled;

        private AdaptiveSampler(
                int[] pixels,
                int resolution,
                double startX,
                double startZ,
                double step,
                PixelShader shader,
                BooleanSupplier cancelled
        ) {
            this.pixels = pixels;
            this.sampled = new boolean[pixels.length];
            this.resolution = resolution;
            this.startX = startX;
            this.startZ = startZ;
            this.step = step;
            this.shader = shader;
            this.cancelled = cancelled;
        }

        private void render(int pixelX, int pixelZ, int width, int height) {
            checkCancelled(cancelled);
            if (width == 1 && height == 1) {
                sample(pixelX, pixelZ);
                return;
            }
            int maximumX = pixelX + width - 1;
            int maximumZ = pixelZ + height - 1;
            int centerX = pixelX + width / 2;
            int centerZ = pixelZ + height / 2;
            int color = sample(pixelX, pixelZ);
            if (sample(maximumX, pixelZ) == color
                    && sample(pixelX, maximumZ) == color
                    && sample(maximumX, maximumZ) == color
                    && sample(centerX, centerZ) == color) {
                fill(pixelX, pixelZ, width, height, color);
                return;
            }
            int leftWidth = Math.max(1, width / 2);
            int rightWidth = width - leftWidth;
            int topHeight = Math.max(1, height / 2);
            int bottomHeight = height - topHeight;
            render(pixelX, pixelZ, leftWidth, topHeight);
            if (rightWidth > 0) {
                render(pixelX + leftWidth, pixelZ, rightWidth, topHeight);
            }
            if (bottomHeight > 0) {
                render(pixelX, pixelZ + topHeight, leftWidth, bottomHeight);
                if (rightWidth > 0) {
                    render(pixelX + leftWidth, pixelZ + topHeight, rightWidth, bottomHeight);
                }
            }
        }

        private int sample(int pixelX, int pixelZ) {
            int index = pixelZ * resolution + pixelX;
            if (!sampled[index]) {
                pixels[index] = shader.color(startX + pixelX * step, startZ + pixelZ * step);
                sampled[index] = true;
            }
            return pixels[index];
        }

        private void fill(int pixelX, int pixelZ, int width, int height, int color) {
            for (int row = pixelZ; row < pixelZ + height; row++) {
                int start = row * resolution + pixelX;
                Arrays.fill(pixels, start, start + width, color);
                Arrays.fill(sampled, start, start + width, true);
            }
        }
    }

    private static final class HeightSampler {
        private static final double MAXIMUM_INTERPOLATION_ERROR = 1.5D;

        private final int[] pixels;
        private final double[] heights;
        private final boolean[] sampled;
        private final int resolution;
        private final double startX;
        private final double startZ;
        private final double step;
        private final ProceduralStream<Double> stream;
        private final double maximumHeight;
        private final double fluidHeight;
        private final BooleanSupplier cancelled;

        private HeightSampler(
                int[] pixels,
                int resolution,
                double startX,
                double startZ,
                double step,
                ProceduralStream<Double> stream,
                double maximumHeight,
                double fluidHeight,
                BooleanSupplier cancelled
        ) {
            this.pixels = pixels;
            this.heights = new double[pixels.length];
            this.sampled = new boolean[pixels.length];
            this.resolution = resolution;
            this.startX = startX;
            this.startZ = startZ;
            this.step = step;
            this.stream = stream;
            this.maximumHeight = maximumHeight;
            this.fluidHeight = fluidHeight;
            this.cancelled = cancelled;
        }

        private void render(int pixelX, int pixelZ, int width, int height) {
            checkCancelled(cancelled);
            if (width == 1 && height == 1) {
                pixels[pixelZ * resolution + pixelX] = heightColor(
                        sample(pixelX, pixelZ), maximumHeight, fluidHeight);
                return;
            }
            int maximumX = pixelX + width - 1;
            int maximumZ = pixelZ + height - 1;
            int centerX = pixelX + width / 2;
            int centerZ = pixelZ + height / 2;
            double topLeft = sample(pixelX, pixelZ);
            double topRight = sample(maximumX, pixelZ);
            double bottomLeft = sample(pixelX, maximumZ);
            double bottomRight = sample(maximumX, maximumZ);
            if (matchesPlane(pixelX, pixelZ, width, height, topLeft, topRight, bottomLeft, bottomRight,
                    centerX, pixelZ)
                    && matchesPlane(pixelX, pixelZ, width, height, topLeft, topRight, bottomLeft, bottomRight,
                    pixelX, centerZ)
                    && matchesPlane(pixelX, pixelZ, width, height, topLeft, topRight, bottomLeft, bottomRight,
                    maximumX, centerZ)
                    && matchesPlane(pixelX, pixelZ, width, height, topLeft, topRight, bottomLeft, bottomRight,
                    centerX, maximumZ)
                    && matchesPlane(pixelX, pixelZ, width, height, topLeft, topRight, bottomLeft, bottomRight,
                    centerX, centerZ)) {
                fillPlane(pixelX, pixelZ, width, height, topLeft, topRight, bottomLeft, bottomRight);
                return;
            }
            int leftWidth = Math.max(1, width / 2);
            int rightWidth = width - leftWidth;
            int topHeight = Math.max(1, height / 2);
            int bottomHeight = height - topHeight;
            render(pixelX, pixelZ, leftWidth, topHeight);
            if (rightWidth > 0) {
                render(pixelX + leftWidth, pixelZ, rightWidth, topHeight);
            }
            if (bottomHeight > 0) {
                render(pixelX, pixelZ + topHeight, leftWidth, bottomHeight);
                if (rightWidth > 0) {
                    render(pixelX + leftWidth, pixelZ + topHeight, rightWidth, bottomHeight);
                }
            }
        }

        private boolean matchesPlane(
                int pixelX,
                int pixelZ,
                int width,
                int height,
                double topLeft,
                double topRight,
                double bottomLeft,
                double bottomRight,
                int sampleX,
                int sampleZ
        ) {
            double predicted = interpolate(
                    pixelX,
                    pixelZ,
                    width,
                    height,
                    topLeft,
                    topRight,
                    bottomLeft,
                    bottomRight,
                    sampleX,
                    sampleZ
            );
            return Math.abs(sample(sampleX, sampleZ) - predicted) <= MAXIMUM_INTERPOLATION_ERROR;
        }

        private void fillPlane(
                int pixelX,
                int pixelZ,
                int width,
                int height,
                double topLeft,
                double topRight,
                double bottomLeft,
                double bottomRight
        ) {
            for (int row = pixelZ; row < pixelZ + height; row++) {
                int offset = row * resolution;
                for (int column = pixelX; column < pixelX + width; column++) {
                    double value = interpolate(
                            pixelX,
                            pixelZ,
                            width,
                            height,
                            topLeft,
                            topRight,
                            bottomLeft,
                            bottomRight,
                            column,
                            row
                    );
                    pixels[offset + column] = heightColor(value, maximumHeight, fluidHeight);
                }
            }
        }

        private double sample(int pixelX, int pixelZ) {
            int index = pixelZ * resolution + pixelX;
            if (!sampled[index]) {
                heights[index] = stream.getDouble(startX + pixelX * step, startZ + pixelZ * step);
                sampled[index] = true;
            }
            return heights[index];
        }

        private static double interpolate(
                int pixelX,
                int pixelZ,
                int width,
                int height,
                double topLeft,
                double topRight,
                double bottomLeft,
                double bottomRight,
                int sampleX,
                int sampleZ
        ) {
            double x = width <= 1 ? 0D : (sampleX - pixelX) / (double) (width - 1);
            double z = height <= 1 ? 0D : (sampleZ - pixelZ) / (double) (height - 1);
            double top = topLeft + (topRight - topLeft) * x;
            double bottom = bottomLeft + (bottomRight - bottomLeft) * x;
            return top + (bottom - top) * z;
        }
    }
}
