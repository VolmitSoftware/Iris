package art.arcane.iris.world.history;

import art.arcane.volmlib.nativelib.terrain.NativeBlockColumn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BoundaryColumnGeometry implements NativeBlockColumn {
    public static final int MAXIMUM_HEIGHT = 65_536;
    private static final Comparator<Voxel> VOXEL_ORDER = BoundaryColumnGeometry::compareVoxels;
    private static final Voxel AIR = new Voxel("minecraft:air", Phase.AIR, "", false);
    private static final BoundaryColumnGeometry EMPTY = new BoundaryColumnGeometry(0, List.of(), new int[0], new short[0]);

    private final int minimumY;
    private final List<Voxel> palette;
    private final int[] runEnds;
    private final short[] paletteIndices;

    public BoundaryColumnGeometry(int minimumY, List<Voxel> palette, int[] runEnds, short[] paletteIndices) {
        this.minimumY = minimumY;
        this.palette = List.copyOf(Objects.requireNonNull(palette, "Geometry palette"));
        this.runEnds = Objects.requireNonNull(runEnds, "Geometry run ends").clone();
        this.paletteIndices = Objects.requireNonNull(paletteIndices, "Geometry palette indices").clone();
        validate();
    }

    public static BoundaryColumnGeometry empty() {
        return EMPTY;
    }

    public static BoundaryColumnGeometry fromVoxels(int minimumY, List<Voxel> voxels) {
        Objects.requireNonNull(voxels, "Geometry voxels");
        if (voxels.size() > MAXIMUM_HEIGHT) {
            throw new IllegalArgumentException("Geometry column exceeds maximum height");
        }
        Runs runs = new Runs(voxels.size());
        for (Voxel voxel : voxels) {
            runs.add(voxel);
        }
        return runs.build(minimumY);
    }

    private static int compareVoxels(Voxel first, Voxel second) {
        int order = first.stateKey().compareTo(second.stateKey());
        if (order != 0) {
            return order;
        }
        order = first.phase().compareTo(second.phase());
        if (order != 0) {
            return order;
        }
        order = first.fluidStateKey().compareTo(second.fluidStateKey());
        return order != 0 ? order : Boolean.compare(first.protectedContent(), second.protectedContent());
    }

    public int minimumY() {
        return minimumY;
    }

    public int height() {
        return runEnds.length == 0 ? 0 : runEnds[runEnds.length - 1];
    }

    public List<Voxel> palette() {
        return palette;
    }

    public int[] runEnds() {
        return runEnds.clone();
    }

    public short[] paletteIndices() {
        return paletteIndices.clone();
    }

    int runCount() {
        return runEnds.length;
    }

    int runEnd(int run) {
        return runEnds[run];
    }

    Voxel runVoxel(int run) {
        return palette.get(paletteIndices[run]);
    }

    @Override
    public String stateKeyAt(int worldY) {
        return voxelAt(worldY).stateKey();
    }

    public Voxel voxelAt(int worldY) {
        long offset = (long) worldY - minimumY;
        if (offset < 0 || offset >= height()) {
            return AIR;
        }
        return palette.get(paletteIndices[runAt((int) offset)]);
    }

    public boolean isEnclosedOpenAt(int worldY) {
        long offset = (long) worldY - minimumY;
        if (offset < 0 || offset >= height() || voxelAt(worldY).phase() == Phase.SOLID) {
            return false;
        }
        return hasSolidAbove(worldY);
    }

    public boolean hasSolidAbove(int worldY) {
        long offset = (long) worldY - minimumY;
        if (offset >= height() - 1L) {
            return false;
        }
        int startRun = offset < 0 ? 0 : runAt((int) offset);
        for (int run = startRun; run < runEnds.length; run++) {
            if (runEnds[run] <= offset + 1L) {
                continue;
            }
            if (palette.get(paletteIndices[run]).phase() == Phase.SOLID) {
                return true;
            }
        }
        return false;
    }

    public List<Voxel> voxels() {
        ArrayList<Voxel> values = new ArrayList<>(height());
        int start = 0;
        for (int run = 0; run < runEnds.length; run++) {
            Voxel value = palette.get(paletteIndices[run]);
            for (int offset = start; offset < runEnds[run]; offset++) {
                values.add(value);
            }
            start = runEnds[run];
        }
        return List.copyOf(values);
    }

    public int surfaceOffsetNear(double expectedOffset) {
        if (!Double.isFinite(expectedOffset) || height() == 0) {
            throw new IllegalArgumentException("A surface reference requires finite height and nonempty geometry");
        }
        int nearest = -1;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int run = 0; run + 1 < runEnds.length; run++) {
            Voxel current = palette.get(paletteIndices[run]);
            Voxel above = palette.get(paletteIndices[run + 1]);
            if (current.phase() != Phase.SOLID || current.protectedContent()
                    || above.phase() == Phase.SOLID && !above.protectedContent()) {
                continue;
            }
            int offset = runEnds[run] - 1;
            double distance = Math.abs(offset - expectedOffset);
            if (distance < nearestDistance) {
                nearest = offset;
                nearestDistance = distance;
            }
        }
        if (nearest >= 0) {
            return nearest;
        }
        for (int run = runEnds.length - 1; run >= 0; run--) {
            Voxel current = palette.get(paletteIndices[run]);
            if (current.phase() == Phase.SOLID && !current.protectedContent()) {
                return runEnds[run] - 1;
            }
        }
        return 0;
    }

    public double[] solidDistances() {
        return phaseDistances(Phase.SOLID);
    }

    public double[] fluidDistances() {
        return phaseDistances(Phase.FLUID);
    }

    private double[] phaseDistances(Phase phase) {
        int height = height();
        double[] distances = new double[height];
        boolean[] solid = new boolean[height];
        int start = 0;
        for (int run = 0; run < runEnds.length; run++) {
            Voxel value = palette.get(paletteIndices[run]);
            boolean occupied = value.phase() == phase && !value.protectedContent();
            Arrays.fill(solid, start, runEnds[run], occupied);
            start = runEnds[run];
        }
        double distance = height + 0.5D;
        for (int offset = 0; offset < height; offset++) {
            distance = offset > 0 && solid[offset] != solid[offset - 1] ? 0.5D : distance + 1D;
            distances[offset] = distance;
        }
        distance = height + 0.5D;
        for (int offset = height - 1; offset >= 0; offset--) {
            distance = offset + 1 < height && solid[offset] != solid[offset + 1] ? 0.5D : distance + 1D;
            distances[offset] = Math.min(distances[offset], distance) * (solid[offset] ? 1D : -1D);
        }
        return distances;
    }

    @Override
    public boolean equals(Object compared) {
        return this == compared || compared instanceof BoundaryColumnGeometry other
                && minimumY == other.minimumY && palette.equals(other.palette)
                && Arrays.equals(runEnds, other.runEnds) && Arrays.equals(paletteIndices, other.paletteIndices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minimumY, palette, Arrays.hashCode(runEnds), Arrays.hashCode(paletteIndices));
    }

    private int runAt(int offset) {
        int index = Arrays.binarySearch(runEnds, offset + 1);
        return index >= 0 ? index : -index - 1;
    }

    private void validate() {
        if (palette.size() > Short.MAX_VALUE + 1 || runEnds.length > MAXIMUM_HEIGHT
                || runEnds.length != paletteIndices.length) {
            throw new IllegalArgumentException("Invalid compact geometry sizes");
        }
        int previousEnd = 0;
        for (int run = 0; run < runEnds.length; run++) {
            if (runEnds[run] <= previousEnd || runEnds[run] > MAXIMUM_HEIGHT
                    || paletteIndices[run] < 0 || paletteIndices[run] >= palette.size()) {
                throw new IllegalArgumentException("Invalid compact geometry run");
            }
            previousEnd = runEnds[run];
        }
        if (height() > 0) {
            Math.toIntExact((long) minimumY + height() - 1L);
        }
    }

    static final class Runs {
        private static final int LINEAR_PALETTE_LIMIT = 32;

        private final Voxel[] voxels;
        private final int[] ends;
        private int count;
        private int length;

        Runs(int capacity) {
            voxels = new Voxel[capacity];
            ends = new int[capacity];
        }

        void clear() {
            count = 0;
            length = 0;
        }

        void add(Voxel voxel) {
            Objects.requireNonNull(voxel, "Geometry voxel");
            if (count == 0 || !sameVoxel(voxels[count - 1], voxel)) {
                voxels[count++] = voxel;
            }
            ends[count - 1] = ++length;
        }

        BoundaryColumnGeometry build(int minimumY) {
            Voxel[] distinct = new Voxel[count];
            int[] slots = new int[count];
            Map<Voxel, Integer> lookup = null;
            int distinctCount = 0;
            for (int run = 0; run < count; run++) {
                Voxel voxel = voxels[run];
                int slot = lookup == null ? linearIndex(distinct, distinctCount, voxel) : lookup.getOrDefault(voxel, -1);
                if (slot < 0) {
                    slot = distinctCount;
                    distinct[distinctCount++] = voxel;
                    if (lookup != null) {
                        lookup.put(voxel, slot);
                    } else if (distinctCount > LINEAR_PALETTE_LIMIT) {
                        lookup = new HashMap<>();
                        for (int index = 0; index < distinctCount; index++) {
                            lookup.put(distinct[index], index);
                        }
                    }
                }
                slots[run] = slot;
            }
            if (distinctCount > Short.MAX_VALUE + 1) {
                throw new IllegalArgumentException("Geometry palette exceeds compact index capacity");
            }
            Voxel[] palette = Arrays.copyOf(distinct, distinctCount);
            Arrays.sort(palette, VOXEL_ORDER);
            short[] slotIndices = new short[distinctCount];
            if (lookup == null) {
                for (int slot = 0; slot < distinctCount; slot++) {
                    slotIndices[slot] = (short) linearIndex(palette, distinctCount, distinct[slot]);
                }
            } else {
                for (int index = 0; index < distinctCount; index++) {
                    slotIndices[lookup.get(palette[index])] = (short) index;
                }
            }
            short[] values = new short[count];
            for (int run = 0; run < count; run++) {
                values[run] = slotIndices[slots[run]];
            }
            return new BoundaryColumnGeometry(minimumY, Arrays.asList(palette), Arrays.copyOf(ends, count), values);
        }

        private static int linearIndex(Voxel[] values, int size, Voxel voxel) {
            for (int index = 0; index < size; index++) {
                if (sameVoxel(values[index], voxel)) {
                    return index;
                }
            }
            return -1;
        }

        private static boolean sameVoxel(Voxel first, Voxel second) {
            return first == second || first.equals(second);
        }
    }

    public enum Phase {
        AIR,
        SOLID,
        FLUID
    }

    public record Voxel(String stateKey, Phase phase, String fluidStateKey, boolean protectedContent) {
        public Voxel {
            Objects.requireNonNull(stateKey, "Block state key");
            Objects.requireNonNull(phase, "Voxel phase");
            Objects.requireNonNull(fluidStateKey, "Fluid state key");
            if (stateKey.isBlank()) {
                throw new IllegalArgumentException("Block state key cannot be blank");
            }
            if (phase == Phase.FLUID && fluidStateKey.isBlank()) {
                throw new IllegalArgumentException("Fluid voxels require a fluid state key");
            }
        }
    }
}
