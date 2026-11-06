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
package art.arcane.iris.builtin.anchor;

/** How a place mode bends the terrain around the object to meet its base; NONE leaves the terrain alone. */
public enum VacuumProfile {
    NONE(0, 1, Edge.PARABOLIC),
    STANDARD(12, 1, Edge.PARABOLIC),
    HIGH(20, 1, Edge.PARABOLIC),
    FAST(8, 2, Edge.PARABOLIC),
    ORGANIC(12, 1, Edge.JITTERED),
    WAVY(12, 1, Edge.WAVY);

    /** The shape of the bend's outer edge. */
    public enum Edge {
        PARABOLIC,
        /** The radius perturbed per column. */
        JITTERED,
        /** The bend rolled by smooth noise. */
        WAVY
    }

    private final int defaultRadiusBlocks;
    private final int columnStepBlocks;
    private final Edge edge;

    VacuumProfile(int defaultRadiusBlocks, int columnStepBlocks, Edge edge) {
        this.defaultRadiusBlocks = defaultRadiusBlocks;
        this.columnStepBlocks = columnStepBlocks;
        this.edge = edge;
    }

    /** How far the bend reaches when the vacuum settings leave the radius open. */
    public int defaultRadiusBlocks() {
        return defaultRadiusBlocks;
    }

    /** Every how many columns the bend samples the terrain. */
    public int columnStepBlocks() {
        return columnStepBlocks;
    }

    public Edge edge() {
        return edge;
    }
}
