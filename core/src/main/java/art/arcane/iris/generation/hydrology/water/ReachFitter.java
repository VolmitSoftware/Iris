package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;

import java.util.Arrays;

final class ReachFitter {
    private static final float SPACING = 4f;
    private static final int RELAX_PASSES = 6;
    private static final float RELAX_MAX_SHIFT = 4f;
    private static final float RELAX_LAMBDA = 0.5f;
    private static final float RELAX_MU = -0.53f;
    private static final double MEANDER_WIDTH_SCALE = 2.5D;
    private static final double MEANDER_MIN = 2D;
    private static final double MEANDER_MAX = 16D;
    private static final double MEANDER_WAVELENGTH_WIDTHS = 12D;
    private static final double MEANDER_WAVELENGTH_MIN = 24D;
    private static final double MEANDER_WAVELENGTH_MAX = 192D;
    private static final double[] MEANDER_WAVE_SCALES = {1D, 0.43D, 1.77D};
    private static final double[] MEANDER_WAVE_WEIGHTS = {0.5D, 0.3D, 0.2D};
    private static final double MEANDER_SLOPE = 0.2D;
    private static final double STEEP_MEANDER = 0.4D;
    private static final int DAMPING_RAMP = 8;
    private static final float SHORE_MARGIN = 2f;
    private static final int SHORE_RAMP = 4;
    private static final int OFFSET_SMOOTHING = 3;
    private static final float OFFSET_CLAMP = 0.5f;
    private static final float VALLEY_SNAP = 8f;
    private static final float MOVE_PENALTY = 0.25f * SPACING;
    private static final float MIN_ADVANCE = 0.25f * SPACING;
    private static final float COVER_AHEAD = 2.5f;
    private static final int[] MOVES = {0, -1, 1};
    private static final long SALT_MEANDER = 0x4d45414e444552L;

    private final HeightLattice fine;
    private final PondLookup ponds;

    ReachFitter(HeightLattice fine, PondLookup ponds) {
        this.fine = fine;
        this.ponds = ponds;
    }

    Fitted fit(DemGrid grid, int[] cells, float[] cellCatchment, HydrologyTier tier, long shapeSeed, boolean drainsToTheSea) {
        return fit(grid, cells, cellCatchment, tier, shapeSeed, drainsToTheSea, tier.halfWidth(cellCatchment[cellCatchment.length - 1]), MEANDER_WIDTH_SCALE, true);
    }

    Fitted fit(DemGrid grid, int[] cells, float[] cellCatchment, HydrologyTier tier, long shapeSeed, boolean drainsToTheSea, float meanderWidth, double amplitudeScale, boolean dampOnSlopes) {
        float halfWidth = tier.halfWidth(cellCatchment[cellCatchment.length - 1]);
        Line line = relax(resample(grid, cells, cellCatchment));
        Tangents tangents = tangents(line.xs(), line.zs());
        meander(line, tangents, shapeSeed, meanderWidth, amplitudeScale, dampOnSlopes);
        int reach = Math.max(2, (int) Math.ceil(Math.max(VALLEY_SNAP, halfWidth + 2f) / SPACING));
        Reads reads = read(line, tangents, reach);
        Fitted fitted = place(line, tangents, reads, soften(viterbi(reads, line.xs().length, reach), reads, reach), tier);
        seaEnd(fitted.ocean(), drainsToTheSea);
        lowestCoveredGround(fitted, tier);
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

    private static Line relax(Line line) {
        int count = line.xs().length;
        if (count < 3) {
            return line;
        }
        float[] xs = line.xs().clone();
        float[] zs = line.zs().clone();
        for (int pass = 0; pass < RELAX_PASSES; pass++) {
            laplacian(xs, zs, line, RELAX_LAMBDA);
            laplacian(xs, zs, line, RELAX_MU);
        }
        return respace(line, xs, zs);
    }

    private static void laplacian(float[] xs, float[] zs, Line origin, float weight) {
        float[] fromX = xs.clone();
        float[] fromZ = zs.clone();
        for (int point = 1; point < xs.length - 1; point++) {
            float x = fromX[point] + weight * (0.5f * (fromX[point - 1] + fromX[point + 1]) - fromX[point]);
            float z = fromZ[point] + weight * (0.5f * (fromZ[point - 1] + fromZ[point + 1]) - fromZ[point]);
            float shiftX = x - origin.xs()[point];
            float shiftZ = z - origin.zs()[point];
            float shift = (float) Math.sqrt(shiftX * shiftX + shiftZ * shiftZ);
            float keep = shift > RELAX_MAX_SHIFT ? RELAX_MAX_SHIFT / shift : 1f;
            xs[point] = origin.xs()[point] + shiftX * keep;
            zs[point] = origin.zs()[point] + shiftZ * keep;
        }
    }

    private static Line respace(Line line, float[] xs, float[] zs) {
        int count = xs.length;
        double[] along = new double[count];
        for (int point = 1; point < count; point++) {
            along[point] = along[point - 1] + StrictMath.hypot(xs[point] - xs[point - 1], zs[point] - zs[point - 1]);
        }
        double length = along[count - 1];
        int segments = Math.max(1, (int) Math.round(length / SPACING));
        Line spaced = new Line(new float[segments + 1], new float[segments + 1], new float[segments + 1], new float[segments + 1],
                new float[segments + 1], (float) length);
        int segment = 0;
        for (int point = 0; point <= segments; point++) {
            double s = length * point / segments;
            while (segment < count - 2 && along[segment + 1] < s) {
                segment++;
            }
            double span = along[segment + 1] - along[segment];
            float t = span > 0D ? (float) ((s - along[segment]) / span) : 0f;
            spaced.xs()[point] = lerp(xs[segment], xs[segment + 1], t);
            spaced.zs()[point] = lerp(zs[segment], zs[segment + 1], t);
            spaced.heights()[point] = lerp(line.heights()[segment], line.heights()[segment + 1], t);
            spaced.catchment()[point] = lerp(line.catchment()[segment], line.catchment()[segment + 1], t);
            spaced.distance()[point] = (float) s;
        }
        spaced.xs()[0] = xs[0];
        spaced.zs()[0] = zs[0];
        spaced.heights()[0] = line.heights()[0];
        spaced.catchment()[0] = line.catchment()[0];
        spaced.xs()[segments] = xs[count - 1];
        spaced.zs()[segments] = zs[count - 1];
        spaced.heights()[segments] = line.heights()[count - 1];
        spaced.catchment()[segments] = line.catchment()[count - 1];
        return spaced;
    }

    private void meander(Line line, Tangents tangents, long shapeSeed, float halfWidth, double amplitudeScale, boolean dampOnSlopes) {
        int count = line.xs().length;
        if (count < 3) {
            return;
        }
        double[] phase = new double[MEANDER_WAVE_SCALES.length];
        for (int wave = 0; wave < phase.length; wave++) {
            phase[wave] = 2D * Math.PI * CellHash.unit(CellHash.mix(shapeSeed, SALT_MEANDER, wave, 0));
        }
        double amplitude = Math.clamp(amplitudeScale * (halfWidth + 1f), MEANDER_MIN, MEANDER_MAX);
        double wavelength = Math.clamp(MEANDER_WAVELENGTH_WIDTHS * 2D * (halfWidth + 1f), MEANDER_WAVELENGTH_MIN, MEANDER_WAVELENGTH_MAX);
        double ramp = Math.min(line.length() / 2D, wavelength / 2D);
        float[] open = awayFromPonds(line, tangents, (float) amplitude + halfWidth + SHORE_MARGIN);
        double[] damping = dampOnSlopes ? slopeDamping(line) : null;
        float[] offsets = new float[count];
        for (int point = 1; point < count - 1; point++) {
            double s = line.distance()[point];
            double wave = 0D;
            for (int index = 0; index < phase.length; index++) {
                wave += MEANDER_WAVE_WEIGHTS[index] * StrictMath.sin(2D * Math.PI * s / (wavelength * MEANDER_WAVE_SCALES[index]) + phase[index]);
            }
            double envelope = smoothstep(s / ramp) * smoothstep((line.length() - s) / ramp);
            offsets[point] = (float) (amplitude * envelope * (damping == null ? 1D : damping[point]) * wave * open[point]);
        }
        for (int point = 1; point < count - 1; point++) {
            line.xs()[point] -= offsets[point] * tangents.z()[point];
            line.zs()[point] += offsets[point] * tangents.x()[point];
        }
    }

    private static double[] slopeDamping(Line line) {
        int count = line.xs().length;
        double[] local = new double[count];
        Arrays.fill(local, 1D);
        for (int point = 1; point < count - 1; point++) {
            double run = Math.max(1e-3D, line.distance()[point + 1] - line.distance()[point - 1]);
            double slope = Math.abs(line.heights()[point + 1] - line.heights()[point - 1]) / run;
            local[point] = STEEP_MEANDER + (1D - STEEP_MEANDER) * (1D - smoothstep(slope / MEANDER_SLOPE));
        }
        double[] damping = new double[count];
        Arrays.fill(damping, 1D);
        for (int point = 0; point < count; point++) {
            for (int other = Math.max(0, point - DAMPING_RAMP); other <= Math.min(count - 1, point + DAMPING_RAMP); other++) {
                double eased = local[other] + (1D - local[other]) * smoothstep(Math.abs(other - point) / (double) DAMPING_RAMP);
                damping[point] = Math.min(damping[point], eased);
            }
        }
        return damping;
    }

    private float[] awayFromPonds(Line line, Tangents tangents, float reach) {
        int count = line.xs().length;
        float[] open = new float[count];
        Arrays.fill(open, 1f);
        if (ponds.isEmpty()) {
            return open;
        }
        int nodes = (int) Math.ceil(reach / SPACING);
        boolean[] beside = new boolean[count];
        for (int point = 0; point < count; point++) {
            for (int node = -nodes; node <= nodes && !beside[point]; node++) {
                float offset = node * SPACING;
                beside[point] = ponds.at(Math.round(line.xs()[point] - offset * tangents.z()[point]), Math.round(line.zs()[point] + offset * tangents.x()[point])) >= 0;
            }
        }
        for (int point = 0; point < count; point++) {
            for (int other = Math.max(0, point - SHORE_RAMP); other <= Math.min(count - 1, point + SHORE_RAMP); other++) {
                if (beside[other]) {
                    open[point] = Math.min(open[point], (float) smoothstep(Math.abs(other - point) / (double) SHORE_RAMP));
                }
            }
        }
        return open;
    }

    private static double smoothstep(double value) {
        double t = Math.clamp(value, 0D, 1D);
        return t * t * (3D - 2D * t);
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

    private void lowestCoveredGround(Fitted fitted, HydrologyTier tier) {
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
        Tangents tangents = tangents(fitted.xs(), fitted.zs());
        float[] ahead = lowestColumnsAhead(fitted);
        float[] edgesAhead = lowestEdgesAhead(fitted, tangents, tier);
        float drop = tier.waterfallMinDrop();
        for (int point = 0; point < points; point++) {
            float lowest = Float.POSITIVE_INFINITY;
            float lowestLand = Float.POSITIVE_INFINITY;
            float coveredLand = Float.POSITIVE_INFINITY;
            for (int node = offsets[point]; node < offsets[point + 1]; node++) {
                int pond = ponds.at(nodeX[node] * step, nodeZ[node] * step);
                float height = pond < 0 ? samples.height()[node] : Math.max(samples.height()[node], ponds.pond(pond).level());
                lowest = Math.min(lowest, height);
                if (!samples.ocean()[node]) {
                    lowestLand = Math.min(lowestLand, height);
                    float forward = (nodeX[node] * step - fitted.xs()[point]) * tangents.x()[point]
                            + (nodeZ[node] * step - fitted.zs()[point]) * tangents.z()[point];
                    if (forward <= COVER_AHEAD) {
                        coveredLand = Math.min(coveredLand, height);
                    }
                }
            }
            if (fitted.ocean()[point] || lowestLand == Float.POSITIVE_INFINITY) {
                fitted.ground()[point] = lowest;
                continue;
            }
            float edge = Math.min(Math.min(coveredLand, ahead[point]), Math.min(edgesAhead[point], fitted.bank()[point]));
            if (coveredLand != Float.POSITIVE_INFINITY && edge - lowestLand >= drop) {
                fitted.ground()[point] = edge;
            } else {
                fitted.ground()[point] = lowestLand - ahead[point] >= drop ? ahead[point] : lowestLand;
            }
        }
    }

    private float[] lowestColumnsAhead(Fitted fitted) {
        int points = fitted.xs().length;
        int[] offsets = new int[points + 1];
        for (int point = 0; point < points; point++) {
            int next = Math.min(points - 1, point + 1);
            double half = 0.5D * Math.hypot(fitted.xs()[next] - fitted.xs()[point], fitted.zs()[next] - fitted.zs()[point]);
            offsets[point + 1] = offsets[point] + 2 + (int) Math.ceil(half);
        }
        int[] columnX = new int[offsets[points]];
        int[] columnZ = new int[offsets[points]];
        for (int point = 0; point < points; point++) {
            int columns = offsets[point + 1] - offsets[point];
            int next = Math.min(points - 1, point + 1);
            for (int column = 0; column < columns; column++) {
                float share = 0.5f * column / (columns - 1);
                columnX[offsets[point] + column] = (int) Math.floor(lerp(fitted.xs()[point], fitted.xs()[next], share));
                columnZ[offsets[point] + column] = (int) Math.floor(lerp(fitted.zs()[point], fitted.zs()[next], share));
            }
        }
        float[] heights = landColumns(columnX, columnZ);
        float[] lowest = new float[points];
        for (int point = 0; point < points; point++) {
            lowest[point] = Float.POSITIVE_INFINITY;
            for (int column = offsets[point]; column < offsets[point + 1]; column++) {
                lowest[point] = Math.min(lowest[point], heights[column]);
            }
        }
        return lowest;
    }

    private float[] lowestEdgesAhead(Fitted fitted, Tangents tangents, HydrologyTier tier) {
        int points = fitted.xs().length;
        int[] columnX = new int[2 * points];
        int[] columnZ = new int[2 * points];
        for (int point = 0; point < points; point++) {
            int next = Math.min(points - 1, point + 1);
            float x = lerp(fitted.xs()[point], fitted.xs()[next], 0.5f);
            float z = lerp(fitted.zs()[point], fitted.zs()[next], 0.5f);
            float halfWidth = tier.halfWidth(fitted.catchment()[point]);
            columnX[2 * point] = (int) Math.floor(x - halfWidth * tangents.z()[point]);
            columnZ[2 * point] = (int) Math.floor(z + halfWidth * tangents.x()[point]);
            columnX[2 * point + 1] = (int) Math.floor(x + halfWidth * tangents.z()[point]);
            columnZ[2 * point + 1] = (int) Math.floor(z - halfWidth * tangents.x()[point]);
        }
        float[] heights = landColumns(columnX, columnZ);
        float[] lowest = new float[points];
        for (int point = 0; point < points; point++) {
            lowest[point] = Math.min(heights[2 * point], heights[2 * point + 1]);
        }
        return lowest;
    }

    private float[] landColumns(int[] columnX, int[] columnZ) {
        HydrologySampleBuffer samples = new HydrologySampleBuffer(columnX.length);
        fine.sampleColumns(columnX, columnZ, samples);
        float[] heights = new float[columnX.length];
        for (int column = 0; column < heights.length; column++) {
            int pond = ponds.at(columnX[column], columnZ[column]);
            float height = pond < 0 ? samples.height()[column] : Math.max(samples.height()[column], ponds.pond(pond).level());
            heights[column] = samples.ocean()[column] ? Float.POSITIVE_INFINITY : height;
        }
        return heights;
    }

    private static void seaEnd(boolean[] ocean, boolean drainsToTheSea) {
        int start = ocean.length - 1;
        ocean[start] = drainsToTheSea;
        while (drainsToTheSea && start > 0 && ocean[start - 1]) {
            start--;
        }
        Arrays.fill(ocean, 0, start, false);
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

    private static float[] soften(int[] chosen, Reads reads, int reach) {
        int count = chosen.length;
        float[] offsets = new float[count];
        for (int point = 0; point < count; point++) {
            offsets[point] = chosen[point];
        }
        for (int pass = 0; pass < OFFSET_SMOOTHING; pass++) {
            float[] from = offsets.clone();
            for (int point = 1; point < count - 1; point++) {
                float smoothed = 0.25f * from[point - 1] + 0.5f * from[point] + 0.25f * from[point + 1];
                offsets[point] = Math.clamp(smoothed, chosen[point] - OFFSET_CLAMP, chosen[point] + OFFSET_CLAMP);
            }
        }
        for (int point = 1; point < count - 1; point++) {
            int toward = (int) Math.signum(offsets[point] - chosen[point]);
            int neighbour = chosen[point] + toward + reach;
            if (toward != 0 && (neighbour < 0 || neighbour >= reads.span() || reads.blocked()[point * reads.span() + neighbour])) {
                offsets[point] = chosen[point];
            }
        }
        return offsets;
    }

    private static Fitted place(Line line, Tangents tangents, Reads reads, float[] offsets, HydrologyTier tier) {
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
            float chosen = offsets[point] + reads.span() / 2;
            int read = point * reads.span() + Math.round(chosen);
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

    private static float bank(Reads reads, int point, float chosen, float offset) {
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
