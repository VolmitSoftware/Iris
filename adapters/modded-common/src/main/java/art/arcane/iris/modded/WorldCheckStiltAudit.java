/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modded;

import art.arcane.iris.nativegen.NativeStructureReferenceEnvelope;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.function.IntBinaryOperator;

/**
 * The world check's reading of a placed structure's stilts: per column of the structure's foundation (each piece's
 * ground level and the block above it), whether the lowest solid foundation block stands on terrain, on stilts of the
 * expected block reaching terrain, or on nothing. It reads the generated world, independently of the fit that built
 * the stilts.
 */
final class WorldCheckStiltAudit {
    private static final int FOUNDATION_VERTICAL_TOLERANCE = 1;

    private WorldCheckStiltAudit() {
    }

    static Result audit(WorldGenLevel world, BoundingBox area, StructureStart start, BlockState expectedStilt,
                        IntBinaryOperator surfaceHeight) {
        BoundingBox structure = NativeStructureReferenceEnvelope.contentBounds(start);
        int minX = Math.max(area.minX(), structure.minX());
        int minZ = Math.max(area.minZ(), structure.minZ());
        int maxX = Math.min(area.maxX(), structure.maxX());
        int maxZ = Math.min(area.maxZ(), structure.maxZ());
        Result result = Result.NONE;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                int foundationY = foundationY(world, area, start, x, z);
                if (foundationY != Integer.MIN_VALUE) {
                    int terrainY = Math.max(area.minY(), Math.min(area.maxY(), surfaceHeight.applyAsInt(x, z)));
                    result = result.plus(column(world, area, x, z, foundationY, terrainY, expectedStilt));
                }
            }
        }
        return result;
    }

    private static Result column(WorldGenLevel world, BoundingBox area, int x, int z, int foundationY, int terrainY,
                                 BlockState expectedStilt) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        boolean grounded = foundationY <= terrainY + 1;
        int stiltBlocks = 0;
        for (int y = foundationY - 1; y >= area.minY(); y--) {
            if (y <= terrainY) {
                grounded = true;
                break;
            }
            BlockState state = world.getBlockState(position.set(x, y, z));
            if (state.is(expectedStilt.getBlock())) {
                stiltBlocks++;
                grounded |= y == area.minY();
                continue;
            }
            grounded = state.isSolid() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES);
            break;
        }
        return new Result(1, stiltBlocks, stiltBlocks > 0 ? 1 : 0, grounded ? 0 : 1);
    }

    /** The lowest solid block of the column inside a piece's ground level band; MIN_VALUE when there is none. */
    private static int foundationY(WorldGenLevel world, BoundingBox area, StructureStart start, int x, int z) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int lowest = Integer.MIN_VALUE;
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox bounds = piece.getBoundingBox();
            if (x < bounds.minX() || x > bounds.maxX() || z < bounds.minZ() || z > bounds.maxZ()) {
                continue;
            }
            int groundY = bounds.minY() + (piece instanceof PoolElementStructurePiece poolPiece
                    ? poolPiece.getGroundLevelDelta() : 0);
            int minY = Math.max(area.minY(), bounds.minY());
            int maxY = Math.min(area.maxY(), Math.min(bounds.maxY(), groundY + FOUNDATION_VERTICAL_TOLERANCE));
            if (groundY < bounds.minY()) {
                continue;
            }
            for (int y = minY; y <= maxY && (lowest == Integer.MIN_VALUE || y < lowest); y++) {
                if (world.getBlockState(position.set(x, y, z)).isSolid()) {
                    lowest = y;
                    break;
                }
            }
        }
        return lowest;
    }

    record Result(int baseColumns, int stiltBlocks, int stiltColumns, int unsupportedColumns) {
        static final Result NONE = new Result(0, 0, 0, 0);

        Result plus(Result other) {
            return new Result(baseColumns + other.baseColumns, stiltBlocks + other.stiltBlocks,
                    stiltColumns + other.stiltColumns, unsupportedColumns + other.unsupportedColumns);
        }
    }
}
