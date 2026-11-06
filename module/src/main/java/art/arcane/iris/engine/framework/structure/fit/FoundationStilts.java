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

package art.arcane.iris.engine.framework.structure.fit;

import art.arcane.iris.builtin.structure.StructureStilts;
import com.volmit.block.BlockState;
import com.volmit.block.PaletteSpec;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntBinaryOperator;

/**
 * Foundation columns under a placed start: in every column of the start's foundation envelope (the pieces' lowest
 * layers up to one above their ground), from the lowest solid block placed there down to the first block that can
 * carry it, the terrain's surface for a surface structure, at most {@code maxDepth} blocks deep. Sparse stilts keep
 * only the columns on their spacing grid.
 *
 * <p>These are the vanilla structures' stilts. Objects grow theirs from their own bottom layer instead
 * ({@code iris:stilt}): repeated object blocks, eroded or organic tips and a ceiling hang have no counterpart here, and
 * these columns have no counterpart there, so the two stay separate.
 */
final class FoundationStilts {
    private static final int FOUNDATION_VERTICAL_TOLERANCE = 1;

    private FoundationStilts() {
    }

    static void placeStilts(FitBlocks blocks, Box area, FitStart start, StructureStilts settings, long seed,
                            FitPalettes palettes, IntBinaryOperator surfaceHeight, boolean surfaceStructure) {
        Objects.requireNonNull(surfaceHeight, "Structure stilts require an Iris terrain height resolver");
        Optional<PaletteSpec> palette = settings.palette();
        long paletteSeed = seed ^ start.structureId().hashCode();
        for (FoundationColumn column : foundationEnvelope(area, start)) {
            if (!isStiltColumn(column.x(), column.z(), settings.spacing())) {
                continue;
            }
            int foundationY = findFoundationY(blocks, column);
            if (foundationY == Integer.MIN_VALUE) {
                continue;
            }
            int terrainY = surfaceStructure
                    ? Math.max(area.minY(), Math.min(area.maxY(), surfaceHeight.applyAsInt(column.x(), column.z())))
                    : area.minY() - 1;
            int anchorY = findStiltAnchorY(blocks, column.x(), column.z(), foundationY,
                    Math.max(1, settings.maxDepth()), terrainY, area.minY());
            if (anchorY == Integer.MIN_VALUE) {
                continue;
            }
            for (int y = foundationY - 1; y > anchorY; y--) {
                BlockState stilt = palette.isEmpty()
                        ? FitBlocks.COBBLESTONE
                        : Objects.requireNonNull(palettes.at(palette.get(), paletteSeed, column.x(), y, column.z()),
                                "Stilt palette returned no block for " + start.structureId() + " at "
                                        + column.x() + "," + y + "," + column.z());
                blocks.set(column.x(), y, column.z(), stilt);
            }
        }
    }

    static boolean isStiltColumn(int x, int z, int spacing) {
        int resolvedSpacing = Math.max(1, spacing);
        return resolvedSpacing == 1 || Math.floorMod(x, resolvedSpacing) == 0 && Math.floorMod(z, resolvedSpacing) == 0;
    }

    static int findStiltAnchorY(FitBlocks blocks, int x, int z, int foundationY, int maxDepth, int terrainY,
                                int areaMinY) {
        int minimumAnchorY = Math.max(areaMinY, Math.max(terrainY, foundationY - maxDepth - 1));
        for (int y = foundationY - 1; y >= minimumAnchorY; y--) {
            BlockState state = blocks.get(x, y, z);
            if (!blocks.isTaggedVegetation(state) && blocks.sturdyTop(x, y, z)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static List<FoundationColumn> foundationEnvelope(Box area, FitStart start) {
        Box structure = start.contentBounds();
        int minX = Math.max(area.minX(), structure.minX());
        int minZ = Math.max(area.minZ(), structure.minZ());
        int maxX = Math.min(area.maxX(), structure.maxX());
        int maxZ = Math.min(area.maxZ(), structure.maxZ());
        if (minX > maxX || minZ > maxZ) {
            return List.of();
        }
        List<FoundationColumn> columns = new ArrayList<>((maxX - minX + 1) * (maxZ - minZ + 1));
        BitSet envelope = new BitSet(area.ySpan());
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                envelope.clear();
                markFoundationEnvelope(envelope, start, area, x, z);
                int cellCount = envelope.cardinality();
                if (cellCount == 0) {
                    continue;
                }
                int[] ys = new int[cellCount];
                int cell = 0;
                for (int bit = envelope.nextSetBit(0); bit >= 0; bit = envelope.nextSetBit(bit + 1)) {
                    ys[cell++] = area.minY() + bit;
                }
                columns.add(new FoundationColumn(x, z, ys));
            }
        }
        return List.copyOf(columns);
    }

    private static void markFoundationEnvelope(BitSet envelope, FitStart start, Box area, int x, int z) {
        for (FitPiece piece : start.pieces()) {
            Box bounds = piece.box();
            if (x < bounds.minX() || x > bounds.maxX() || z < bounds.minZ() || z > bounds.maxZ()) {
                continue;
            }
            int groundY = piece.groundY();
            if (piece.isJigsaw() && groundY < bounds.minY()) {
                continue;
            }
            int minY = Math.max(area.minY(), bounds.minY());
            int maxY = Math.min(area.maxY(), Math.min(bounds.maxY(), groundY + FOUNDATION_VERTICAL_TOLERANCE));
            if (minY <= maxY) {
                envelope.set(minY - area.minY(), maxY - area.minY() + 1);
            }
        }
    }

    private static int findFoundationY(FitBlocks blocks, FoundationColumn column) {
        for (int y : column.ys()) {
            if (blocks.isSolid(blocks.get(column.x(), y, column.z()))) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private record FoundationColumn(int x, int z, int[] ys) {
    }
}
