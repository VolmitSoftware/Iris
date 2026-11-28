package art.arcane.iris.generation.noise;

import art.arcane.volmlib.util.interpolation.IrisInterpolation;
import art.arcane.volmlib.util.noise.program.NoiseProgram;

import java.util.Arrays;

public final class NoiseLayerProgram {
    private final boolean enabled;
    private final double offsetY;
    private final NoiseProgram program;
    private final NoiseProgram zAxisProgram;
    private final double zoom;
    private final double opacity;
    private final double offsetX;
    private final double offsetZ;
    private final double exponent;
    private final boolean negative;
    private final boolean parametric;
    private final boolean bezier;
    private final boolean sinCentered;
    private final NoiseLayerProgram[] children;
    private final double[] childOffsets;

    private NoiseLayerProgram(IrisNoiseGenerator layer, NoiseProgram program, NoiseProgram zAxisProgram,
                              NoiseLayerProgram[] children, double[] childOffsets) {
        this.enabled = program != null;
        this.offsetY = layer.getOffsetY();
        this.program = program;
        this.zAxisProgram = zAxisProgram;
        this.zoom = layer.getZoom();
        this.opacity = layer.getOpacity();
        this.offsetX = layer.getOffsetX();
        this.offsetZ = layer.getOffsetZ();
        this.exponent = layer.getExponent();
        this.negative = layer.isNegative();
        this.parametric = layer.isParametric();
        this.bezier = layer.isBezier();
        this.sinCentered = layer.isSinCentered();
        this.children = children;
        this.childOffsets = childOffsets;
    }

    static NoiseLayerProgram disabled(IrisNoiseGenerator layer) {
        return new NoiseLayerProgram(layer, null, null, new NoiseLayerProgram[0], new double[0]);
    }

    /**
     * zAxisProgram is the layer's generator reseeded for the z axis; only a layer read as a fracture needs it, and the
     * root layer passes null.
     */
    static NoiseLayerProgram of(IrisNoiseGenerator layer, NoiseProgram program, NoiseProgram zAxisProgram,
                                NoiseLayerProgram[] children, double[] childOffsets) {
        return new NoiseLayerProgram(layer, program, zAxisProgram, children, childOffsets);
    }

    public void evaluate(TerrainBatchScratch scratch, double[] xs, double[] zs, int count, double[] out) {
        if (!enabled) {
            Arrays.fill(out, 0, count, offsetY);
            return;
        }
        int mark = scratch.mark();
        try {
            double[] sampleX = scratch.buffer(count);
            double[] sampleZ = scratch.buffer(count);
            warpedSampleCoordinates(scratch, xs, zs, count, sampleX, sampleZ);
            program.fill2D(scratch.noise(), sampleX, sampleZ, count, out);
            applyPost(out, count);
        } finally {
            scratch.reset(mark);
        }
    }

    /** Fills this layer read as a fracture: the x warp from its generator, the z warp from its z-axis generator. */
    void evaluateAxes(TerrainBatchScratch scratch, double[] xs, double[] zs, int count, double[] xWarp, double[] zWarp) {
        if (!enabled) {
            Arrays.fill(xWarp, 0, count, offsetY);
            Arrays.fill(zWarp, 0, count, offsetY);
            return;
        }
        int mark = scratch.mark();
        try {
            double[] sampleX = scratch.buffer(count);
            double[] sampleZ = scratch.buffer(count);
            warpedSampleCoordinates(scratch, xs, zs, count, sampleX, sampleZ);
            program.fill2D(scratch.noise(), sampleX, sampleZ, count, xWarp);
            zAxisProgram.fill2D(scratch.noise(), sampleX, sampleZ, count, zWarp);
            applyPost(xWarp, count);
            applyPost(zWarp, count);
        } finally {
            scratch.reset(mark);
        }
    }

    private void warpedSampleCoordinates(TerrainBatchScratch scratch, double[] xs, double[] zs, int count,
                                         double[] sampleX, double[] sampleZ) {
        System.arraycopy(xs, 0, sampleX, 0, count);
        System.arraycopy(zs, 0, sampleZ, 0, count);
        if (children.length > 0) {
            double[] childX = scratch.buffer(count);
            double[] childZ = scratch.buffer(count);
            for (int child = 0; child < children.length; child++) {
                children[child].evaluateAxes(scratch, xs, zs, count, childX, childZ);
                double offset = childOffsets[child];
                for (int index = 0; index < count; index++) {
                    sampleX[index] += childX[index] - offset;
                    sampleZ[index] += childZ[index] - offset;
                }
            }
        }
        for (int index = 0; index < count; index++) {
            sampleX[index] = (sampleX[index] / zoom) + offsetX;
            sampleZ[index] = (sampleZ[index] / zoom) + offsetZ;
        }
    }

    public double sample(TerrainBatchScratch scratch, double x, double z) {
        if (!enabled) {
            return offsetY;
        }
        int mark = scratch.mark();
        try {
            double[] sampleCoordinates = scratch.buffer(2);
            warpedSampleCoordinate(scratch, x, z, sampleCoordinates);
            return post(program.sample2D(sampleCoordinates[0], sampleCoordinates[1]));
        } finally {
            scratch.reset(mark);
        }
    }

    private void sampleAxes(TerrainBatchScratch scratch, double x, double z, double[] out) {
        if (!enabled) {
            out[0] = offsetY;
            out[1] = offsetY;
            return;
        }
        int mark = scratch.mark();
        try {
            double[] sampleCoordinates = scratch.buffer(2);
            warpedSampleCoordinate(scratch, x, z, sampleCoordinates);
            out[0] = post(program.sample2D(sampleCoordinates[0], sampleCoordinates[1]));
            out[1] = post(zAxisProgram.sample2D(sampleCoordinates[0], sampleCoordinates[1]));
        } finally {
            scratch.reset(mark);
        }
    }

    private void warpedSampleCoordinate(TerrainBatchScratch scratch, double x, double z, double[] out) {
        double sampleX = x;
        double sampleZ = z;
        if (children.length > 0) {
            double[] warp = scratch.buffer(2);
            for (int child = 0; child < children.length; child++) {
                children[child].sampleAxes(scratch, x, z, warp);
                double offset = childOffsets[child];
                sampleX += warp[0] - offset;
                sampleZ += warp[1] - offset;
            }
        }
        out[0] = (sampleX / zoom) + offsetX;
        out[1] = (sampleZ / zoom) + offsetZ;
    }

    private void applyPost(double[] values, int count) {
        for (int index = 0; index < count; index++) {
            values[index] = post(values[index]);
        }
    }

    private double post(double raw) {
        double n = raw * opacity;
        n = negative ? (-n + opacity) : n;
        n = (exponent != 1 ? n < 0 ? -Math.pow(-n, exponent) : Math.pow(n, exponent) : n) + offsetY;
        n = parametric ? IrisInterpolation.parametric(n, 2D) : n;
        n = bezier ? IrisInterpolation.bezier(n) : n;
        n = sinCentered ? IrisInterpolation.sinCenter(n) : n;
        return n;
    }
}
