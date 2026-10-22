package art.arcane.iris.generation.noise;

import art.arcane.volmlib.util.interpolation.IrisInterpolation;
import art.arcane.volmlib.util.noise.program.NoiseProgram;

import java.util.Arrays;

public final class NoiseLayerProgram {
    private final boolean enabled;
    private final double offsetY;
    private final NoiseProgram program;
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

    private NoiseLayerProgram(IrisNoiseGenerator layer, NoiseProgram program, NoiseLayerProgram[] children, double[] childOffsets) {
        this.enabled = program != null;
        this.offsetY = layer.getOffsetY();
        this.program = program;
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
        return new NoiseLayerProgram(layer, null, new NoiseLayerProgram[0], new double[0]);
    }

    static NoiseLayerProgram of(IrisNoiseGenerator layer, NoiseProgram program, NoiseLayerProgram[] children, double[] childOffsets) {
        return new NoiseLayerProgram(layer, program, children, childOffsets);
    }

    public void evaluate(TerrainBatchScratch scratch, double[] xs, double[] zs, int count, double[] out) {
        if (!enabled) {
            Arrays.fill(out, 0, count, offsetY);
            return;
        }
        int mark = scratch.mark();
        double[] sampleX = scratch.buffer(count);
        double[] sampleZ = scratch.buffer(count);
        if (children.length == 0) {
            for (int index = 0; index < count; index++) {
                sampleX[index] = (xs[index] / zoom) + offsetX;
                sampleZ[index] = (zs[index] / zoom) + offsetZ;
            }
        } else {
            System.arraycopy(xs, 0, sampleX, 0, count);
            System.arraycopy(zs, 0, sampleZ, 0, count);
            double[] forward = scratch.buffer(count);
            double[] reverse = scratch.buffer(count);
            for (int child = 0; child < children.length; child++) {
                children[child].evaluatePair(scratch, xs, zs, count, forward, reverse);
                double offset = childOffsets[child];
                for (int index = 0; index < count; index++) {
                    sampleX[index] += forward[index] - offset;
                    sampleZ[index] += reverse[index] - offset;
                }
            }
            for (int index = 0; index < count; index++) {
                sampleX[index] = (sampleX[index] / zoom) + offsetX;
                sampleZ[index] = (sampleZ[index] / zoom) + offsetZ;
            }
        }
        program.fill2D(scratch.noise(), sampleX, sampleZ, count, out);
        for (int index = 0; index < count; index++) {
            out[index] = post(out[index]);
        }
        scratch.reset(mark);
    }

    void evaluatePair(TerrainBatchScratch scratch, double[] xs, double[] zs, int count, double[] forward, double[] reverse) {
        if (!enabled) {
            Arrays.fill(forward, 0, count, offsetY);
            Arrays.fill(reverse, 0, count, offsetY);
            return;
        }
        int mark = scratch.mark();
        int pairs = count * 2;
        double[] sampleX = scratch.buffer(pairs);
        double[] sampleZ = scratch.buffer(pairs);
        System.arraycopy(xs, 0, sampleX, 0, count);
        System.arraycopy(zs, 0, sampleZ, 0, count);
        System.arraycopy(zs, 0, sampleX, count, count);
        System.arraycopy(xs, 0, sampleZ, count, count);
        if (children.length > 0) {
            double[] childForward = scratch.buffer(count);
            double[] childReverse = scratch.buffer(count);
            for (int child = 0; child < children.length; child++) {
                children[child].evaluatePair(scratch, xs, zs, count, childForward, childReverse);
                double offset = childOffsets[child];
                for (int index = 0; index < count; index++) {
                    sampleX[index] += childForward[index] - offset;
                    sampleZ[index] += childReverse[index] - offset;
                    sampleX[count + index] += childReverse[index] - offset;
                    sampleZ[count + index] += childForward[index] - offset;
                }
            }
        }
        for (int index = 0; index < pairs; index++) {
            sampleX[index] = (sampleX[index] / zoom) + offsetX;
            sampleZ[index] = (sampleZ[index] / zoom) + offsetZ;
        }
        double[] values = scratch.buffer(pairs);
        program.fill2D(scratch.noise(), sampleX, sampleZ, pairs, values);
        for (int index = 0; index < count; index++) {
            forward[index] = post(values[index]);
            reverse[index] = post(values[count + index]);
        }
        scratch.reset(mark);
    }

    public double sample(TerrainBatchScratch scratch, double x, double z) {
        if (!enabled) {
            return offsetY;
        }
        double sampleX = x;
        double sampleZ = z;
        if (children.length > 0) {
            int mark = scratch.mark();
            double[] pair = scratch.buffer(2);
            for (int child = 0; child < children.length; child++) {
                children[child].samplePair(scratch, x, z, pair);
                double offset = childOffsets[child];
                sampleX += pair[0] - offset;
                sampleZ += pair[1] - offset;
            }
            scratch.reset(mark);
        }
        return post(program.sample2D((sampleX / zoom) + offsetX, (sampleZ / zoom) + offsetZ));
    }

    private void samplePair(TerrainBatchScratch scratch, double x, double z, double[] out) {
        if (!enabled) {
            out[0] = offsetY;
            out[1] = offsetY;
            return;
        }
        double forwardX = x;
        double forwardZ = z;
        double reverseX = z;
        double reverseZ = x;
        if (children.length > 0) {
            int mark = scratch.mark();
            double[] pair = scratch.buffer(2);
            for (int child = 0; child < children.length; child++) {
                children[child].samplePair(scratch, x, z, pair);
                double offset = childOffsets[child];
                forwardX += pair[0] - offset;
                forwardZ += pair[1] - offset;
                reverseX += pair[1] - offset;
                reverseZ += pair[0] - offset;
            }
            scratch.reset(mark);
        }
        out[0] = post(program.sample2D((forwardX / zoom) + offsetX, (forwardZ / zoom) + offsetZ));
        out[1] = post(program.sample2D((reverseX / zoom) + offsetX, (reverseZ / zoom) + offsetZ));
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
