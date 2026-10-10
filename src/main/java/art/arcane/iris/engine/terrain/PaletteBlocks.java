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

package art.arcane.iris.engine.terrain;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;

import java.util.ArrayList;
import java.util.List;

/** A palette's blocks as the platform's, the way terrain layers and palettes always picked from them. */
final class PaletteBlocks {
    private PaletteBlocks() {
    }

    /** The platform's block of each spec that has one, each repeated by its weight. */
    static List<PlatformBlockState> weighted(BlockRegistry registry, List<BlockSpec> palette) {
        List<PlatformBlockState> weighted = new ArrayList<>();
        for (BlockSpec spec : palette) {
            PlatformBlockState state = B.getState(registry.resolve(spec).key());
            if (state == null) {
                continue;
            }
            for (int n = 0; n < spec.weight(); n++) {
                weighted.add(state);
            }
        }
        return List.copyOf(weighted);
    }
}
