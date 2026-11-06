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

package art.arcane.iris.modules.terrain;

import art.arcane.iris.builtin.terrain.PaletteLayer;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

/**
 * The slab post processing places on steps in a biome, written as {@code "slab": {...}}, one layer. A biome without the key has no slab.
 */
@Desc("The default slab if iris decides to place a slab in this biome. Default is no slab.")
@Feature(id = "iris:terrain/slab", name = "Slab", key = "slab", root = "layer", targets = Level.BIOME)
public final class SlabSettings implements IrisFeature {
    @Desc("The default slab if iris decides to place a slab in this biome. Default is no slab.")
    private PaletteLayer layer = PaletteLayer.withoutBlocks();

    public SlabSettings() {
    }

    public PaletteLayer layer() {
        return layer == null ? PaletteLayer.withoutBlocks() : layer;
    }
}
