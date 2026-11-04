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

package art.arcane.iris.studio.render;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.world.history.GenerationSemanticQueries;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeGeneratorLink;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.stream.ProceduralStream;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
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
    private static final int WATER_STREAM = new Color(96, 166, 214).getRGB();
    private static final int WATER_RIVER = new Color(48, 112, 190).getRGB();
    private static final int WATER_MAJOR_RIVER = new Color(28, 72, 160).getRGB();
    private static final int WATER_CASCADE = new Color(135, 204, 232).getRGB();
    private static final int WATER_WATERFALL = new Color(190, 236, 255).getRGB();
    private static final int WATER_MOUTH = new Color(54, 164, 205).getRGB();
    private static final int WATER_POND = new Color(64, 140, 170).getRGB();
    private static final int WATER_LAKE = new Color(40, 96, 140).getRGB();
    private static final int WATER_DAM = new Color(150, 120, 84).getRGB();
    private static final int WATER_SEA_INLET = new Color(51, 174, 174).getRGB();
    private static final int WATER_GROTTO = new Color(150, 80, 200).getRGB();
    private static final int WATER_CAVE_POOL = new Color(102, 86, 196).getRGB();
    private static final int NO_RIVER = new Color(28, 31, 38).getRGB();
    private static final double RIVER_OVERLAY = 0.72D;
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

    public WaterTile prepareStudioWater(double startX, double startZ, double step, int resolution) {
        if (!Double.isFinite(startX) || !Double.isFinite(startZ) || !Double.isFinite(step) || step <= 0D || resolution < 1) {
            throw new IllegalArgumentException("Water tile coordinates, scale and resolution must be valid");
        }
        return new WaterTile(startX, startZ, step, resolution);
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
        PixelShader shader = shader(currentType);
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

    public static List<HydrologyFeatureType> mapWaterTypes() {
        return WaterMap.DRAWN;
    }

    public static int hydrologyFeatureColor(HydrologyFeatureType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case STREAM -> WATER_STREAM;
            case RIVER -> WATER_RIVER;
            case MAJOR_RIVER -> WATER_MAJOR_RIVER;
            case CASCADE -> WATER_CASCADE;
            case WATERFALL -> WATER_WATERFALL;
            case MOUTH -> WATER_MOUTH;
            case POND -> WATER_POND;
            case LAKE -> WATER_LAKE;
            case DAM -> WATER_DAM;
            case SEA_INLET -> WATER_SEA_INLET;
            case GROTTO -> WATER_GROTTO;
            case CAVE_POOL -> WATER_CAVE_POOL;
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

    private PixelShader shader(RenderType currentType) {
        IrisComplex complex = renderer.getComplex();
        return switch (currentType) {
            case BIOME, DECORATOR_LOAD, OBJECT_LOAD, LAYER_LOAD -> biomeShader(complex.getTrueBiomeStream(), currentType);
            case BIOME_LAND -> biomeShader(complex.getLandBiomeStream(), currentType);
            case BIOME_SEA -> biomeShader(complex.getSeaBiomeStream(), currentType);
            case REGION -> regionShader(complex, currentType);
            case CAVE_LAND -> biomeShader(complex.getCaveBiomeStream(), currentType);
            case HEIGHT -> heightShader(complex.getHeightStream());
            case CONTINENT -> this::continentColor;
        };
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

    public final class WaterTile {
        private final double startX;
        private final double startZ;
        private final double step;
        private final int resolution;
        private final WaterMap map;
        private boolean complete;
        private long poolRevision;

        private WaterTile(double startX, double startZ, double step, int resolution) {
            this.startX = startX;
            this.startZ = startZ;
            this.step = step;
            this.resolution = resolution;
            HydrologyRuntime runtime = renderer.getComplex().getHydrologyRuntime();
            this.map = runtime == null || !runtime.enabled() || step > WaterMap.MAX_STEP
                    ? null : new WaterMap(runtime, resolution, startX, startZ, step);
            complete = map == null;
        }

        public int[] render(int[] base, boolean composite, BooleanSupplier cancelled) {
            Objects.requireNonNull(cancelled, "cancelled");
            checkCancelled(cancelled);
            if (composite && (base == null || base.length != resolution * resolution)) {
                throw new IllegalArgumentException("Water overlay requires matching base pixels");
            }
            if (map != null) {
                draw(cancelled);
            }
            int[] colors = composite ? base.clone() : new int[resolution * resolution];
            if (!composite) {
                Arrays.fill(colors, NO_RIVER & 0xFFFFFF);
            }
            if (map != null) {
                for (int z = 0; z < resolution; z++) {
                    checkCancelled(cancelled);
                    for (int x = 0; x < resolution; x++) {
                        HydrologyFeatureType type = map.at(x, z);
                        if (type != null) {
                            int index = z * resolution + x;
                            int color = hydrologyFeatureColor(type);
                            colors[index] = (composite ? blend(colors[index], color, RIVER_OVERLAY) : color) & 0xFFFFFF;
                        }
                    }
                }
            }
            if (!complete) {
                ProvisionalSampling.mark();
            }
            return colors;
        }

        public boolean complete() {
            return complete;
        }

        public long bytes() {
            return map == null ? 0L : map.bytes();
        }

        private void draw(BooleanSupplier cancelled) {
            long revision = GenerationSemanticQueries.recordedRiverRevision(renderer);
            boolean advanced = !complete;
            if (advanced) {
                map.advance(cancelled);
                complete = !map.provisional();
            }
            if (!advanced && revision == poolRevision) {
                return;
            }
            map.drawCavePools(GenerationSemanticQueries.recordedCavePools(renderer,
                    Math.floorDiv((int) Math.floor(startX), 16), Math.floorDiv((int) Math.floor(startZ), 16),
                    Math.floorDiv((int) Math.ceil(startX + resolution * step) - 1, 16),
                    Math.floorDiv((int) Math.ceil(startZ + resolution * step) - 1, 16)));
            poolRevision = revision;
        }
    }

    @FunctionalInterface
    private interface PixelShader {
        int color(double x, double z);
    }
}
