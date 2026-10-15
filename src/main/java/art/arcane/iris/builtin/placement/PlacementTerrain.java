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

package art.arcane.iris.builtin.placement;

import com.volmit.block.BlockState;

/**
 * What a placement asks of the terrain and the world around it: heights, carving and water at a column, and the
 * world's own facts. A placement made without a world ({@link #hasWorld()} is false) answers its terrain questions from
 * the placer alone and the world's facts with nothing.
 */
public interface PlacementTerrain {
    /** The surface height of the column, counting fluid unless {@code ignoreFluid}. */
    int highest(int x, int z, boolean ignoreFluid);

    boolean isCarved(int x, int y, int z);

    /** Whether the block is real, solid ground; the height field alone cannot say at mantle time. */
    boolean isSurfaceSolid(int x, int y, int z);

    boolean isUnderwater(int x, int z);

    /** The height up to which the dimension is filled with fluid. */
    int fluidHeight();

    /** Whether leaves the placement writes never decay. */
    boolean preventsDecay();

    /** Whether bored air is written as a visible block, to see where smart boring bored. */
    boolean debugsSmartBore();

    boolean hasWorld();

    /** The terrain's slope at the column, as the dimension's slope field gives it. */
    double slopeAt(int x, int z);

    /** The terrain height at a position between columns, as the dimension's height field gives it. */
    double heightAt(double x, double z);

    /** Whether the dimension has a bedrock floor. */
    boolean hasBedrock();

    /** The real Y of the dimension's bottom. */
    int minHeight();

    /** The dimension's height in blocks. */
    int worldHeight();

    /** The rock the dimension has at the column; air when it has none to say. */
    BlockState rockAt(int x, int z);

    StructureVolumes structureVolumes(int minX, int minZ, int maxX, int maxZ);

    /** The pack's key of the object placed at the block, or null when none was. */
    String objectKeyAt(int x, int y, int z);
}
