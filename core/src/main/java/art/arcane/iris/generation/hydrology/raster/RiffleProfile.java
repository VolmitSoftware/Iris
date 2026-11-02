package art.arcane.iris.generation.hydrology.raster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class RiffleProfile {
    static final int RADIUS = 16;
    static final int PLUNGE_MIN_DROP = 6;
    static final double PLUNGE_RATIO = 0.5D;
    static final int PLUNGE_MAX_DEPTH = 8;
    private static final int PLUNGE_MIN_DEPTH = 2;
    private static final double PLUNGE_RADIUS_RATIO = 0.75D;
    private static final double PLUNGE_MIN_RADIUS = 3D;
    private static final double PLUNGE_MAX_RADIUS = 12D;
    private static final int CHUNK = 16;
    private static final int PADDING = RADIUS + 1;
    private static final int GRID = CHUNK + 2 * PADDING;
    private static final int OUTPUT = CHUNK + 2;
    private static final int LIMIT = 10 * RADIUS;
    private static final int DRY = Integer.MIN_VALUE;
    private static final int[] SIDES = {1, -1, GRID, -GRID};
    private static final int[] REMAINING = remaining();

    private final int[] heads = new int[GRID * GRID];
    private final long[] features = new long[GRID * GRID];
    private final boolean[] flowing = new boolean[GRID * GRID];
    private final List<Field> fields = new ArrayList<>();
    private final List<Plunge> plunges = new ArrayList<>();

    RiffleProfile(int chunkX, int chunkZ, Water water) {
        int originX = (chunkX << 4) - PADDING;
        int originZ = (chunkZ << 4) - PADDING;
        Sample sample = new Sample();
        for (int cell = 0; cell < heads.length; cell++) {
            sample.set(DRY, 0L, WaterRole.NONE);
            water.sample(originX + cell % GRID, originZ + cell / GRID, sample);
            heads[cell] = sample.head;
            features[cell] = sample.feature;
            flowing[cell] = sample.role.flowing();
        }
        TreeMap<Crest, Seeds> crests = new TreeMap<>();
        TreeMap<Fall, Seeds> feet = new TreeMap<>();
        TreeMap<Integer, Seeds> brinks = new TreeMap<>();
        for (int z = 1; z < GRID - 1; z++) {
            for (int x = 1; x < GRID - 1; x++) {
                collect(z * GRID + x, crests, feet, brinks);
            }
        }
        if (crests.isEmpty() && feet.isEmpty()) {
            return;
        }
        DistanceField distances = new DistanceField(heads);
        for (Map.Entry<Crest, Seeds> crest : crests.entrySet()) {
            int[] output = distances.measure(crest.getValue(), DistanceField.ANY_LEVEL, LIMIT);
            if (output != null) {
                fields.add(new Field(crest.getKey(), output));
            }
        }
        for (Map.Entry<Fall, Seeds> fall : feet.entrySet()) {
            Fall key = fall.getKey();
            int[] output = distances.measure(fall.getValue(), key.foot(), key.reach());
            if (output != null) {
                Seeds tail = brinks.get(key.foot());
                plunges.add(new Plunge(key, output, tail == null ? null : distances.measure(tail, key.foot(), key.reach())));
            }
        }
    }

    int bed(int localX, int localZ, int original, int head, double variation) {
        if (head == DRY) {
            return original;
        }
        int cell = (localZ + 1) * OUTPUT + localX + 1;
        int grid = (localZ + PADDING) * GRID + localX + PADDING;
        double raised = original;
        for (Field field : fields) {
            if (skips(field.crest(), grid, head)) {
                continue;
            }
            int crest = Math.min(head - 1, field.crest().level());
            if (crest <= original) {
                continue;
            }
            double reach = 10D * Math.min(RADIUS, Math.max(4D, 2D * (crest - (double) original)))
                    * (0.925D + 0.075D * variation);
            if (field.distances()[cell] >= reach) {
                continue;
            }
            double u = field.distances()[cell] / reach;
            double blend = 1D - u * u * (3D - 2D * u);
            raised = Math.max(raised, original + (crest - (double) original) * blend);
        }
        int riffled = (int) Math.max(original, Math.min(head - 1L, Math.round(raised)));
        return flowing[grid] ? plunge(cell, riffled, head, variation) : riffled;
    }

    private void collect(int cell, TreeMap<Crest, Seeds> crests, TreeMap<Fall, Seeds> feet, TreeMap<Integer, Seeds> brinks) {
        int head = heads[cell];
        if (head == DRY) {
            return;
        }
        boolean crest = false;
        for (int side : SIDES) {
            int neighbour = heads[cell + side];
            if (neighbour == DRY) {
                continue;
            }
            crest |= neighbour <= head - 2;
            if (flowing[cell] && neighbour - head >= PLUNGE_MIN_DROP) {
                feet.computeIfAbsent(new Fall(head, neighbour), ignored -> new Seeds()).add(cell);
            }
        }
        if (crest) {
            crests.computeIfAbsent(new Crest(head - 1, features[cell], flowing[cell]), ignored -> new Seeds()).add(cell);
            brinks.computeIfAbsent(head, ignored -> new Seeds()).add(cell);
        }
    }

    private boolean skips(Crest crest, int grid, int head) {
        if (crest.level() - (head - 1) >= PLUNGE_MIN_DROP - 1) {
            return true;
        }
        return crest.level() > head - 1 && crest.flowing() && flowing[grid] && crest.feature() != features[grid];
    }

    private int plunge(int cell, int bed, int head, double variation) {
        int carved = bed;
        for (Plunge plunge : plunges) {
            Fall fall = plunge.fall();
            int distance = plunge.distances()[cell];
            double span = plunge.span(cell);
            if (fall.foot() != head || distance >= span) {
                continue;
            }
            long depth = Math.clamp(Math.round(PLUNGE_RATIO * fall.drop() * (0.85D + 0.15D * variation)), PLUNGE_MIN_DEPTH, PLUNGE_MAX_DEPTH);
            double u = distance / span;
            double bowl = 1D - u * u * (3D - 2D * u);
            carved = (int) Math.min(carved, head - 1L - Math.round((depth - 1L) * bowl));
        }
        return carved;
    }

    private static int[] remaining() {
        int[] remaining = new int[GRID * GRID];
        int low = PADDING - 1;
        int high = PADDING + CHUNK;
        for (int z = 0; z < GRID; z++) {
            for (int x = 0; x < GRID; x++) {
                int outsideX = Math.max(0, Math.max(low - x, x - high));
                int outsideZ = Math.max(0, Math.max(low - z, z - high));
                remaining[z * GRID + x] = 10 * Math.max(outsideX, outsideZ);
            }
        }
        return remaining;
    }

    @FunctionalInterface
    interface Water {
        void sample(int x, int z, Sample sample);
    }

    static final class Sample {
        private int head;
        private long feature;
        private WaterRole role;

        void set(int head, long feature, WaterRole role) {
            this.head = head;
            this.feature = feature;
            this.role = role;
        }
    }

    private record Crest(int level, long feature, boolean flowing) implements Comparable<Crest> {
        @Override
        public int compareTo(Crest other) {
            if (level != other.level) {
                return Integer.compare(level, other.level);
            }
            if (feature != other.feature) {
                return Long.compare(feature, other.feature);
            }
            return Boolean.compare(flowing, other.flowing);
        }
    }

    private record Fall(int foot, int top) implements Comparable<Fall> {
        int drop() {
            return top - foot;
        }

        double radius() {
            return Math.clamp(PLUNGE_RADIUS_RATIO * drop(), PLUNGE_MIN_RADIUS, PLUNGE_MAX_RADIUS);
        }

        int reach() {
            return (int) Math.ceil(10D * radius());
        }

        @Override
        public int compareTo(Fall other) {
            return foot != other.foot ? Integer.compare(foot, other.foot) : Integer.compare(top, other.top);
        }
    }

    private record Field(Crest crest, int[] distances) {
    }

    private static final class Seeds {
        private int[] cells = new int[16];
        private int size;

        private void add(int cell) {
            if (size == cells.length) {
                cells = Arrays.copyOf(cells, 2 * size);
            }
            cells[size++] = cell;
        }
    }

    private record Plunge(Fall fall, int[] distances, int[] brinks) {
        double span(int cell) {
            double radius = 10D * fall.radius();
            return brinks == null ? radius : Math.min(radius, distances[cell] + (double) brinks[cell]);
        }
    }

    private static final class DistanceField {
        private static final int ANY_LEVEL = Integer.MAX_VALUE;

        private final int[] heads;
        private final int[] distances = new int[GRID * GRID];
        private final int[] next = new int[GRID * GRID];
        private final int[] previous = new int[GRID * GRID];
        private final int[] first = new int[LIMIT];
        private int level;
        private int limit;

        private DistanceField(int[] heads) {
            this.heads = heads;
        }

        private int[] measure(Seeds seeds, int passable, int reach) {
            level = passable;
            limit = reach;
            Arrays.fill(distances, LIMIT);
            Arrays.fill(next, -1);
            Arrays.fill(previous, -1);
            Arrays.fill(first, -1);
            for (int seed = 0; seed < seeds.size; seed++) {
                offer(seeds.cells[seed], 0);
            }
            spread();
            return output();
        }

        private boolean blocked(int cell) {
            return heads[cell] == DRY || level != ANY_LEVEL && heads[cell] != level;
        }

        private void offer(int cell, int distance) {
            if (distance + REMAINING[cell] >= limit) {
                return;
            }
            if (distances[cell] < LIMIT) {
                remove(cell);
            }
            distances[cell] = distance;
            next[cell] = first[distance];
            previous[cell] = -1;
            if (next[cell] >= 0) {
                previous[next[cell]] = cell;
            }
            first[distance] = cell;
        }

        private void remove(int cell) {
            if (previous[cell] < 0) {
                first[distances[cell]] = next[cell];
            } else {
                next[previous[cell]] = next[cell];
            }
            if (next[cell] >= 0) {
                previous[next[cell]] = previous[cell];
            }
            next[cell] = -1;
            previous[cell] = -1;
        }

        private void spread() {
            for (int distance = 0; distance < limit; distance++) {
                while (first[distance] >= 0) {
                    int cell = first[distance];
                    remove(cell);
                    int x = cell % GRID;
                    int z = cell / GRID;
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx;
                            int nz = z + dz;
                            if (dx == 0 && dz == 0 || nx < 0 || nx >= GRID || nz < 0 || nz >= GRID) {
                                continue;
                            }
                            int neighbour = nz * GRID + nx;
                            int candidate = distance + (dx != 0 && dz != 0 ? 14 : 10);
                            if (blocked(neighbour) || candidate >= distances[neighbour]) {
                                continue;
                            }
                            if (dx != 0 && dz != 0 && (blocked(z * GRID + nx) || blocked(nz * GRID + x))) {
                                continue;
                            }
                            offer(neighbour, candidate);
                        }
                    }
                }
            }
        }

        private int[] output() {
            int[] output = new int[OUTPUT * OUTPUT];
            boolean affected = false;
            for (int z = 0; z < OUTPUT; z++) {
                for (int x = 0; x < OUTPUT; x++) {
                    int distance = distances[(z + PADDING - 1) * GRID + x + PADDING - 1];
                    output[z * OUTPUT + x] = distance;
                    affected |= distance < LIMIT;
                }
            }
            return affected ? output : null;
        }
    }
}
