package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;

import java.util.Arrays;

final class ReachFitter {
    private static final float SPACING = 4f;
    private static final float MEANDER_AMPLITUDE = 8f;
    private static final double MEANDER_SLOPE = 0.2D;
    private static final float VALLEY_SNAP = 8f;
    private static final float MOVE_PENALTY = 0.25f * SPACING;
    private static final float MIN_ADVANCE = 0.25f * SPACING;
    private static final int[] MOVES = {0, -1, 1};
    private static final long SALT_MEANDER = 0x4d45414e444552L;

    private final HeightLattice fine;
    private final PondLookup ponds;

    ReachFitter(HeightLattice fine, PondLookup ponds) {
        this.fine = fine;
        this.ponds = ponds;
    }

    Fitted fit(DemGrid grid, int[] cells, float[] cellCatchment, HydrologyTier tier, long shapeSeed) {
        float halfWidth = tier.halfWidth(cellCatchment[cellCatchment.length - 1]);
        Line line = resample(grid, cells, cellCatchment);
        meander(line, shapeSeed);
        int reach = Math.max(2, (int) Math.ceil(Math.max(VALLEY_SNAP, halfWidth + 2f) / SPACING));
        Tangents tangents = tangents(line.xs(), line.zs());
        Reads reads = read(line, tangents, reach);
        Fitted fitted = place(line, tangents, reads, viterbi(reads, line.xs().length, reach), tier);
        lowestNearbyGround(fitted);
        return fitted;
    }

    private static Line resample(DemGrid grid, int[] cells, float[] cellCatchment) {
        int vertices = cells.length;
        float[] vx = new float[vertices];
        float[] vz = new float[vertices];
        double[] along = new double[vertices];
        for (int vertex = 0; vertex < vertices; vertex++) {
            vx[vertex] = grid.blockX(cells[vertex]);
            vz[vertex] = grid.blockZ(cells[vertex]);
            if (vertex > 0) {
                along[vertex] = along[vertex - 1] + Math.hypot(vx[vertex] - vx[vertex - 1], vz[vertex] - vz[vertex - 1]);
            }
        }
        double length = along[vertices - 1];
        int segments = Math.max(1, (int) Math.round(length / SPACING));
        Line line = new Line(new float[segments + 1], new float[segments + 1], new float[segments + 1], new float[segments + 1],
                new float[segments + 1], (float) length);
        int segment = 0;
        for (int point = 0; point <= segments; point++) {
            double s = point == segments ? length : length * point / segments;
            while (segment < vertices - 2 && along[segment + 1] < s) {
                segment++;
            }
            double span = along[segment + 1] - along[segment];
            float t = span > 0D ? (float) ((s - along[segment]) / span) : 0f;
            line.xs()[point] = lerp(vx[segment], vx[segment + 1], t);
            line.zs()[point] = lerp(vz[segment], vz[segment + 1], t);
            line.heights()[point] = lerp(grid.values()[cells[segment]], grid.values()[cells[segment + 1]], t);
            line.catchment()[point] = lerp(cellCatchment[segment], cellCatchment[segment + 1], t);
            line.distance()[point] = (float) s;
        }
        line.xs()[0] = vx[0];
        line.zs()[0] = vz[0];
        line.catchment()[0] = cellCatchment[0];
        line.xs()[segments] = vx[vertices - 1];
        line.zs()[segments] = vz[vertices - 1];
        line.catchment()[segments] = cellCatchment[vertices - 1];
        return line;
    }

    private static void meander(Line line, long shapeSeed) {
        int count = line.xs().length;
        if (count < 3) {
            return;
        }
        double[] phase = new double[3];
        for (int wave = 0; wave < phase.length; wave++) {
            phase[wave] = 2D * Math.PI * CellHash.unit(CellHash.mix(shapeSeed, SALT_MEANDER, wave, 0));
        }
        Tangents tangents = tangents(line.xs(), line.zs());
        float[] offsets = new float[count];
        for (int point = 1; point < count - 1; point++) {
            double s = line.distance()[point];
            double run = Math.max(1e-3D, line.distance()[point + 1] - line.distance()[point - 1]);
            double slope = Math.abs(line.heights()[point + 1] - line.heights()[point - 1]) / run;
            double damping = Math.clamp(1D - slope / MEANDER_SLOPE, 0D, 1D);
            double wave = 0.5D * StrictMath.sin(0.03D * s + phase[0]) + 0.3D * StrictMath.sin(0.07D * s + phase[1])
                    + 0.2D * StrictMath.sin(0.017D * s + phase[2]);
            offsets[point] = (float) (MEANDER_AMPLITUDE * StrictMath.sin(Math.PI * s / line.length()) * damping * wave);
        }
        for (int point = 1; point < count - 1; point++) {
            line.xs()[point] -= offsets[point] * tangents.z()[point];
            line.zs()[point] += offsets[point] * tangents.x()[point];
        }
    }

    private Reads read(Line line, Tangents tangents, int reach) {
        int span = 2 * reach + 1;
        int count = line.xs().length * span;
        int step = fine.cell();
        int[] nodeX = new int[count];
        int[] nodeZ = new int[count];
        for (int point = 0; point < line.xs().length; point++) {
            for (int k = 0; k < span; k++) {
                float offset = (k - reach) * SPACING;
                int index = point * span + k;
                nodeX[index] = (int) Math.floor((line.xs()[point] - offset * tangents.z()[point]) / step + 0.5f);
                nodeZ[index] = (int) Math.floor((line.zs()[point] + offset * tangents.x()[point]) / step + 0.5f);
            }
        }
        HydrologySampleBuffer samples = new HydrologySampleBuffer(count);
        fine.fill(nodeX, nodeZ, samples);
        float[] ground = new float[count];
        boolean[] blocked = new boolean[count];
        for (int index = 0; index < count; index++) {
            int pond = ponds.at(nodeX[index] * step, nodeZ[index] * step);
            ground[index] = pond < 0 ? samples.height()[index] : Math.max(samples.height()[index], ponds.pond(pond).level());
            blocked[index] = pond >= 0 || samples.ocean()[index];
        }
        return new Reads(span, ground, samples.ocean(), blocked);
    }

    private void lowestNearbyGround(Fitted fitted) {
        int points = fitted.xs().length;
        int step = fine.cell();
        int[] firstX = new int[points];
        int[] firstZ = new int[points];
        int[] spanX = new int[points];
        int[] offsets = new int[points + 1];
        for (int point = 0; point < points; point++) {
            double x = fitted.xs()[point] / (double) step;
            double z = fitted.zs()[point] / (double) step;
            firstX[point] = (int) Math.ceil(x - 1D);
            firstZ[point] = (int) Math.ceil(z - 1D);
            spanX[point] = (int) Math.floor(x + 1D) - firstX[point] + 1;
            offsets[point + 1] = offsets[point] + spanX[point] * ((int) Math.floor(z + 1D) - firstZ[point] + 1);
        }
        int[] nodeX = new int[offsets[points]];
        int[] nodeZ = new int[offsets[points]];
        for (int point = 0; point < points; point++) {
            for (int node = offsets[point]; node < offsets[point + 1]; node++) {
                nodeX[node] = firstX[point] + (node - offsets[point]) % spanX[point];
                nodeZ[node] = firstZ[point] + (node - offsets[point]) / spanX[point];
            }
        }
        HydrologySampleBuffer samples = new HydrologySampleBuffer(nodeX.length);
        fine.fill(nodeX, nodeZ, samples);
        for (int point = 0; point < points; point++) {
            float lowest = Float.POSITIVE_INFINITY;
            for (int node = offsets[point]; node < offsets[point + 1]; node++) {
                int pond = ponds.at(nodeX[node] * step, nodeZ[node] * step);
                float height = pond < 0 ? samples.height()[node] : Math.max(samples.height()[node], ponds.pond(pond).level());
                lowest = Math.min(lowest, height);
            }
            fitted.ground()[point] = lowest;
        }
    }

    private static int[] viterbi(Reads reads, int count, int reach) {
        int span = reads.span();
        float[] cost = new float[count * span];
        int[] from = new int[count * span];
        Arrays.fill(cost, Float.POSITIVE_INFINITY);
        cost[reach] = reads.ground()[reach];
        for (int point = 1; point < count; point++) {
            for (int k = 0; k < span; k++) {
                int index = point * span + k;
                if (k != reach && (point == count - 1 || reads.blocked()[index])) {
                    continue;
                }
                float best = Float.POSITIVE_INFINITY;
                int previous = -1;
                for (int move : MOVES) {
                    int candidate = k + move;
                    if (candidate < 0 || candidate >= span) {
                        continue;
                    }
                    float total = cost[(point - 1) * span + candidate] + MOVE_PENALTY * Math.abs(move);
                    if (total < best) {
                        best = total;
                        previous = candidate;
                    }
                }
                if (previous >= 0) {
                    cost[index] = best + reads.ground()[index];
                    from[index] = previous;
                }
            }
        }
        int[] chosen = new int[count];
        int k = reach;
        for (int point = count - 1; point >= 0; point--) {
            chosen[point] = k - reach;
            k = from[point * span + k];
        }
        return chosen;
    }

    private static Fitted place(Line line, Tangents tangents, Reads reads, int[] offsets, HydrologyTier tier) {
        int count = line.xs().length;
        float[] px = new float[count];
        float[] pz = new float[count];
        for (int point = 0; point < count; point++) {
            float offset = offsets[point] * SPACING;
            px[point] = line.xs()[point] - offset * tangents.z()[point];
            pz[point] = line.zs()[point] + offset * tangents.x()[point];
        }
        int[] kept = new int[count];
        int size = 0;
        kept[size++] = 0;
        for (int point = 1; point < count - 1; point++) {
            if (advance(px, pz, tangents, kept[size - 1], point) >= MIN_ADVANCE) {
                kept[size++] = point;
            }
        }
        while (size > 1 && advance(px, pz, tangents, kept[size - 1], count - 1) < MIN_ADVANCE) {
            size--;
        }
        kept[size++] = count - 1;
        Fitted fitted = new Fitted(new float[size], new float[size], new float[size], new float[size], new boolean[size], new float[size]);
        for (int index = 0; index < size; index++) {
            int point = kept[index];
            int chosen = offsets[point] + reads.span() / 2;
            int read = point * reads.span() + chosen;
            float beside = (tier.halfWidth(line.catchment()[point]) + 1f) / SPACING;
            fitted.bank()[index] = Math.min(bank(reads, point, chosen, -beside), bank(reads, point, chosen, beside));
            fitted.xs()[index] = px[point];
            fitted.zs()[index] = pz[point];
            fitted.catchment()[index] = line.catchment()[point];
            fitted.ground()[index] = reads.ground()[read];
            fitted.ocean()[index] = reads.ocean()[read];
        }
        fitted.xs()[0] = line.xs()[0];
        fitted.zs()[0] = line.zs()[0];
        fitted.xs()[size - 1] = line.xs()[count - 1];
        fitted.zs()[size - 1] = line.zs()[count - 1];
        return fitted;
    }

    private static float bank(Reads reads, int point, int chosen, float offset) {
        float position = Math.clamp(chosen + offset, 0f, reads.span() - 1f);
        int near = (int) Math.floor(position);
        int far = Math.min(reads.span() - 1, near + 1);
        float share = position - near;
        float[] ground = reads.ground();
        int row = point * reads.span();
        return ground[row + near] + (ground[row + far] - ground[row + near]) * share;
    }

    private static float advance(float[] px, float[] pz, Tangents tangents, int from, int to) {
        return (px[to] - px[from]) * tangents.x()[to] + (pz[to] - pz[from]) * tangents.z()[to];
    }

    private static Tangents tangents(float[] xs, float[] zs) {
        int count = xs.length;
        float[] tx = new float[count];
        float[] tz = new float[count];
        for (int point = 0; point < count; point++) {
            int before = Math.max(0, point - 1);
            int after = Math.min(count - 1, point + 1);
            float dx = xs[after] - xs[before];
            float dz = zs[after] - zs[before];
            float length = (float) Math.sqrt(dx * dx + dz * dz);
            tx[point] = length > 1e-6f ? dx / length : 1f;
            tz[point] = length > 1e-6f ? dz / length : 0f;
        }
        return new Tangents(tx, tz);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    record Fitted(float[] xs, float[] zs, float[] catchment, float[] ground, boolean[] ocean, float[] bank) {
    }

    private record Line(float[] xs, float[] zs, float[] heights, float[] catchment, float[] distance, float length) {
    }

    private record Tangents(float[] x, float[] z) {
    }

    private record Reads(int span, float[] ground, boolean[] ocean, boolean[] blocked) {
    }
}
