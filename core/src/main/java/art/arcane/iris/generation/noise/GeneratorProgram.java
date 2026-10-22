package art.arcane.iris.generation.noise;

import art.arcane.volmlib.util.interpolation.IrisInterpolation;

import java.util.Arrays;

public final class GeneratorProgram {
    private static final int SURFACE_GRID = 6;

    private final IrisGenerator generator;
    private final long superSeed;
    private final NoiseLayerProgram[] layers;
    private final double totalOpacity;
    private final NoiseLayerProgram cliffs;
    private final boolean multiplicitive;
    private final double opacity;
    private final double offsetX;
    private final double offsetZ;
    private final double zoom;
    private final double surfaceDetail;
    private final double cliffHeightMin;
    private final double cliffHeightMax;
    private final boolean cellCracks;

    GeneratorProgram(IrisGenerator generator, long superSeed, NoiseLayerProgram[] layers, double totalOpacity, NoiseLayerProgram cliffs) {
        this.generator = generator;
        this.superSeed = superSeed;
        this.layers = layers;
        this.totalOpacity = totalOpacity;
        this.cliffs = cliffs;
        this.multiplicitive = generator.isMultiplicitive();
        this.opacity = generator.getOpacity();
        this.offsetX = generator.getOffsetX();
        this.offsetZ = generator.getOffsetZ();
        this.zoom = generator.getZoom();
        this.surfaceDetail = generator.getSurfaceDetail();
        this.cliffHeightMin = generator.getCliffHeightMin();
        this.cliffHeightMax = generator.getCliffHeightMax();
        this.cellCracks = generator.getCellFractureHeight() != 0;
    }

    public IrisGenerator generator() {
        return generator;
    }

    public void heights(TerrainBatchScratch scratch, double[] xs, double[] zs, int count, double[] out) {
        if (count == 0) {
            return;
        }
        if (layers.length == 0) {
            Arrays.fill(out, 0, count, 0D);
            return;
        }
        if (surfaceDetail == 1D) {
            sample(scratch, xs, zs, count, out);
            return;
        }
        surfaceHeights(scratch, xs, zs, count, out);
    }

    public double height(TerrainBatchScratch scratch, double x, double z) {
        if (layers.length == 0) {
            return 0D;
        }
        if (surfaceDetail == 1D) {
            return sample(scratch, x, z);
        }
        double lowerX = Math.floor(x / SURFACE_GRID) * SURFACE_GRID;
        double lowerZ = Math.floor(z / SURFACE_GRID) * SURFACE_GRID;
        double dx = (x - lowerX) / SURFACE_GRID;
        double dz = (z - lowerZ) / SURFACE_GRID;
        IrisGenerator.SurfaceCacheStripe[] stripes = generator.surfaceStripes();
        double northWest = lattice(scratch, stripes, lowerX, lowerZ);
        if (dx == 0D && dz == 0D) {
            return northWest;
        }
        double north = dx == 0D ? northWest
                : IrisInterpolation.lerp(northWest, lattice(scratch, stripes, lowerX + SURFACE_GRID, lowerZ), dx);
        double smooth = north;
        if (dz != 0D) {
            double southWest = lattice(scratch, stripes, lowerX, lowerZ + SURFACE_GRID);
            double south = dx == 0D ? southWest
                    : IrisInterpolation.lerp(southWest, lattice(scratch, stripes, lowerX + SURFACE_GRID, lowerZ + SURFACE_GRID), dx);
            smooth = IrisInterpolation.lerp(north, south, dz);
        }
        return surfaceDetail == 0D ? smooth : smooth + (sample(scratch, x, z) - smooth) * surfaceDetail;
    }

    private double lattice(TerrainBatchScratch scratch, IrisGenerator.SurfaceCacheStripe[] stripes, double x, double z) {
        if (x != (int) x || z != (int) z) {
            return sample(scratch, x, z);
        }
        int mark = scratch.mark();
        boolean hit;
        double value;
        try {
            double[] cached = scratch.buffer(1);
            hit = IrisGenerator.cachedSurface(stripes, x, z, superSeed, cached, 0);
            value = cached[0];
        } finally {
            scratch.reset(mark);
        }
        if (hit) {
            return value;
        }
        double height = sample(scratch, x, z);
        IrisGenerator.storeSurface(stripes, x, z, superSeed, height);
        return height;
    }

    private double sample(TerrainBatchScratch scratch, double x, double z) {
        double sampleX = (x + offsetX) / zoom;
        double sampleZ = (z + offsetZ) / zoom;
        double value = multiplicitive ? 1D : 0D;
        for (NoiseLayerProgram layer : layers) {
            double noise = layer.sample(scratch, sampleX, sampleZ);
            value = multiplicitive ? value * noise : value + noise;
        }
        value = multiplicitive ? value * opacity : (value / totalOpacity) * opacity;
        if (Double.isNaN(value)) {
            value = 0D;
        }
        if (cliffs != null) {
            double height = IrisInterpolation.lerp(cliffHeightMin, cliffHeightMax, cliffs.sample(scratch, sampleX, sampleZ));
            value = (Math.round((value * 255D) / height) * height) / 255D;
        }
        return cellCracks ? generator.cell(x, z, value, 0D) : value;
    }

    private void surfaceHeights(TerrainBatchScratch scratch, double[] xs, double[] zs, int count, double[] out) {
        int mark = scratch.mark();
        int indexMark = scratch.indexMark();
        try {
            fillSurfaceHeights(scratch, xs, zs, count, out);
        } finally {
            scratch.resetIndices(indexMark);
            scratch.reset(mark);
        }
    }

    private void fillSurfaceHeights(TerrainBatchScratch scratch, double[] xs, double[] zs, int count, double[] out) {
        int[] northWest = scratch.indices(count);
        int[] northEast = scratch.indices(count);
        int[] southWest = scratch.indices(count);
        int[] southEast = scratch.indices(count);
        double[] fractionX = scratch.buffer(count);
        double[] fractionZ = scratch.buffer(count);
        scratch.clearLattice(count * 4);
        int detailed = 0;
        double previousX = Double.NaN;
        double previousZ = Double.NaN;
        int cellNorthWest = -1;
        int cellNorthEast = -1;
        int cellSouthWest = -1;
        int cellSouthEast = -1;
        for (int index = 0; index < count; index++) {
            double lowerX = Math.floor(xs[index] / SURFACE_GRID) * SURFACE_GRID;
            double lowerZ = Math.floor(zs[index] / SURFACE_GRID) * SURFACE_GRID;
            double dx = (xs[index] - lowerX) / SURFACE_GRID;
            double dz = (zs[index] - lowerZ) / SURFACE_GRID;
            fractionX[index] = dx;
            fractionZ[index] = dz;
            if (Double.doubleToRawLongBits(lowerX) != Double.doubleToRawLongBits(previousX)
                    || Double.doubleToRawLongBits(lowerZ) != Double.doubleToRawLongBits(previousZ)) {
                previousX = lowerX;
                previousZ = lowerZ;
                cellNorthWest = scratch.lattice(lowerX, lowerZ);
                cellNorthEast = -1;
                cellSouthWest = -1;
                cellSouthEast = -1;
            }
            if (dx != 0D && cellNorthEast < 0) {
                cellNorthEast = scratch.lattice(lowerX + SURFACE_GRID, lowerZ);
            }
            if (dz != 0D && cellSouthWest < 0) {
                cellSouthWest = scratch.lattice(lowerX, lowerZ + SURFACE_GRID);
            }
            if (dx != 0D && dz != 0D && cellSouthEast < 0) {
                cellSouthEast = scratch.lattice(lowerX + SURFACE_GRID, lowerZ + SURFACE_GRID);
            }
            northWest[index] = cellNorthWest;
            northEast[index] = dx == 0D ? -1 : cellNorthEast;
            southWest[index] = dz == 0D ? -1 : cellSouthWest;
            southEast[index] = dx == 0D || dz == 0D ? -1 : cellSouthEast;
            if (dx != 0D || dz != 0D) {
                detailed++;
            }
        }
        int nodes = scratch.latticeCount();
        double[] lattice = scratch.buffer(nodes);
        resolveLattice(scratch, nodes, lattice);
        double[] detail = null;
        int[] detailIndex = null;
        if (surfaceDetail != 0D && detailed > 0) {
            double[] detailX = scratch.buffer(detailed);
            double[] detailZ = scratch.buffer(detailed);
            detail = scratch.buffer(detailed);
            detailIndex = scratch.indices(count);
            int cursor = 0;
            for (int index = 0; index < count; index++) {
                if (fractionX[index] != 0D || fractionZ[index] != 0D) {
                    detailX[cursor] = xs[index];
                    detailZ[cursor] = zs[index];
                    detailIndex[index] = cursor++;
                }
            }
            sample(scratch, detailX, detailZ, detailed, detail);
        }
        for (int index = 0; index < count; index++) {
            double dx = fractionX[index];
            double dz = fractionZ[index];
            double nw = lattice[northWest[index]];
            if (dx == 0D && dz == 0D) {
                out[index] = nw;
                continue;
            }
            double north = dx == 0D ? nw : IrisInterpolation.lerp(nw, lattice[northEast[index]], dx);
            double smooth = north;
            if (dz != 0D) {
                double sw = lattice[southWest[index]];
                double south = dx == 0D ? sw : IrisInterpolation.lerp(sw, lattice[southEast[index]], dx);
                smooth = IrisInterpolation.lerp(north, south, dz);
            }
            out[index] = surfaceDetail == 0D ? smooth : smooth + (detail[detailIndex[index]] - smooth) * surfaceDetail;
        }
    }

    private void resolveLattice(TerrainBatchScratch scratch, int nodes, double[] lattice) {
        IrisGenerator.SurfaceCacheStripe[] stripes = generator.surfaceStripes();
        int indexMark = scratch.indexMark();
        int mark = scratch.mark();
        try {
            fillLattice(scratch, nodes, lattice, stripes);
        } finally {
            scratch.reset(mark);
            scratch.resetIndices(indexMark);
        }
    }

    private void fillLattice(TerrainBatchScratch scratch, int nodes, double[] lattice, IrisGenerator.SurfaceCacheStripe[] stripes) {
        int[] missing = scratch.indices(nodes);
        double[] missingX = scratch.buffer(nodes);
        double[] missingZ = scratch.buffer(nodes);
        int missingCount = 0;
        for (int node = 0; node < nodes; node++) {
            double x = scratch.latticeX(node);
            double z = scratch.latticeZ(node);
            if (x == (int) x && z == (int) z && IrisGenerator.cachedSurface(stripes, x, z, superSeed, lattice, node)) {
                continue;
            }
            missing[missingCount] = node;
            missingX[missingCount] = x;
            missingZ[missingCount++] = z;
        }
        if (missingCount > 0) {
            double[] sampled = scratch.buffer(missingCount);
            sample(scratch, missingX, missingZ, missingCount, sampled);
            for (int index = 0; index < missingCount; index++) {
                int node = missing[index];
                lattice[node] = sampled[index];
                if (missingX[index] == (int) missingX[index] && missingZ[index] == (int) missingZ[index]) {
                    IrisGenerator.storeSurface(stripes, missingX[index], missingZ[index], superSeed, sampled[index]);
                }
            }
        }
    }

    private void sample(TerrainBatchScratch scratch, double[] xs, double[] zs, int count, double[] out) {
        int mark = scratch.mark();
        try {
            sampleBatch(scratch, xs, zs, count, out);
        } finally {
            scratch.reset(mark);
        }
    }

    private void sampleBatch(TerrainBatchScratch scratch, double[] xs, double[] zs, int count, double[] out) {
        double[] sampleX = scratch.buffer(count);
        double[] sampleZ = scratch.buffer(count);
        double[] noise = scratch.buffer(count);
        for (int index = 0; index < count; index++) {
            sampleX[index] = (xs[index] + offsetX) / zoom;
            sampleZ[index] = (zs[index] + offsetZ) / zoom;
        }
        Arrays.fill(out, 0, count, multiplicitive ? 1D : 0D);
        for (NoiseLayerProgram layer : layers) {
            layer.evaluate(scratch, sampleX, sampleZ, count, noise);
            if (multiplicitive) {
                for (int index = 0; index < count; index++) {
                    out[index] *= noise[index];
                }
            } else {
                for (int index = 0; index < count; index++) {
                    out[index] += noise[index];
                }
            }
        }
        for (int index = 0; index < count; index++) {
            double value = multiplicitive ? out[index] * opacity : (out[index] / totalOpacity) * opacity;
            out[index] = Double.isNaN(value) ? 0D : value;
        }
        if (cliffs != null) {
            cliffs.evaluate(scratch, sampleX, sampleZ, count, noise);
            for (int index = 0; index < count; index++) {
                double height = IrisInterpolation.lerp(cliffHeightMin, cliffHeightMax, noise[index]);
                out[index] = (Math.round((out[index] * 255D) / height) * height) / 255D;
            }
        }
        if (cellCracks) {
            for (int index = 0; index < count; index++) {
                out[index] = generator.cell(xs[index], zs[index], out[index], 0D);
            }
        }
    }
}
