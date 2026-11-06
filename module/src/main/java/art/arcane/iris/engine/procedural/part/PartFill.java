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
package art.arcane.iris.engine.procedural.part;

/** How one part writes its blocks: the mode it combines with, and the density that thins it. */
record PartFill(PartCanvas canvas, PartMode mode, int role, int partId, double density, PartPattern pattern, double scale,
                double falloff, long seed) {
    static PartFill of(PartCanvas canvas, ProceduralPart part, int partId, int role, long seed) {
        return new PartFill(canvas, part.mode(), role, partId, part.density(), part.pattern(), part.scale(), part.falloff(), seed);
    }

    /**
     * Places the block if the density lets it; {@code edge} is how far toward the shape's edge it is, from 0 at the
     * center to 1 at the edge, for the falloff.
     */
    void place(int x, int y, int z, double edge, double along, PartCanvas.Axis axis) {
        double chance = density;
        if (falloff > 0) {
            chance *= Math.pow(Math.max(0, 1 - edge), falloff);
        }
        if (!PartNoise.passes(pattern, chance, scale, x, y, z, seed)) {
            return;
        }
        canvas.place(x, y, z, mode, new PartCanvas.Cell(role, (float) along, axis, partId));
    }
}
