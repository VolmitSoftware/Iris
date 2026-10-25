/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.map;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * The visible faces of noise terrain as columns, with neighbouring columns merged into one face where they show the
 * same stretch: tops of one height along X, and sides of one height range along the side. A face runs {@code columns}
 * columns from its first one and reaches {@code height} across: one column along Z for tops, blocks up for sides.
 * <p>
 * Hole walls follow the surface: a side from the floor up for each column next to where a {@link TerrainHole} may end,
 * shown in place of the usual side while the column beside it is left out. No face crosses a hole's cell edge, so one
 * set of faces serves every hole.
 * <p>
 * Faces depend on neither the camera nor the hole, so they are found once per terrain rather than every frame.
 */
final class BlockFaces {
    private static final int INITIAL_CAPACITY = 1024;
    private static final Facing[] FACINGS = Facing.values();
    private static final Kind[] KINDS = Kind.values();

    private final NoiseTerrain terrain;
    private final int floorY;
    // Faces are kept kind by kind, and facing by facing within a kind.
    private final int[] firstOfGroup = new int[KINDS.length * FACINGS.length + 1];
    private int count;
    private int[] firstColumn = new int[INITIAL_CAPACITY];
    private int[] firstRow = new int[INITIAL_CAPACITY];
    private int[] bottomY = new int[INITIAL_CAPACITY];
    private int[] columns = new int[INITIAL_CAPACITY];
    private int[] height = new int[INITIAL_CAPACITY];
    private byte[] facing = new byte[INITIAL_CAPACITY];

    private BlockFaces(NoiseTerrain terrain, int floorY) {
        this.terrain = terrain;
        this.floorY = floorY;
    }

    /**
     * @param floorY where sides on the edge, next to a gap or into a hole end
     */
    static BlockFaces of(NoiseTerrain terrain, int floorY) {
        BlockFaces faces = new BlockFaces(terrain, floorY);
        for (Kind kind : KINDS) {
            for (Facing facing : FACINGS) {
                faces.firstOfGroup[group(kind, facing)] = faces.count;
                faces.add(kind, facing);
            }
        }
        faces.firstOfGroup[KINDS.length * FACINGS.length] = faces.count;
        faces.trimToCount();
        return faces;
    }

    private static int group(Kind kind, Facing facing) {
        return kind.ordinal() * FACINGS.length + facing.ordinal();
    }

    private void add(Kind kind, Facing facing) {
        if (kind == Kind.HOLE_WALL) {
            addHoleWalls(facing);
        } else if (facing == Facing.TOP) {
            addTops();
        } else {
            addSides(facing);
        }
    }

    // A height of 64 places blocks up to y 64, so the column's top face is at 65.
    static int blockTop(double height) {
        return (int) Math.floor(height) + 1;
    }

    int count() {
        return count;
    }

    // All faces of one kind and facing follow on from the first.
    int first(Kind kind, Facing facing) {
        return firstOfGroup[group(kind, facing)];
    }

    int count(Kind kind, Facing facing) {
        return firstOfGroup[group(kind, facing) + 1] - firstOfGroup[group(kind, facing)];
    }

    boolean isHoleWall(int face) {
        return face >= first(Kind.HOLE_WALL, FACINGS[0]);
    }

    int bottomY(int face) {
        return bottomY[face];
    }

    Facing facing(int face) {
        return FACINGS[facing[face]];
    }

    int columns(int face) {
        return columns[face];
    }

    int height(int face) {
        return height[face];
    }

    // The column and row of the face's first column; the others follow along its run.
    int firstColumn(int face) {
        return firstColumn[face];
    }

    int firstRow(int face) {
        return firstRow[face];
    }

    // The corner the face runs and reaches from; east and south sides lie on the far edge of their column.
    double originX(int face) {
        return terrain.worldX(firstColumn[face] + (facing(face).normalX > 0 ? 1 : 0));
    }

    double originY(int face) {
        return bottomY[face];
    }

    double originZ(int face) {
        return terrain.worldZ(firstRow[face] + (facing(face).normalZ > 0 ? 1 : 0));
    }

    double runX(int face) {
        return facing(face).runsAlongX() ? (double) columns[face] * terrain.step() : 0D;
    }

    double runZ(int face) {
        return facing(face).runsAlongX() ? 0D : (double) columns[face] * terrain.step();
    }

    double reachY(int face) {
        return facing(face) == Facing.TOP ? 0D : height[face];
    }

    double reachZ(int face) {
        return facing(face) == Facing.TOP ? (double) height[face] * terrain.step() : 0D;
    }

    private boolean hasColumn(int column, int row) {
        return terrain.hasHeight(column, row);
    }

    private static boolean startsCell(int columnOrRow) {
        return columnOrRow % TerrainHole.CELL_COLUMNS == 0;
    }

    private void addTops() {
        for (int row = 0; row < terrain.rows(); row++) {
            int column = 0;
            while (column < terrain.columns()) {
                if (!hasColumn(column, row)) {
                    column++;
                    continue;
                }
                int top = blockTop(terrain.height(column, row));
                int runEnd = column + 1;
                while (runEnd < terrain.columns() && !startsCell(runEnd) && hasColumn(runEnd, row)
                        && blockTop(terrain.height(runEnd, row)) == top) {
                    runEnd++;
                }
                add(Facing.TOP, column, row, top, runEnd - column, 1);
                column = runEnd;
            }
        }
    }

    private void addSides(Facing side) {
        for (int lane = 0; lane < lanes(side); lane++) {
            int sideLane = lane;
            addSideRuns(side, lane, step -> sideBottom(side, sideLane, step));
        }
    }

    // A hole only ends on the lines between cells, so walls only stand along those inside the terrain.
    private void addHoleWalls(Facing side) {
        if (side == Facing.TOP) {
            return;
        }
        int towardNeighbour = side.runsAlongX() ? side.normalZ : side.normalX;
        for (int lane = 0; lane < lanes(side); lane++) {
            int neighbourLane = lane + towardNeighbour;
            boolean facesNextCell = lane / TerrainHole.CELL_COLUMNS != neighbourLane / TerrainHole.CELL_COLUMNS;
            if (neighbourLane >= 0 && neighbourLane < lanes(side) && facesNextCell) {
                addSideRuns(side, lane, step -> floorY);
            }
        }
    }

    private int lanes(Facing side) {
        return side.runsAlongX() ? terrain.rows() : terrain.columns();
    }

    private void addSideRuns(Facing side, int lane, IntUnaryOperator bottomAt) {
        int steps = side.runsAlongX() ? terrain.columns() : terrain.rows();
        int step = 0;
        while (step < steps) {
            int top = sideTop(side, lane, step);
            int bottom = bottomAt.applyAsInt(step);
            if (bottom >= top) {
                step++;
                continue;
            }
            int runEnd = step + 1;
            while (runEnd < steps && !startsCell(runEnd) && sideTop(side, lane, runEnd) == top && bottomAt.applyAsInt(runEnd) == bottom) {
                runEnd++;
            }
            add(side, side.column(lane, step), side.row(lane, step), bottom, runEnd - step, top - bottom);
            step = runEnd;
        }
    }

    // No column means no side, which the caller sees as a side with no height.
    private int sideTop(Facing side, int lane, int step) {
        int column = side.column(lane, step);
        int row = side.row(lane, step);
        return hasColumn(column, row) ? blockTop(terrain.height(column, row)) : Integer.MIN_VALUE;
    }

    // Columns on the edge, or next to a gap, show their side all the way down to the floor.
    private int sideBottom(Facing side, int lane, int step) {
        int column = side.column(lane, step) + side.normalX;
        int row = side.row(lane, step) + side.normalZ;
        return hasColumn(column, row) ? blockTop(terrain.height(column, row)) : floorY;
    }

    private void add(Facing faceFacing, int column, int row, int bottom, int runColumns, int reach) {
        if (count == facing.length) {
            grow();
        }
        firstColumn[count] = column;
        firstRow[count] = row;
        bottomY[count] = bottom;
        columns[count] = runColumns;
        height[count] = reach;
        facing[count] = (byte) faceFacing.ordinal();
        count++;
    }

    private void grow() {
        resizeTo(facing.length * 2);
    }

    // Large terrain keeps millions of faces, so the room left over from growing would be a lot of memory.
    private void trimToCount() {
        resizeTo(count);
    }

    private void resizeTo(int capacity) {
        firstColumn = Arrays.copyOf(firstColumn, capacity);
        firstRow = Arrays.copyOf(firstRow, capacity);
        bottomY = Arrays.copyOf(bottomY, capacity);
        columns = Arrays.copyOf(columns, capacity);
        height = Arrays.copyOf(height, capacity);
        facing = Arrays.copyOf(facing, capacity);
    }

    /**
     * Which way a face looks. Tops and north and south sides run along X; east and west sides run along Z.
     */
    enum Facing {
        TOP(0, 1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);

        final int normalX;
        final int normalY;
        final int normalZ;

        Facing(int normalX, int normalY, int normalZ) {
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
        }

        boolean runsAlongX() {
            return normalX == 0;
        }

        // A lane is a row for faces running along X and a column for the others; steps go along the lane.
        int column(int lane, int step) {
            return runsAlongX() ? step : lane;
        }

        int row(int lane, int step) {
            return runsAlongX() ? lane : step;
        }
    }

    // Surface faces show unless their column is in a hole; hole walls only beside one.
    enum Kind {
        SURFACE,
        HOLE_WALL
    }
}
