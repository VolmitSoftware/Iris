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

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

/**
 * The wall post processing paints on steep faces, and caves paint on their walls, in a biome, written as {@code "wall": {...}}, one layer.
 * A biome without the key has no wall.
 */
@Desc("The default wall if iris decides to place a wall higher than 2 blocks (steep hills or possibly cliffs)")
@Feature(id = "iris:terrain/wall", name = "Wall", key = "wall", root = "layer", targets = Level.BIOME)
public final class WallSettings implements IrisFeature {
    @Desc("The default wall if iris decides to place a wall higher than 2 blocks (steep hills or possibly cliffs)")
    private PaletteLayer layer = PaletteLayer.withoutBlocks();

    public WallSettings() {
    }

    public PaletteLayer layer() {
        return layer == null ? PaletteLayer.withoutBlocks() : layer;
    }
}
