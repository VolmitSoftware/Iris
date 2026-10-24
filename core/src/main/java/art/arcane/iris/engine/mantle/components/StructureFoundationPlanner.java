package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.spi.PlatformBlockState;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

final class StructureFoundationPlanner {
    static final int NO_GROUND = Integer.MIN_VALUE;

    private StructureFoundationPlanner() {
    }

    static void recordBaseCell(Long2IntOpenHashMap columns, int x, int y, int z,
                               PlatformBlockState state, boolean supportNonOccluding) {
        if (columns == null || state == null
                || !(supportNonOccluding ? state.isSolid() : state.isOccluding())) {
            return;
        }
        long columnKey = pack(x, z);
        if (!columns.containsKey(columnKey) || y < columns.get(columnKey)) {
            columns.put(columnKey, y);
        }
    }

    static int findGroundY(int foundationY, int maxDepth, int minimumY, IntPredicate solidAtY) {
        if (foundationY <= minimumY) {
            return NO_GROUND;
        }
        long requestedFloor = (long) foundationY - Math.max(1, maxDepth);
        int scanFloor = (int) Math.max(minimumY, requestedFloor);
        for (int y = foundationY - 1; y >= scanFloor; y--) {
            if (solidAtY.test(y)) {
                return y;
            }
        }
        return NO_GROUND;
    }

    static boolean isGroundSolid(PlatformBlockState overlay, boolean carved, int mantleY, int terrainHeight) {
        if (mantleY <= 0) {
            return true;
        }
        if (overlay != null) {
            return overlay.isSolid();
        }
        return !carved && mantleY <= terrainHeight;
    }

    static boolean isSurfaceSupportBoundary(PlatformBlockState overlay, boolean carved,
                                            int mantleY, int terrainHeight) {
        return carved || isGroundSolid(overlay, false, mantleY, terrainHeight);
    }

    static int fillSupportColumn(int foundationY, int groundY, IntConsumer supportAtY) {
        if (groundY == NO_GROUND || groundY >= foundationY - 1) {
            return 0;
        }
        int written = 0;
        for (int y = foundationY - 1; y > groundY; y--) {
            supportAtY.accept(y);
            written++;
        }
        return written;
    }

    static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    static int unpackZ(long packed) {
        return (int) packed;
    }
}
