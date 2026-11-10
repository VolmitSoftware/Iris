package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.framework.structure.ColumnHeights;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

final class StructureFoundationPlanner {
    static final int NO_GROUND = Integer.MIN_VALUE;

    private StructureFoundationPlanner() {
    }

    static void recordBaseCell(ColumnHeights columns, int x, int y, int z,
                               BlockState state, BlockRegistry blocks, boolean supportNonOccluding) {
        if (columns == null || state == null
                || !blocks.hasTrait(state, supportNonOccluding ? BlockTrait.SOLID : BlockTrait.OCCLUDING)) {
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

    static boolean isGroundSolid(BlockState overlay, BlockRegistry blocks, boolean carved, int mantleY,
                                 int terrainHeight) {
        if (mantleY <= 0) {
            return true;
        }
        if (overlay != null) {
            return blocks.hasTrait(overlay, BlockTrait.SOLID);
        }
        return !carved && mantleY <= terrainHeight;
    }

    static boolean isSurfaceSupportBoundary(BlockState overlay, BlockRegistry blocks, boolean carved,
                                            int mantleY, int terrainHeight) {
        return carved || isGroundSolid(overlay, blocks, false, mantleY, terrainHeight);
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
