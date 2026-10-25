package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;

import java.util.Objects;

final class FineLatticeSource implements HydrologyTerrainSource {
    private final HeightLattice fine;
    private final HydrologyTerrainSource terrain;

    FineLatticeSource(HeightLattice fine, HydrologyTerrainSource terrain) {
        this.fine = Objects.requireNonNull(fine, "fine");
        this.terrain = Objects.requireNonNull(terrain, "terrain");
    }

    @Override
    public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
        if (baseOnly) {
            throw new IllegalArgumentException("The fine lattice holds natural heights only");
        }
        if (xs.length != zs.length) {
            throw new IllegalArgumentException("Fine lattice read has " + xs.length + " x and " + zs.length + " z coordinates");
        }
        int cell = fine.cell();
        int[] cellXs = new int[xs.length];
        int[] cellZs = new int[zs.length];
        for (int index = 0; index < xs.length; index++) {
            if (Math.floorMod(xs[index], cell) != 0 || Math.floorMod(zs[index], cell) != 0) {
                throw new IllegalArgumentException("Column " + xs[index] + "," + zs[index] + " is not a node of the " + cell + "-block fine lattice");
            }
            cellXs[index] = Math.floorDiv(xs[index], cell);
            cellZs[index] = Math.floorDiv(zs[index], cell);
        }
        fine.fill(cellXs, cellZs, out);
    }

    @Override
    public int seaLevel() {
        return terrain.seaLevel();
    }

    @Override
    public int minY() {
        return terrain.minY();
    }

    @Override
    public int maxY() {
        return terrain.maxY();
    }
}
