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

import art.arcane.iris.engine.procedural.IrisProceduralBlocks;
import art.arcane.iris.modules.objects.ObjectBuilder;
import com.volmit.block.PaletteSpec;
import com.volmit.block.BlockState;

/** A block or a noise palette to take one from, optionally banded by height for strata. */
record BlockChoice(String block, PaletteSpec palette, int bands) {
    /** The block at the position; null when neither block nor palette names one. */
    BlockState at(ObjectBuilder blocks, long seed, int x, int y, int z) {
        if (IrisProceduralBlocks.paletteSet(palette)) {
            if (bands > 0) {
                int band = Math.floorDiv(y, bands);
                return blocks.pick(palette, seed + band * 31L, x, band, z);
            }
            return blocks.pick(palette, seed, x, y, z);
        }
        if (block == null || block.isEmpty()) {
            return null;
        }
        return blocks.block(block);
    }

    boolean isSet() {
        return IrisProceduralBlocks.paletteSet(palette) || (block != null && !block.isEmpty());
    }
}
