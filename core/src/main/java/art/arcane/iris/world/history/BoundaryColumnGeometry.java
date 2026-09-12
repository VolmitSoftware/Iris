package art.arcane.iris.world.history;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public final class BoundaryColumnGeometry {
    public static final int MAXIMUM_HEIGHT = 65_536;
    private static final Comparator<Voxel> VOXEL_ORDER = Comparator.comparing(Voxel::stateKey)
            .thenComparing(Voxel::phase)
            .thenComparing(Voxel::fluidStateKey)
            .thenComparing(Voxel::protectedContent);
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
        TreeSet<Voxel> used = new TreeSet<>(VOXEL_ORDER);
        Voxel previous = null;
        for (Voxel voxel : voxels) {
            if (previous == null || !previous.equals(voxel)) {
                used.add(voxel);
                previous = voxel;
            }
        }
        List<Voxel> palette = List.copyOf(used);
        Map<Voxel, Short> indices = new HashMap<>(palette.size());
        for (int index = 0; index < palette.size(); index++) {
            if (index > Short.MAX_VALUE) {
                throw new IllegalArgumentException("Geometry palette exceeds compact index capacity");
            }
            indices.put(palette.get(index), (short) index);
        }
        int[] ends = new int[voxels.size()];
        short[] values = new short[voxels.size()];
        int runCount = 0;
        previous = null;
        for (int offset = 0; offset < voxels.size(); offset++) {
            Voxel voxel = voxels.get(offset);
            if (previous == null || !previous.equals(voxel)) {
                values[runCount++] = indices.get(voxel);
                previous = voxel;
            }
            ends[runCount - 1] = offset + 1;
        }
        return new BoundaryColumnGeometry(minimumY, palette,
                Arrays.copyOf(ends, runCount), Arrays.copyOf(values, runCount));
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
        for (int offset = 0; offset + 1 < height(); offset++) {
            Voxel current = voxelAt(minimumY + offset);
            Voxel above = voxelAt(minimumY + offset + 1);
            if (current.phase() != Phase.SOLID || current.protectedContent()
                    || above.phase() == Phase.SOLID && !above.protectedContent()) {
                continue;
            }
            double distance = Math.abs(offset - expectedOffset);
            if (distance < nearestDistance) {
                nearest = offset;
                nearestDistance = distance;
            }
        }
        if (nearest >= 0) {
            return nearest;
        }
        for (int offset = height() - 1; offset >= 0; offset--) {
            Voxel current = voxelAt(minimumY + offset);
            if (current.phase() == Phase.SOLID && !current.protectedContent()) {
                return offset;
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
