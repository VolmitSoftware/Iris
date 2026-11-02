package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

final class ObjectHydrologyVeto {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final ObjectHydrologyVeto NONE = new ObjectHydrologyVeto(null, null);

    private final IrisComplex complex;
    private final HydrologyRuntime runtime;

    private ObjectHydrologyVeto(IrisComplex complex, HydrologyRuntime runtime) {
        this.complex = complex;
        this.runtime = runtime;
    }

    static ObjectHydrologyVeto of(Engine engine) {
        IrisComplex complex = engine == null ? null : engine.getComplex();
        HydrologyRuntime runtime = complex == null ? null : complex.getHydrologyRuntime();
        return runtime != null && runtime.enabled() ? new ObjectHydrologyVeto(complex, runtime) : NONE;
    }

    boolean keepsBlock(int x, int y, int z, NativeBlockState state, boolean entersWater) {
        NativeBlockState base = state.isCustom() && state.placementBaseState() != null ? state.placementBaseState() : state;
        return keeps(x, y, z, base.isSolid(), entersWater);
    }

    boolean keeps(int x, int y, int z, boolean solid, boolean entersWater) {
        if (runtime == null) {
            return false;
        }
        ChunkWaterRaster raster = runtime.raster(x >> 4, z >> 4);
        if (raster.empty()) {
            return false;
        }
        WaterRole role = raster.role(x & 15, z & 15);
        if (role == WaterRole.INLET && y >= raster.waterHead(x & 15, z & 15) && y <= complex.naturalTrueHeight(x, z)) {
            return true;
        }
        if (solid && !entersWater && fillsWater(x, y, z)) {
            return true;
        }
        if (role.wet()) {
            int surface = raster.waterHead(x & 15, z & 15);
            int fallTop = highestHead(raster, x, z);
            if (fallTop > surface && y >= surface && y <= fallTop) {
                return true;
            }
            return !solid && y >= raster.terrainHeight(x & 15, z & 15) && y <= surface;
        }
        if (solid || role == WaterRole.NONE) {
            return false;
        }
        int head = Integer.MIN_VALUE;
        int bed = Integer.MAX_VALUE;
        for (int[] side : SIDES) {
            int nx = x + side[0];
            int nz = z + side[1];
            ChunkWaterRaster neighbour = neighbour(raster, x, z, nx, nz);
            if (neighbour.role(nx & 15, nz & 15).wet()) {
                head = Math.max(head, neighbour.waterHead(nx & 15, nz & 15));
                bed = Math.min(bed, neighbour.terrainHeight(nx & 15, nz & 15));
            }
        }
        return y > bed && y <= head;
    }

    private boolean fillsWater(int x, int y, int z) {
        if (!complex.getWaterRoleStream().get(x, z).wet()) {
            return false;
        }
        return y > complex.getRoundedHeighteightStream().get(x, z)
                && y <= (int) Math.round(complex.getWaterHeadStream().getDouble(x, z));
    }

    private int highestHead(ChunkWaterRaster raster, int x, int z) {
        int top = Integer.MIN_VALUE;
        for (int[] side : SIDES) {
            int nx = x + side[0];
            int nz = z + side[1];
            top = Math.max(top, neighbour(raster, x, z, nx, nz).waterHead(nx & 15, nz & 15));
        }
        return top;
    }

    private ChunkWaterRaster neighbour(ChunkWaterRaster raster, int x, int z, int nx, int nz) {
        return (nx >> 4) == (x >> 4) && (nz >> 4) == (z >> 4) ? raster : runtime.raster(nx >> 4, nz >> 4);
    }
}
