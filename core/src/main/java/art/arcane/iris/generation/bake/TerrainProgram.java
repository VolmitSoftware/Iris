package art.arcane.iris.generation.bake;

import art.arcane.iris.generation.noise.GeneratorProgram;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.TerrainBatchScratch;
import art.arcane.volmlib.util.math.M;

import java.util.Arrays;

public final class TerrainProgram {
    private static final int SKIPPED = 0;
    private static final int CONSTANT = 1;
    private static final int ACTIVE = 2;

    private final IrisGenerator[][] generators;
    private final GeneratorProgram[][] programs;
    private final long heightSeed;

    private TerrainProgram(IrisGenerator[][] generators, GeneratorProgram[][] programs, long heightSeed) {
        this.generators = generators;
        this.programs = programs;
        this.heightSeed = heightSeed;
    }

    public static TerrainProgram compile(IrisGenerator[][] generators, long heightSeed) {
        GeneratorProgram[][] programs = new GeneratorProgram[generators.length][];
        for (int group = 0; group < generators.length; group++) {
            programs[group] = new GeneratorProgram[generators[group].length];
            for (int index = 0; index < generators[group].length; index++) {
                IrisGenerator generator = generators[group][index];
                programs[group][index] = generator.supportsBatch() ? generator.compileProgram(heightSeed) : null;
            }
        }
        return new TerrainProgram(generators, programs, heightSeed);
    }

    public int groups() {
        return generators.length;
    }

    public int groupSize(int group) {
        return generators[group].length;
    }

    public long accumulate(int group, TerrainBatchScratch scratch, double[] xs, double[] zs, int count,
                           double[] lows, double[] highs, double[] heights) {
        IrisGenerator[] members = generators[group];
        if (members.length == 0 || count == 0) {
            return 0L;
        }
        int mark = scratch.mark();
        int indexMark = scratch.indexMark();
        int[] modes = scratch.indices(count);
        int activeCount = 0;
        for (int index = 0; index < count; index++) {
            double low = lows[index];
            double high = highs[index];
            if (low == 0D && high == 0D) {
                modes[index] = SKIPPED;
                continue;
            }
            if (!Double.isFinite(low) || !Double.isFinite(high)) {
                throw new IllegalStateException("Terrain generator bounds were not finite at " + xs[index] + "," + zs[index]
                        + " (generator=" + members[0].getLoadKey() + ", bounds=" + low + ".." + high + ").");
            }
            if (low == high) {
                modes[index] = CONSTANT;
                double sum = 0D;
                for (int member = 0; member < members.length; member++) {
                    sum += low;
                }
                heights[index] += sum / members.length;
                continue;
            }
            modes[index] = ACTIVE;
            activeCount++;
        }
        if (activeCount == 0) {
            scratch.resetIndices(indexMark);
            return 0L;
        }
        double[] activeX = scratch.buffer(activeCount);
        double[] activeZ = scratch.buffer(activeCount);
        double[] activeLow = scratch.buffer(activeCount);
        double[] activeHigh = scratch.buffer(activeCount);
        int[] activeIndex = scratch.indices(activeCount);
        int cursor = 0;
        for (int index = 0; index < count; index++) {
            if (modes[index] == ACTIVE) {
                activeX[cursor] = xs[index];
                activeZ[cursor] = zs[index];
                activeLow[cursor] = lows[index];
                activeHigh[cursor] = highs[index];
                activeIndex[cursor++] = index;
            }
        }
        double[] sums = scratch.buffer(activeCount);
        double[] noise = scratch.buffer(activeCount);
        Arrays.fill(sums, 0, activeCount, 0D);
        GeneratorProgram[] compiled = programs[group];
        for (int member = 0; member < members.length; member++) {
            GeneratorProgram program = compiled[member];
            if (program != null) {
                program.heights(scratch, activeX, activeZ, activeCount, noise);
            } else {
                for (int index = 0; index < activeCount; index++) {
                    noise[index] = members[member].getHeight(activeX[index], activeZ[index], heightSeed);
                }
            }
            for (int index = 0; index < activeCount; index++) {
                if (!Double.isFinite(noise[index])) {
                    throw new IllegalStateException("Terrain generator noise was not finite at " + activeX[index] + "," + activeZ[index]
                            + " (generator=" + members[member].getLoadKey() + ", noise=" + noise[index]
                            + ", bounds=" + activeLow[index] + ".." + activeHigh[index] + ").");
                }
                sums[index] += M.lerp(activeLow[index], activeHigh[index], noise[index]);
            }
        }
        for (int index = 0; index < activeCount; index++) {
            heights[activeIndex[index]] += sums[index] / members.length;
        }
        scratch.reset(mark);
        scratch.resetIndices(indexMark);
        return (long) activeCount * members.length;
    }
}
