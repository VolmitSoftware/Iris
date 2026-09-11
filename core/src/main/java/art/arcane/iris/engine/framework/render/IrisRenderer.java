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

package art.arcane.iris.engine.framework.render;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.hydrology.HydrologyCandidateKind;
import art.arcane.iris.engine.hydrology.HydrologyDiagnosticCandidate;
import art.arcane.iris.engine.hydrology.HydrologyDiagnosticRenderSample;
import art.arcane.iris.engine.hydrology.HydrologyFeatureRef;
import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.engine.hydrology.HydrologyRenderSample;
import art.arcane.iris.engine.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisBiomeGeneratorLink;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.util.project.stream.ProceduralStream;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class IrisRenderer {
    private static final int BLUE = new Color(45, 91, 156).getRGB();
    private static final int YELLOW = new Color(211, 164, 67).getRGB();
    private static final int GREEN = new Color(78, 137, 83).getRGB();
    private static final int HEADWATER = new Color(99, 184, 230).getRGB();
    private static final int HEADWATER_DIRECTION = new Color(13, 38, 55).getRGB();
    private static final int RIVER_CHANNEL = new Color(48, 112, 190).getRGB();
    private static final int RIVER_RIFFLE = new Color(96, 166, 214).getRGB();
    private static final int RIVER_CASCADE = new Color(135, 204, 232).getRGB();
    private static final int SUBTERRANEAN_CHANNEL = new Color(102, 86, 196).getRGB();
    private static final int RIDGE_TUNNEL = new Color(120, 99, 174).getRGB();
    private static final int COASTAL_GROTTO = new Color(51, 174, 174).getRGB();
    private static final int INLAND_GROTTO = new Color(116, 76, 150).getRGB();
    private static final int RIVER_SINKHOLE = new Color(176, 146, 232).getRGB();
    private static final int RIVER_MOUTH = new Color(54, 164, 205).getRGB();
    private static final int RIVER_WATERFALL = new Color(190, 236, 255).getRGB();
    private static final int CONFIRMED_DEEP_LAVA = new Color(194, 50, 22).getRGB();
    private static final int STANDING_POOL = new Color(232, 120, 40).getRGB();
    private static final int PROJECTED_SOURCE = new Color(225, 75, 178).getRGB();
    private static final int PROJECTED_OUTLET = new Color(235, 145, 54).getRGB();
    private static final int PROJECTED_DEEP_FLUID = new Color(175, 70, 118).getRGB();
    private static final int NO_RIVER = new Color(28, 31, 38).getRGB();
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

        BufferedImage image = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        double step = size / resolution;
        if (studio && currentType == RenderType.HEIGHT) {
            renderHeightAtlas(pixels, resolution, sx, sz, step, renderer, cancelled);
            return image;
        }
        PixelShader shader = shader(currentType, step, studio);
        if (studio && currentType == RenderType.RIVER) {
            Arrays.fill(pixels, NO_RIVER);
            renderRiverAtlas(pixels, resolution, sx, sz, step, renderer.getComplex(), cancelled, false);
            return image;
        }
        if (studio && adaptiveStudioType(currentType)) {
            renderAdaptiveAtlas(pixels, resolution, sx, sz, step, shader, cancelled);
            if (currentType == RenderType.BIOME) {
                renderRiverAtlas(pixels, resolution, sx, sz, step, renderer.getComplex(), cancelled, true);
            }
            return image;
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

        return image;
    }

    public static int waterfallColor() {
        return RIVER_WATERFALL;
    }

    public static int surfaceWaterColor() {
        return RIVER_CHANNEL;
    }

    public static int subterraneanColor() {
        return SUBTERRANEAN_CHANNEL;
    }

    public static int confirmedDeepLavaColor() {
        return CONFIRMED_DEEP_LAVA;
    }

    public static int hydrologyFeatureColor(HydrologyFeatureType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case SURFACE_POOL -> RIVER_CHANNEL;
            case RIFFLE -> RIVER_RIFFLE;
            case CASCADE -> RIVER_CASCADE;
            case WATERFALL -> RIVER_WATERFALL;
            case SINKHOLE -> RIVER_SINKHOLE;
            case RIDGE_BORE -> RIDGE_TUNNEL;
            case UNDERGROUND_POOL, UNDERGROUND_DROP -> SUBTERRANEAN_CHANNEL;
            case COASTAL_GROTTO -> COASTAL_GROTTO;
            case INLAND_GROTTO -> INLAND_GROTTO;
            case MOUTH -> RIVER_MOUTH;
            case DEEP_POOL, DEEP_CHANNEL -> CONFIRMED_DEEP_LAVA;
            case STANDING_POOL -> STANDING_POOL;
        };
    }

    public static int headwaterColor() {
        return HEADWATER;
    }

    public static int headwaterDirectionColor() {
        return HEADWATER_DIRECTION;
    }

    public static int hydrologyDiagnosticColor(HydrologyCandidateKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case SOURCE, TRIBUTARY, REGIONAL_SOURCE, COASTAL_CHANNEL -> PROJECTED_SOURCE;
            case OUTLET -> PROJECTED_OUTLET;
            case DEEP_FLUID, POOL -> PROJECTED_DEEP_FLUID;
        };
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

    private PixelShader shader(RenderType currentType, double step, boolean studio) {
        IrisComplex complex = renderer.getComplex();
        return switch (currentType) {
            case BIOME, DECORATOR_LOAD, OBJECT_LOAD, LAYER_LOAD -> biomeShader(
                    studio ? complex.getBaseBiomeStream() : complex.getTrueBiomeStream(), currentType);
            case BIOME_LAND -> biomeShader(complex.getLandBiomeStream(), currentType);
            case BIOME_SEA -> biomeShader(complex.getSeaBiomeStream(), currentType);
            case REGION -> regionShader(complex, currentType);
            case CAVE_LAND -> biomeShader(complex.getCaveBiomeStream(), currentType);
            case HEIGHT -> heightShader(studio ? complex.getNaturalHeightStream() : complex.getHeightStream());
            case RIVER -> (double x, double z) -> riverColor(complex, x, z, step);
            case CONTINENT -> studio
                    ? continentShader(complex.getBaseBiomeStream())
                    : this::continentColor;
        };
    }

    private static boolean adaptiveStudioType(RenderType type) {
        return switch (type) {
            case BIOME, DECORATOR_LOAD, OBJECT_LOAD, LAYER_LOAD, BIOME_LAND, BIOME_SEA, REGION, CAVE_LAND,
                    CONTINENT -> true;
            case HEIGHT, RIVER -> false;
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
                complex.getNaturalHeightStream(),
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

    private static void renderRiverAtlas(
            int[] pixels,
            int resolution,
            double startX,
            double startZ,
            double step,
            IrisComplex complex,
            BooleanSupplier cancelled,
            boolean composite
    ) {
        IrisHydrologyRuntime runtime = complex.getHydrologyRuntime();
        if (runtime == null) {
            return;
        }
        Map<Long, HydrologyFeatureRef> headwaters = new LinkedHashMap<>();
        int maximumPixels = Math.max(1, Math.min(16, (int) Math.floor(64D / step)));
        int blockPixels = Integer.highestOneBit(maximumPixels);
        for (int pixelZ = 0; pixelZ < resolution; pixelZ += blockPixels) {
            int height = Math.min(blockPixels, resolution - pixelZ);
            for (int pixelX = 0; pixelX < resolution; pixelX += blockPixels) {
                renderRiverBlock(
                        pixels,
                        resolution,
                        startX,
                        startZ,
                        step,
                        runtime,
                        cancelled,
                        composite,
                        !composite,
                        headwaters,
                        pixelX,
                        pixelZ,
                        Math.min(blockPixels, resolution - pixelX),
                        height
                );
            }
        }
        renderHeadwaterDirections(pixels, resolution, startX, startZ, step, headwaters);
    }

    private static void renderRiverBlock(
            int[] pixels,
            int resolution,
            double startX,
            double startZ,
            double step,
            IrisHydrologyRuntime runtime,
            BooleanSupplier cancelled,
            boolean composite,
            boolean diagnostics,
            Map<Long, HydrologyFeatureRef> headwaters,
            int pixelX,
            int pixelZ,
            int width,
            int height
    ) {
        checkCancelled(cancelled);
        HydrologyRenderSample sample = runtime.sampleRenderFootprint(
                startX + pixelX * step,
                startZ + pixelZ * step,
                startX + (pixelX + width) * step,
                startZ + (pixelZ + height) * step
        );
        HydrologyDiagnosticRenderSample diagnosticSample = diagnostics
                ? runtime.sampleDiagnosticFootprint(
                startX + pixelX * step,
                startZ + pixelZ * step,
                startX + (pixelX + width) * step,
                startZ + (pixelZ + height) * step
                )
                : null;
        collectHeadwaters(sample, headwaters);
        if (!sample.present() && (diagnosticSample == null || !diagnosticSample.present())) {
            return;
        }
        if (width == 1 && height == 1) {
            int index = pixelZ * resolution + pixelX;
            int color = sample.present()
                    ? hydrologyColor(sample)
                    : hydrologyDiagnosticColor(diagnosticSample);
            pixels[index] = composite
                    ? blend(pixels[index], color, 0.72D)
                    : color;
            return;
        }
        int leftWidth = Math.max(1, width / 2);
        int rightWidth = width - leftWidth;
        int topHeight = Math.max(1, height / 2);
        int bottomHeight = height - topHeight;
        renderRiverBlock(pixels, resolution, startX, startZ, step, runtime, cancelled, composite, diagnostics,
                headwaters,
                pixelX, pixelZ, leftWidth, topHeight);
        if (rightWidth > 0) {
            renderRiverBlock(pixels, resolution, startX, startZ, step, runtime, cancelled, composite, diagnostics,
                    headwaters,
                    pixelX + leftWidth, pixelZ, rightWidth, topHeight);
        }
        if (bottomHeight > 0) {
            renderRiverBlock(pixels, resolution, startX, startZ, step, runtime, cancelled, composite, diagnostics,
                    headwaters,
                    pixelX, pixelZ + topHeight, leftWidth, bottomHeight);
            if (rightWidth > 0) {
                renderRiverBlock(pixels, resolution, startX, startZ, step, runtime, cancelled, composite, diagnostics,
                        headwaters,
                        pixelX + leftWidth, pixelZ + topHeight, rightWidth, bottomHeight);
            }
        }
    }

    private static void collectHeadwaters(
            HydrologyRenderSample sample,
            Map<Long, HydrologyFeatureRef> headwaters
    ) {
        for (HydrologyFeatureRef feature : sample.features()) {
            if (!feature.source()) {
                continue;
            }
            HydrologyFeatureRef existing = headwaters.putIfAbsent(feature.id(), feature);
            if (existing != null && !existing.equals(feature)) {
                throw new IllegalStateException("Hydrology headwater feature id collision in renderer footprint.");
            }
        }
    }

    private static void renderHeadwaterDirections(
            int[] pixels,
            int resolution,
            double startX,
            double startZ,
            double step,
            Map<Long, HydrologyFeatureRef> headwaters
    ) {
        if (resolution < 5 || headwaters.isEmpty()) {
            return;
        }
        ArrayList<HydrologyFeatureRef> ordered = new ArrayList<>(headwaters.values());
        ordered.sort(Comparator.comparingLong(HydrologyFeatureRef::id));
        for (HydrologyFeatureRef feature : ordered) {
            int pixelX = worldToPixel(feature.x(), startX, step, resolution);
            int pixelZ = worldToPixel(feature.z(), startZ, step, resolution);
            drawHeadwaterDirection(
                    pixels,
                    resolution,
                    pixelX,
                    pixelZ,
                    feature.flowDeltaX(),
                    feature.flowDeltaZ()
            );
        }
    }

    private static int worldToPixel(int coordinate, double start, double step, int resolution) {
        int pixel = (int) StrictMath.floor((coordinate + 0.5D - start) / step);
        int markerRadius = 2;
        return Math.max(markerRadius, Math.min(resolution - markerRadius - 1, pixel));
    }

    private static void drawHeadwaterDirection(
            int[] pixels,
            int resolution,
            int centerX,
            int centerZ,
            int flowX,
            int flowZ
    ) {
        int markerRadius = Math.min(2, (resolution - 1) / 2);
        for (int deltaZ = -markerRadius; deltaZ <= markerRadius; deltaZ++) {
            for (int deltaX = -markerRadius; deltaX <= markerRadius; deltaX++) {
                setPixel(pixels, resolution, centerX + deltaX, centerZ + deltaZ, HEADWATER);
            }
        }
        if (flowX == 0 && flowZ == 0) {
            setPixel(pixels, resolution, centerX, centerZ, HEADWATER_DIRECTION);
            return;
        }
        setPixel(pixels, resolution, centerX - flowX * 2, centerZ - flowZ * 2, HEADWATER_DIRECTION);
        setPixel(pixels, resolution, centerX - flowX, centerZ - flowZ, HEADWATER_DIRECTION);
        setPixel(pixels, resolution, centerX, centerZ, HEADWATER_DIRECTION);
        setPixel(pixels, resolution, centerX + flowX, centerZ + flowZ, HEADWATER_DIRECTION);
        int tipX = centerX + flowX * 2;
        int tipZ = centerZ + flowZ * 2;
        setPixel(pixels, resolution, tipX, tipZ, HEADWATER_DIRECTION);
        setPixel(
                pixels,
                resolution,
                tipX - flowX - flowZ,
                tipZ - flowZ + flowX,
                HEADWATER_DIRECTION
        );
        setPixel(
                pixels,
                resolution,
                tipX - flowX + flowZ,
                tipZ - flowZ - flowX,
                HEADWATER_DIRECTION
        );
    }

    private static void setPixel(int[] pixels, int resolution, int x, int z, int color) {
        if (x < 0 || x >= resolution || z < 0 || z >= resolution) {
            return;
        }
        pixels[z * resolution + x] = color;
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

    private int riverColor(IrisComplex complex, double x, double z, double step) {
        IrisHydrologyRuntime runtime = complex.getHydrologyRuntime();
        if (runtime == null) {
            return NO_RIVER;
        }
        double endX = x + step;
        double endZ = z + step;
        HydrologyRenderSample sample = runtime.sampleRenderFootprint(
                StrictMath.min(x, endX),
                StrictMath.min(z, endZ),
                StrictMath.max(x, endX),
                StrictMath.max(z, endZ)
        );
        if (sample.present()) {
            return hydrologyColor(sample);
        }
        return hydrologyDiagnosticColor(runtime.sampleDiagnosticFootprint(
                StrictMath.min(x, endX),
                StrictMath.min(z, endZ),
                StrictMath.max(x, endX),
                StrictMath.max(z, endZ)
        ));
    }

    static int hydrologyColor(HydrologyRenderSample sample) {
        Objects.requireNonNull(sample, "sample");
        HydrologyFeatureRef feature = sample.primaryFeature().orElse(null);
        if (feature == null) {
            return NO_RIVER;
        }
        if (feature.source()) {
            return HEADWATER;
        }
        return hydrologyFeatureColor(feature.type());
    }

    static int hydrologyDiagnosticColor(HydrologyDiagnosticRenderSample sample) {
        Objects.requireNonNull(sample, "sample");
        HydrologyDiagnosticCandidate candidate = sample.candidates().isEmpty()
                ? null
                : sample.candidates().getFirst();
        return candidate == null ? NO_RIVER : hydrologyDiagnosticColor(candidate.kind());
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
