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

package art.arcane.iris.builtin.structure;

import com.volmit.tooling.CommandContext;

/**
 * What Iris's running worlds offer {@code iris:structure}'s {@code verify} command: whether each structure can generate
 * in the world, which needs the world's native structure policy and biome reachability that only Iris itself reads.
 * Ask for it with {@code world.extension(StructureVerifier.class)}.
 */
public interface StructureVerifier {
    /** Reports to the sender of {@code context}, for every structure the platform registers and every structure the pack places, whether it can generate within {@code radiusChunks} of the world origin. */
    void verify(CommandContext context, int radiusChunks);
}
