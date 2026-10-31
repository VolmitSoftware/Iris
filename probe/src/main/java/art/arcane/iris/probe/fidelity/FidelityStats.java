package art.arcane.iris.probe.fidelity;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

public final class FidelityStats {
    public static final int DEEP_LAVA_LINE = -54;
    private static final int CHUNK_WIDTH = 16;
    static final int BAND_HEIGHT = 16;
    private static final int MIN_ROOF_THICKNESS = 4;
    private static final double SQUARE_METRES_PER_SQUARE_KILOMETRE = 1_000_000.0D;

    private final int minY;
    private final int maxY;
    private final long[] heightHistogram;
    private final long[] subsurfaceVoxels;
    private final long[] caveVoxels;
    private final long[] voidVoxels;
    private final SortedMap<String, Long> biomeColumns;
    private long columns;
    private long heightColumns;
    private long fluidColumns;
    private long blockColumns;
    private long decorationColumns;
    private long riverColumns;
    private long deepVoxels;
    private long deepLavaVoxels;
    private long wallSolidVoxels;
    private long wallVoxels;
    private long oneBlockWallVoxels;

    public FidelityStats(int minY, int maxY) {
        if (maxY <= minY) {
            throw new IllegalArgumentException("Fidelity height range [" + minY + ", " + maxY + ") is empty");
        }
        this.minY = minY;
        this.maxY = maxY;
        int height = maxY - minY;
        int bands = (height + BAND_HEIGHT - 1) / BAND_HEIGHT;
        this.heightHistogram = new long[height];
        this.subsurfaceVoxels = new long[bands];
        this.caveVoxels = new long[bands];
        this.voidVoxels = new long[bands];
        this.biomeColumns = new TreeMap<>();
    }

    public void accept(FidelityChunk chunk, int stride) {
        Objects.requireNonNull(chunk, "chunk");
        requireStride(stride);
        for (int x = 0; x < CHUNK_WIDTH; x += stride) {
            for (int z = 0; z < CHUNK_WIDTH; z += stride) {
                acceptColumn(chunk, x, z);
            }
        }
    }

    public void accept(FidelityColumn column) {
        Objects.requireNonNull(column, "column");
        columns++;
        if (column.biome() != null) {
            biomeColumns.merge(column.biome(), 1L, Long::sum);
        }
        int terrainY = column.terrainY();
        if (terrainY >= maxY) {
            throw new IllegalArgumentException("Terrain Y " + terrainY + " is above the height range [" + minY + ", " + maxY + ")");
        }
        if (terrainY < minY) {
            return;
        }
        heightColumns++;
        heightHistogram[terrainY - minY]++;
        if (column.wet()) {
            fluidColumns++;
        }
    }

    public FidelityStats merge(FidelityStats other) {
        Objects.requireNonNull(other, "other");
        if (other.minY != minY || other.maxY != maxY) {
            throw new IllegalArgumentException("Cannot merge fidelity stats over [" + minY + ", " + maxY + ") with ["
                    + other.minY + ", " + other.maxY + ")");
        }
        FidelityStats merged = new FidelityStats(minY, maxY);
        merged.add(this);
        merged.add(other);
        return merged;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public long columns() {
        return columns;
    }

    public long heightColumns() {
        return heightColumns;
    }

    public long fluidColumns() {
        return fluidColumns;
    }

    public long blockColumns() {
        return blockColumns;
    }

    public long decorationColumns() {
        return decorationColumns;
    }

    public long riverColumns() {
        return riverColumns;
    }

    public long deepVoxels() {
        return deepVoxels;
    }

    public long deepLavaVoxels() {
        return deepLavaVoxels;
    }

    public long wallSolidVoxels() {
        return wallSolidVoxels;
    }

    public long wallVoxels() {
        return wallVoxels;
    }

    public long oneBlockWallVoxels() {
        return oneBlockWallVoxels;
    }

    public Map<String, Long> biomeColumns() {
        return Collections.unmodifiableSortedMap(biomeColumns);
    }

    public long[] heightHistogram() {
        return heightHistogram.clone();
    }

    public long[] subsurfaceVoxels() {
        return subsurfaceVoxels.clone();
    }

    public long[] caveVoxels() {
        return caveVoxels.clone();
    }

    public long[] voidVoxels() {
        return voidVoxels.clone();
    }

    public int bandCount() {
        return subsurfaceVoxels.length;
    }

    public int bandMinY(int band) {
        return minY + band * BAND_HEIGHT;
    }

    public Map<String, Double> biomeShares() {
        long resolved = 0L;
        for (long count : biomeColumns.values()) {
            resolved += count;
        }
        SortedMap<String, Double> shares = new TreeMap<>();
        for (Map.Entry<String, Long> entry : biomeColumns.entrySet()) {
            shares.put(entry.getKey(), entry.getValue() / (double) resolved);
        }
        return Collections.unmodifiableSortedMap(shares);
    }

    public double heightQuantile(double fraction) {
        if (heightColumns == 0L) {
            throw new IllegalStateException("No terrain columns were sampled");
        }
        if (!(fraction >= 0.0D && fraction <= 1.0D)) {
            throw new IllegalArgumentException("Quantile fraction must be within [0, 1], got " + fraction);
        }
        long rank = Math.max(1L, (long) Math.ceil(fraction * heightColumns));
        long seen = 0L;
        for (int index = 0; index < heightHistogram.length; index++) {
            seen += heightHistogram[index];
            if (seen >= rank) {
                return minY + index;
            }
        }
        return maxY - 1;
    }

    public double landFraction() {
        if (heightColumns == 0L) {
            throw new IllegalStateException("No terrain columns were sampled");
        }
        return (heightColumns - fluidColumns) / (double) heightColumns;
    }

    public double caveShare(int band) {
        return bandShare(caveVoxels, band);
    }

    public double voidShare(int band) {
        return bandShare(voidVoxels, band);
    }

    public double deepLavaShare() {
        if (deepVoxels == 0L) {
            throw new IllegalStateException("No subsurface voxels were sampled below Y " + DEEP_LAVA_LINE);
        }
        return deepLavaVoxels / (double) deepVoxels;
    }

    public double wallShare() {
        if (wallSolidVoxels == 0L) {
            throw new IllegalStateException("No solid subsurface voxels reported cave walls");
        }
        return wallVoxels / (double) wallSolidVoxels;
    }

    public double oneBlockWallShare() {
        if (wallSolidVoxels == 0L) {
            throw new IllegalStateException("No solid subsurface voxels reported cave walls");
        }
        return oneBlockWallVoxels / (double) wallSolidVoxels;
    }

    public double decorationPerSquareKilometre() {
        if (blockColumns == 0L) {
            throw new IllegalStateException("No sampled column reported block ids");
        }
        return decorationColumns / (double) blockColumns * SQUARE_METRES_PER_SQUARE_KILOMETRE;
    }

    static void requireStride(int stride) {
        if (stride < 1 || stride > CHUNK_WIDTH) {
            throw new IllegalArgumentException("Column stride must be within [1, " + CHUNK_WIDTH + "], got " + stride);
        }
    }

    private void acceptColumn(FidelityChunk chunk, int x, int z) {
        columns++;
        String biome = chunk.surfaceBiome(x, z);
        if (biome != null) {
            biomeColumns.merge(biome, 1L, Long::sum);
        }
        if (chunk.river(x, z)) {
            riverColumns++;
        }
        int surfaceY = chunk.surfaceY(x, z);
        if (surfaceY >= maxY) {
            throw new IllegalArgumentException("Surface Y " + surfaceY + " at column " + x + "," + z
                    + " is above the height range [" + minY + ", " + maxY + ")");
        }
        if (surfaceY < minY) {
            return;
        }
        int terrainY = surfaceY;
        String top = chunk.block(x, surfaceY, z);
        if (top != null) {
            blockColumns++;
            if (FidelityBlocks.decoration(top)) {
                decorationColumns++;
                terrainY = terrainBelow(chunk, x, z, surfaceY);
            }
        }
        if (terrainY < minY) {
            return;
        }
        heightColumns++;
        heightHistogram[terrainY - minY]++;
        if (terrainY + 1 < maxY && chunk.fluid(x, terrainY + 1, z)) {
            fluidColumns++;
        }
        int caveCeiling = caveCeiling(chunk, x, z, terrainY);
        boolean walls = chunk.wallsKnown() && interior(x, z);
        for (int y = minY; y < caveCeiling; y++) {
            int band = (y - minY) / BAND_HEIGHT;
            subsurfaceVoxels[band]++;
            boolean open = chunk.open(x, y, z);
            if (open) {
                if (chunk.terrainMass(x, y, z)) {
                    caveVoxels[band]++;
                } else {
                    voidVoxels[band]++;
                }
            }
            if (walls && !open && !chunk.fluid(x, y, z)) {
                wallSolidVoxels++;
                if (chunk.wall(x, y, z)) {
                    wallVoxels++;
                }
                if (CaveWalls.besideCave(chunk, x, y, z)) {
                    oneBlockWallVoxels++;
                }
            }
            if (y < DEEP_LAVA_LINE) {
                deepVoxels++;
                if (chunk.lava(x, y, z)) {
                    deepLavaVoxels++;
                }
            }
        }
    }

    private double bandShare(long[] voxels, int band) {
        if (subsurfaceVoxels[band] == 0L) {
            throw new IllegalStateException("No subsurface voxels were sampled in the band starting at Y " + bandMinY(band));
        }
        return voxels[band] / (double) subsurfaceVoxels[band];
    }

    private static boolean interior(int x, int z) {
        return x > 0 && x < CHUNK_WIDTH - 1 && z > 0 && z < CHUNK_WIDTH - 1;
    }

    private int terrainBelow(FidelityChunk chunk, int x, int z, int surfaceY) {
        int y = surfaceY - 1;
        while (y >= minY && aboveTerrain(chunk, x, y, z)) {
            y--;
        }
        return y;
    }

    private int caveCeiling(FidelityChunk chunk, int x, int z, int terrainY) {
        int top = terrainY;
        while (top >= minY) {
            int bottom = top;
            while (bottom >= minY && top - bottom < MIN_ROOF_THICKNESS && !aboveTerrain(chunk, x, bottom, z)) {
                bottom--;
            }
            if (top - bottom >= MIN_ROOF_THICKNESS || bottom < minY || !decoration(chunk, x, bottom, z)) {
                return top;
            }
            top = bottom;
            while (top >= minY && aboveTerrain(chunk, x, top, z)) {
                top--;
            }
        }
        return top;
    }

    private static boolean decoration(FidelityChunk chunk, int x, int y, int z) {
        String id = chunk.block(x, y, z);
        return id != null && FidelityBlocks.decoration(id);
    }

    private static boolean aboveTerrain(FidelityChunk chunk, int x, int y, int z) {
        if (chunk.open(x, y, z) || chunk.fluid(x, y, z)) {
            return true;
        }
        String id = chunk.block(x, y, z);
        return id != null && FidelityBlocks.decoration(id);
    }

    private void add(FidelityStats other) {
        columns += other.columns;
        heightColumns += other.heightColumns;
        fluidColumns += other.fluidColumns;
        blockColumns += other.blockColumns;
        decorationColumns += other.decorationColumns;
        riverColumns += other.riverColumns;
        deepVoxels += other.deepVoxels;
        deepLavaVoxels += other.deepLavaVoxels;
        wallSolidVoxels += other.wallSolidVoxels;
        wallVoxels += other.wallVoxels;
        oneBlockWallVoxels += other.oneBlockWallVoxels;
        for (int index = 0; index < heightHistogram.length; index++) {
            heightHistogram[index] += other.heightHistogram[index];
        }
        for (int band = 0; band < subsurfaceVoxels.length; band++) {
            subsurfaceVoxels[band] += other.subsurfaceVoxels[band];
            caveVoxels[band] += other.caveVoxels[band];
            voidVoxels[band] += other.voidVoxels[band];
        }
        for (Map.Entry<String, Long> entry : other.biomeColumns.entrySet()) {
            biomeColumns.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
    }
}
