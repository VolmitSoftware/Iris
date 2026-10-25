package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.grid.Basin;
import art.arcane.iris.generation.hydrology.grid.DemGrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CupSet {
    public static final CupSet EMPTY = new CupSet(List.of());

    private final List<Cup> cups;

    CupSet(List<Cup> cups) {
        this.cups = List.copyOf(cups);
    }

    public int size() {
        return cups.size();
    }

    List<Cup> within(int minX, int minZ, int maxX, int maxZ) {
        List<Cup> near = new ArrayList<>();
        for (Cup cup : cups) {
            if (cup.x() + cup.radius() > minX && cup.x() - cup.radius() < maxX
                    && cup.z() + cup.radius() > minZ && cup.z() - cup.radius() < maxZ) {
                near.add(cup);
            }
        }
        return near;
    }

    PondKind kind(DemGrid raw, Basin basin) {
        PondKind kind = PondKind.NATURAL;
        for (Cup cup : cups) {
            int cell = cellOf(raw, cup);
            if (cell >= 0 && Arrays.binarySearch(basin.cells(), cell) >= 0) {
                if (cup.chained()) {
                    return PondKind.CHAINED;
                }
                kind = PondKind.SEEDED;
            }
        }
        return kind;
    }

    private static int cellOf(DemGrid raw, Cup cup) {
        int offsetX = cup.x() - raw.originX();
        int offsetZ = cup.z() - raw.originZ();
        if (Math.floorMod(offsetX, raw.cell()) != 0 || Math.floorMod(offsetZ, raw.cell()) != 0) {
            return -1;
        }
        int i = Math.floorDiv(offsetX, raw.cell());
        int j = Math.floorDiv(offsetZ, raw.cell());
        if (i < 0 || j < 0 || i >= raw.width() || j >= raw.depth()) {
            return -1;
        }
        return j * raw.width() + i;
    }

    record Cup(int x, int z, int radius, float rim, int depth, boolean chained) {
        private static final int WALL = 4;

        float floorAt(int px, int pz) {
            long dx = px - x;
            long dz = pz - z;
            long squared = dx * dx + dz * dz;
            if (squared >= (long) radius * radius) {
                return Float.POSITIVE_INFINITY;
            }
            double bowl = Math.min(1D, (radius - Math.sqrt(squared)) / Math.min(WALL, radius));
            return (float) (rim - depth * bowl);
        }
    }
}
