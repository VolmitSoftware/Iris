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

import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.InferredType;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomeGeneratorLink;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.stream.ProceduralStream;

import java.awt.Color;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Draws map tiles for one generation of the pack: a hotload needs a new renderer, which also starts new labels.
 */
public final class IrisRenderer {
    private static final int BLUE = rgb(45, 91, 156);
    private static final int YELLOW = rgb(211, 164, 67);
    private static final int GREEN = rgb(78, 137, 83);
    private static final int DEEP_WATER = rgb(20, 48, 92);
    private static final int SHALLOW_WATER = rgb(50, 112, 154);
    private static final int LOWLAND = rgb(78, 128, 76);
    private static final int HIGHLAND = rgb(151, 139, 92);
    private static final int ROCK = rgb(126, 119, 112);
    private static final int SNOW = rgb(226, 230, 232);
    private static final int UNKNOWN_SUBJECT = rgb(90, 92, 98);
    private static final int LARGEST_SAMPLE_BLOCK_PIXELS = 16;
    private static final double LARGEST_SAMPLE_BLOCK_BLOCKS = 64D;
    private static final double DEFAULT_DEPTH_BELOW_SEA_BLOCKS = 48D;
    private static final double DEFAULT_HEIGHT_ABOVE_SEA_BLOCKS = 160D;
    private static final int NOISE_LOW = rgb(5, 5, 5);
    private static final int NOISE_HIGH = rgb(255, 255, 255);
    private static final double DARKEST_PICK_SHADE = 0.3D;

    private final Engine engine;
    private final MapLabels labels = new MapLabels();
    private final ConcurrentHashMap<LabelColorKey, Integer> labelColors = new ConcurrentHashMap<>();

    public IrisRenderer(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public MapLabels labels() {
        return labels;
    }

    /**
     * Renders the square of {@code size} pixels whose top left pixel samples the given world position.
     *
     * @param withHeights also sample terrain heights for shading; the height view always has them
     */
    public MapTile renderTile(
            double startX,
            double startZ,
            double blocksPerPixel,
            int size,
            MapLayer layer,
            boolean withHeights,
            BooleanSupplier cancelled
    ) {
        if (!Double.isFinite(startX) || !Double.isFinite(startZ) || !Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0D) {
            throw new IllegalArgumentException("Map tile coordinates and scale must be finite");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Map tile size must be positive");
        }
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(cancelled, "cancelled");
        RenderType type = layer.type();
        checkCancelled(cancelled);

        TileGrid grid = new TileGrid(size + 2, startX - blocksPerPixel, startZ - blocksPerPixel, blocksPerPixel, cancelled);
        int[] identities = sampleIdentities(grid, layer);
        double[] heights = withHeights || type == RenderType.HEIGHT ? sampleHeights(grid) : null;
        short[] subjects = new short[identities.length];
        short[] regions = new short[identities.length];
        for (int index = 0; index < identities.length; index++) {
            subjects[index] = subjectOf(identities[index]);
            regions[index] = regionOf(identities[index]);
        }
        int[] colors = type == RenderType.HEIGHT ? null : tileColors(size, type, subjects);
        if (layer.display() != PickDisplay.PICKS) {
            drawPickNoise(colors, grid, size, layer);
        }
        MapLabels.Kind subjectKind = type == RenderType.REGION ? MapLabels.Kind.REGION : MapLabels.Kind.BIOME;
        return new MapTile(size, blocksPerPixel, labels, subjectKind, colors, defaultHeightRange(), subjects, regions, heights);
    }

    /**
     * The colour a biome or region has on the map in the given view, for legends.
     */
    public int legendColor(MapLabels.Label label, RenderType type) {
        RenderType colorType = type == RenderType.HEIGHT ? RenderType.BIOME : type;
        return labelColor(label.id(), colorType);
    }

    /**
     * Until the map has measured the heights on screen, land colours cover the band most terrain sits in.
     */
    public MapStyle.HeightRange defaultHeightRange() {
        double seaLevel = fluidHeight();
        return new MapStyle.HeightRange(Math.max(0D, seaLevel - DEFAULT_DEPTH_BELOW_SEA_BLOCKS), seaLevel,
                Math.min(engine.getHeight(), seaLevel + DEFAULT_HEIGHT_ABOVE_SEA_BLOCKS));
    }

    public static int heightColor(double height, MapStyle.HeightRange range) {
        double seaLevel = range.seaLevel();
        if (height <= seaLevel) {
            double waterDepthRange = Math.max(1D, seaLevel - range.lowest());
            return blend(DEEP_WATER, SHALLOW_WATER, (height - range.lowest()) / waterDepthRange);
        }

        double landRange = Math.max(1D, range.highest() - seaLevel);
        double land = clamp((height - seaLevel) / landRange, 0D, 1D);
        if (land < 0.45D) {
            return blend(LOWLAND, HIGHLAND, land / 0.45D);
        }
        if (land < 0.78D) {
            return blend(HIGHLAND, ROCK, (land - 0.45D) / 0.33D);
        }
        return blend(ROCK, SNOW, (land - 0.78D) / 0.22D);
    }

    private int[] sampleIdentities(TileGrid grid, MapLayer layer) {
        IdentitySampler sampler = new IdentitySampler(grid, identityShader(layer));
        sampler.renderAll(sampleBlockPixels(grid.step()));
        return sampler.identities;
    }

    private double[] sampleHeights(TileGrid grid) {
        return new HeightLattice(grid, engine.getComplex().getHeightStream()).interpolatedHeights();
    }

    // Large uniform squares are filled after a few samples; squares stay small enough to not skip tiny biomes.
    private static int sampleBlockPixels(double blocksPerPixel) {
        int largestPixels = (int) Math.floor(LARGEST_SAMPLE_BLOCK_BLOCKS / blocksPerPixel);
        return Integer.highestOneBit(Math.max(1, Math.min(LARGEST_SAMPLE_BLOCK_PIXELS, largestPixels)));
    }

    private IdentityShader identityShader(MapLayer layer) {
        IrisComplex complex = engine.getComplex();
        ProceduralStream<IrisRegion> regionStream = complex.getRegionStream();
        LabelLookup lookup = new LabelLookup(labels);
        if (layer.type() == RenderType.REGION) {
            return (double x, double z) -> {
                short region = lookup.idOf(regionStream.get(x, z));
                return identity(region, region);
            };
        }
        if (layer.type() == RenderType.REGION_PREVIEW) {
            return regionPreviewShader(complex, layer.previewRegionKey(), lookup);
        }
        ProceduralStream<IrisBiome> biomeStream = subjectBiomeStream(complex, layer.type());
        return (double x, double z) -> identity(lookup.idOf(biomeStream.get(x, z)), lookup.idOf(regionStream.get(x, z)));
    }

    // Every spot is treated as part of the previewed region, so the tile shows the biomes that region places there.
    private IdentityShader regionPreviewShader(IrisComplex complex, String regionKey, LabelLookup lookup) {
        IrisRegion region = engine.getData().getRegionLoader().load(regionKey);
        if (region == null) {
            return (double x, double z) -> identity(MapLabels.NONE, MapLabels.NONE);
        }
        short regionId = lookup.idOf(region);
        return (double x, double z) -> identity(lookup.idOf(complex.surfaceBiomeAsIfIn(region, x, z)), regionId);
    }

    private void drawPickNoise(int[] colors, TileGrid grid, int size, MapLayer layer) {
        PickNoise noise = pickNoise(grid, layer);
        for (int pixelZ = 0; pixelZ < size; pixelZ++) {
            checkCancelled(grid.cancelled());
            double worldZ = grid.worldZ(pixelZ + 1);
            for (int pixelX = 0; pixelX < size; pixelX++) {
                int index = pixelZ * size + pixelX;
                double value = noise.valueAt((pixelZ + 1) * grid.size() + pixelX + 1, grid.worldX(pixelX + 1), worldZ);
                colors[index] = layer.display() == PickDisplay.NOISE
                        ? noise.color(value)
                        : shade(colors[index], DARKEST_PICK_SHADE + (1D - DARKEST_PICK_SHADE) * noise.brightness(value));
            }
        }
    }

    // Regions and BIOME terrain biomes are picked by splitting a noise by rarity; HYBRID terrain biomes by a height.
    private PickNoise pickNoise(TileGrid grid, MapLayer layer) {
        IrisComplex complex = engine.getComplex();
        if (layer.type() == RenderType.REGION) {
            ProceduralStream<Double> regionNoise = complex.getRegionStyleStream();
            return new UnitNoise((gridIndex, x, z) -> regionNoise.get(x, z));
        }
        IrisRegion region = engine.getData().getRegionLoader().load(layer.previewRegionKey());
        if (region == null) {
            return new UnitNoise((gridIndex, x, z) -> 0D);
        }
        if (complex.isHybridTerrain()) {
            ProceduralStream<Double> placementHeight = complex.getPlacementHeightStream();
            return new PlacementHeightNoise((gridIndex, x, z) -> placementHeight.get(x, z), fluidHeight(), defaultHeightRange());
        }
        return new UnitNoise(biomePickNoise(complex, region, grid));
    }

    // Finding which biome list picked a spot is slow, so it is sampled in squares like the biomes; the noise is cheap.
    private static NoiseField biomePickNoise(IrisComplex complex, IrisRegion region, TileGrid grid) {
        IdentitySampler typeSampler = new IdentitySampler(grid, (x, z) -> complex.surfacePickAsIfIn(region, x, z).type().ordinal());
        typeSampler.renderAll(sampleBlockPixels(grid.step()));
        int[] typeByGridIndex = typeSampler.identities;
        InferredType[] types = InferredType.values();
        Map<InferredType, ProceduralStream<Double>> noiseByType = new EnumMap<>(InferredType.class);
        return (gridIndex, x, z) -> noiseByType
                .computeIfAbsent(types[typeByGridIndex[gridIndex]], type -> complex.biomePickNoiseStream(region, type))
                .get(x, z);
    }

    // Hybrid terrain decides land, coast and sea by height after the biomes are chosen, so the final biome tells.
    private static ProceduralStream<IrisBiome> subjectBiomeStream(IrisComplex complex, RenderType type) {
        return switch (type) {
            case BIOME_LAND -> complex.getLandBiomeStream();
            case BIOME_SEA -> complex.getSeaBiomeStream();
            case CAVE_LAND -> complex.getCaveBiomeStream();
            case CONTINENT -> complex.isHybridTerrain() ? complex.getTrueBiomeStream() : complex.getBaseBiomeStream();
            case BIOME, HEIGHT, REGION, REGION_PREVIEW -> complex.getBaseBiomeStream();
        };
    }

    private int[] tileColors(int size, RenderType type, short[] subjects) {
        int[] colors = new int[size * size];
        short previousSubject = MapLabels.NONE;
        int previousColor = UNKNOWN_SUBJECT;
        for (int pixelZ = 0; pixelZ < size; pixelZ++) {
            for (int pixelX = 0; pixelX < size; pixelX++) {
                int gridIndex = (pixelZ + 1) * (size + 2) + pixelX + 1;
                short subject = subjects[gridIndex];
                if (subject != previousSubject) {
                    previousSubject = subject;
                    previousColor = labelColor(subject, type);
                }
                colors[pixelZ * size + pixelX] = previousColor;
            }
        }
        return colors;
    }

    private double fluidHeight() {
        IrisDimension dimension = engine.getDimension();
        return dimension == null ? 0D : dimension.getFluidHeight();
    }

    private int labelColor(short id, RenderType type) {
        MapLabels.Label label = labels.label(id);
        if (label == null) {
            return UNKNOWN_SUBJECT;
        }
        return labelColors.computeIfAbsent(new LabelColorKey(type, id), key -> subjectColor(label.subject(), type));
    }

    private int subjectColor(Object subject, RenderType type) {
        if (type == RenderType.CONTINENT && subject instanceof IrisBiome biome) {
            return engine.getComplex().isHybridTerrain() ? continentColorByPlacement(biome) : continentColor(biome);
        }
        if (subject instanceof IrisRegion region) {
            return region.getColor(engine.getComplex(), type).getRGB() & 0xFFFFFF;
        }
        if (subject instanceof IrisBiome biome) {
            return biome.getColor(engine, type).getRGB() & 0xFFFFFF;
        }
        return UNKNOWN_SUBJECT;
    }

    private static int continentColor(IrisBiome biome) {
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

    private static int continentColorByPlacement(IrisBiome biome) {
        if (biome.isShore()) {
            return YELLOW;
        }
        return biome.isSea() ? BLUE : GREEN;
    }

    private static int identity(short subject, short region) {
        return (subject << 16) | (region & 0xFFFF);
    }

    private static short subjectOf(int identity) {
        return (short) (identity >> 16);
    }

    private static short regionOf(int identity) {
        return (short) identity;
    }

    private static int rgb(int red, int green, int blue) {
        return new Color(red, green, blue).getRGB() & 0xFFFFFF;
    }

    private static int shade(int color, double brightness) {
        int red = (int) Math.round(((color >> 16) & 0xFF) * brightness);
        int green = (int) Math.round(((color >> 8) & 0xFF) * brightness);
        int blue = (int) Math.round((color & 0xFF) * brightness);
        return (red << 16) | (green << 8) | blue;
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
            throw new CancellationException("Map tile render cancelled");
        }
    }

    @FunctionalInterface
    private interface IdentityShader {
        int identity(double x, double z);
    }

    private record LabelColorKey(RenderType type, short id) {
    }

    @FunctionalInterface
    private interface NoiseField {
        double at(int gridIndex, double x, double z);
    }

    /**
     * A noise that picks regions or biomes, drawn on its own or as how bright the picks are.
     */
    private interface PickNoise {
        double valueAt(int gridIndex, double x, double z);

        int color(double value);

        double brightness(double value);
    }

    /**
     * A noise whose values run from 0 to 1, split into one band per region or biome by their rarities.
     */
    private record UnitNoise(NoiseField field) implements PickNoise {
        @Override
        public double valueAt(int gridIndex, double x, double z) {
            return field.at(gridIndex, x, z);
        }

        @Override
        public int color(double value) {
            return blend(NOISE_LOW, NOISE_HIGH, value);
        }

        @Override
        public double brightness(double value) {
            return clamp(value, 0D, 1D);
        }
    }

    /**
     * The height above the fluid that HYBRID terrain picks biomes by, coloured like the height view.
     */
    private record PlacementHeightNoise(NoiseField field, double fluidHeight, MapStyle.HeightRange range) implements PickNoise {
        @Override
        public double valueAt(int gridIndex, double x, double z) {
            return field.at(gridIndex, x, z);
        }

        @Override
        public int color(double value) {
            return heightColor(fluidHeight + value, range);
        }

        @Override
        public double brightness(double value) {
            return clamp((fluidHeight + value - range.lowest()) / Math.max(1D, range.highest() - range.lowest()), 0D, 1D);
        }
    }

    /**
     * The square of world positions one tile samples, including the one pixel ring borrowed from its neighbours.
     */
    private record TileGrid(int size, double startX, double startZ, double step, BooleanSupplier cancelled) {
        double worldX(int gridX) {
            return startX + gridX * step;
        }

        double worldZ(int gridZ) {
            return startZ + gridZ * step;
        }
    }

    /**
     * Labels the biomes and regions one tile meets, asking the shared labels only for ones this tile has not seen yet.
     */
    private static final class LabelLookup {
        private final MapLabels labels;
        private final IdentityHashMap<Object, Short> seen = new IdentityHashMap<>();

        private LabelLookup(MapLabels labels) {
            this.labels = labels;
        }

        short idOf(IrisBiome biome) {
            if (biome == null) {
                return MapLabels.NONE;
            }
            return seen.computeIfAbsent(biome, subject -> labels.idOf(biome));
        }

        short idOf(IrisRegion region) {
            if (region == null) {
                return MapLabels.NONE;
            }
            return seen.computeIfAbsent(region, subject -> labels.idOf(region));
        }
    }

    /**
     * Samples the biome and region of every grid cell in squares, filling a square without sampling its inside when
     * its corners and centre agree, and splitting it into quarters otherwise.
     */
    private static final class IdentitySampler {
        private final TileGrid grid;
        private final IdentityShader shader;
        private final int[] identities;
        private final boolean[] sampled;

        private IdentitySampler(TileGrid grid, IdentityShader shader) {
            this.grid = grid;
            this.shader = shader;
            this.identities = new int[grid.size() * grid.size()];
            this.sampled = new boolean[identities.length];
        }

        void renderAll(int blockPixels) {
            int size = grid.size();
            for (int gridZ = 0; gridZ < size; gridZ += blockPixels) {
                int height = Math.min(blockPixels, size - gridZ);
                for (int gridX = 0; gridX < size; gridX += blockPixels) {
                    render(gridX, gridZ, Math.min(blockPixels, size - gridX), height);
                }
            }
        }

        private void render(int gridX, int gridZ, int width, int height) {
            checkCancelled(grid.cancelled());
            if (width == 1 && height == 1) {
                sample(gridX, gridZ);
                return;
            }
            if (fillIfUniform(gridX, gridZ, width, height)) {
                return;
            }
            int leftWidth = Math.max(1, width / 2);
            int rightWidth = width - leftWidth;
            int topHeight = Math.max(1, height / 2);
            int bottomHeight = height - topHeight;
            render(gridX, gridZ, leftWidth, topHeight);
            if (rightWidth > 0) {
                render(gridX + leftWidth, gridZ, rightWidth, topHeight);
            }
            if (bottomHeight > 0) {
                render(gridX, gridZ + topHeight, leftWidth, bottomHeight);
                if (rightWidth > 0) {
                    render(gridX + leftWidth, gridZ + topHeight, rightWidth, bottomHeight);
                }
            }
        }

        private boolean fillIfUniform(int gridX, int gridZ, int width, int height) {
            int maximumX = gridX + width - 1;
            int maximumZ = gridZ + height - 1;
            int identity = sample(gridX, gridZ);
            boolean uniform = sample(maximumX, gridZ) == identity
                    && sample(gridX, maximumZ) == identity
                    && sample(maximumX, maximumZ) == identity
                    && sample(gridX + width / 2, gridZ + height / 2) == identity;
            if (!uniform) {
                return false;
            }
            for (int row = gridZ; row <= maximumZ; row++) {
                int start = row * grid.size() + gridX;
                Arrays.fill(identities, start, start + width, identity);
                Arrays.fill(sampled, start, start + width, true);
            }
            return true;
        }

        private int sample(int gridX, int gridZ) {
            int index = gridZ * grid.size() + gridX;
            if (!sampled[index]) {
                identities[index] = shader.identity(grid.worldX(gridX), grid.worldZ(gridZ));
                sampled[index] = true;
            }
            return identities[index];
        }
    }

    /**
     * Terrain heights from samples on a coarse lattice, smoothly interpolated in between. Iris heights are costly to
     * sample, and shading turns the steps of cheaper shortcuts into visible creases, so a smooth curve through fewer
     * exact samples looks better and draws an order of magnitude faster than sampling every pixel. The lattice lines
     * up across tiles, so slopes continue seamlessly from one tile into the next.
     */
    private static final class HeightLattice {
        private static final int LATTICE_PIXELS = 4;
        private static final int LATTICE_MARGIN = 2;

        private final TileGrid grid;
        private final ProceduralStream<Double> stream;
        private final int pointsPerSide;
        private final double[] points;

        private HeightLattice(TileGrid grid, ProceduralStream<Double> stream) {
            this.grid = grid;
            this.stream = stream;
            this.pointsPerSide = (grid.size() - 2) / LATTICE_PIXELS + LATTICE_MARGIN * 2 + 1;
            this.points = new double[pointsPerSide * pointsPerSide];
        }

        double[] interpolatedHeights() {
            samplePoints();
            int size = grid.size();
            double[] heights = new double[size * size];
            for (int gridZ = 0; gridZ < size; gridZ++) {
                for (int gridX = 0; gridX < size; gridX++) {
                    heights[gridZ * size + gridX] = heightAt(gridX - 1, gridZ - 1);
                }
            }
            return heights;
        }

        // Lattice point 0 sits on the tile's first pixel; the margin covers the border ring and the curve's reach.
        private void samplePoints() {
            double latticeStep = grid.step() * LATTICE_PIXELS;
            double firstX = grid.worldX(1) - LATTICE_MARGIN * latticeStep;
            double firstZ = grid.worldZ(1) - LATTICE_MARGIN * latticeStep;
            for (int pointZ = 0; pointZ < pointsPerSide; pointZ++) {
                checkCancelled(grid.cancelled());
                for (int pointX = 0; pointX < pointsPerSide; pointX++) {
                    points[pointZ * pointsPerSide + pointX] = stream.getDouble(firstX + pointX * latticeStep,
                            firstZ + pointZ * latticeStep);
                }
            }
        }

        private double heightAt(int pixelX, int pixelZ) {
            int cellX = Math.floorDiv(pixelX, LATTICE_PIXELS);
            int cellZ = Math.floorDiv(pixelZ, LATTICE_PIXELS);
            double fractionX = (pixelX - cellX * LATTICE_PIXELS) / (double) LATTICE_PIXELS;
            double fractionZ = (pixelZ - cellZ * LATTICE_PIXELS) / (double) LATTICE_PIXELS;
            int baseX = cellX + LATTICE_MARGIN;
            int baseZ = cellZ + LATTICE_MARGIN;
            double[] rows = new double[4];
            for (int row = 0; row < 4; row++) {
                int offset = (baseZ - 1 + row) * pointsPerSide + baseX - 1;
                rows[row] = catmullRom(points[offset], points[offset + 1], points[offset + 2], points[offset + 3], fractionX);
            }
            return catmullRom(rows[0], rows[1], rows[2], rows[3], fractionZ);
        }

        private static double catmullRom(double before, double start, double end, double after, double fraction) {
            double squared = fraction * fraction;
            double cubed = squared * fraction;
            return 0.5D * (2D * start
                    + (end - before) * fraction
                    + (2D * before - 5D * start + 4D * end - after) * squared
                    + (3D * start - before - 3D * end + after) * cubed);
        }
    }
}
