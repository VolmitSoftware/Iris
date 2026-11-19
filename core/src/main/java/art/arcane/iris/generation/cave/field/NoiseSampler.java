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

package art.arcane.iris.generation.cave.field;

/** Noise a cave field reads, built from one pack style and one seed. Safe for concurrent reads. */
public interface NoiseSampler {
    /** The noise at a column, from 0 to 1. */
    double sample(double worldX, double worldZ);

    /** The noise at a block, from 0 to 1. */
    double sample(double worldX, double y, double worldZ);

    /** The noise at a column, from -1 to 1. */
    default double signed(double worldX, double worldZ) {
        return sample(worldX, worldZ) * 2D - 1D;
    }

    /** The noise at a block, from -1 to 1. */
    default double signed(double worldX, double y, double worldZ) {
        return sample(worldX, y, worldZ) * 2D - 1D;
    }

    /**
     * This noise with its coordinates multiplied by {@code factor}: above 1 squeezes the pattern, below 1 stretches it.
     * The factor is folded into the generator's own scale, so a fracture still moves the noise in blocks.
     */
    NoiseSampler scaled(double factor);
}
