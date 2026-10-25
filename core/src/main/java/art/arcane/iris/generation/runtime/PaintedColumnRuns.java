package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.BoundaryColumnGeometry;

import java.util.Arrays;
import java.util.List;

final class PaintedColumnRuns {
    static final short GROUND = 0;
    static final short AIR = 1;
    static final short FLUID = 2;
    private static final List<BoundaryColumnGeometry.Voxel> PALETTE = List.of(
            new BoundaryColumnGeometry.Voxel("minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false),
            new BoundaryColumnGeometry.Voxel("minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false),
            new BoundaryColumnGeometry.Voxel("minecraft:water", BoundaryColumnGeometry.Phase.FLUID, "minecraft:water", false));
    private static final ThreadLocal<PaintedColumnRuns> SCRATCH = ThreadLocal.withInitial(PaintedColumnRuns::new);

    private int[] ends = new int[0];
    private short[] voxels = new short[0];
    private int count;

    private PaintedColumnRuns() {
    }

    static PaintedColumnRuns start(int height) {
        PaintedColumnRuns runs = SCRATCH.get();
        if (runs.ends.length < height) {
            runs.ends = new int[height];
            runs.voxels = new short[height];
        }
        runs.count = 0;
        return runs;
    }

    void add(short voxel) {
        if (count > 0 && voxels[count - 1] == voxel) {
            ends[count - 1]++;
            return;
        }
        ends[count] = count == 0 ? 1 : ends[count - 1] + 1;
        voxels[count] = voxel;
        count++;
    }

    BoundaryColumnGeometry build(int minimumY) {
        return new BoundaryColumnGeometry(minimumY, PALETTE, Arrays.copyOf(ends, count), Arrays.copyOf(voxels, count));
    }
}
