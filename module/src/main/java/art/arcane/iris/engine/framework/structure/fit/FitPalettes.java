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

import com.volmit.block.BlockState;
import com.volmit.block.PaletteSpec;

/** Picks a block of a pack palette for the structure fit, seeded the way the fit's own randomness is. */
@FunctionalInterface
interface FitPalettes {
    /** The block {@code palette}, seeded with {@code seed}, gives at the world position. */
    BlockState at(PaletteSpec palette, long seed, int x, int y, int z);
}
