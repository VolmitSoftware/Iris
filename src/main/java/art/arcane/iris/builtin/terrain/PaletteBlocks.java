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
package art.arcane.iris.builtin.terrain;

import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;

import java.util.ArrayList;
import java.util.List;

/** A palette's blocks, the way terrain layers and palettes always picked from them. */
final class PaletteBlocks {
    private PaletteBlocks() {
    }

    /** The block of each spec, each repeated by its weight. */
    static List<BlockState> weighted(BlockRegistry registry, List<BlockSpec> palette) {
        List<BlockState> weighted = new ArrayList<>();
        for (BlockSpec spec : palette) {
            BlockState state = registry.resolve(spec);
            for (int n = 0; n < spec.weight(); n++) {
                weighted.add(state);
            }
        }
        return List.copyOf(weighted);
    }
}
