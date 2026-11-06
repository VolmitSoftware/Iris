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

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.block.BlockSpec;
import com.volmit.block.PaletteSpec;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

import java.util.List;

/**
 * The blocks that fill terrain below the layers of a dimension, written as {@code "rockPalette": {...}}, a palette. A dimension without the key is stone.
 */
@Desc("The palette of blocks for 'stone'")
@Feature(id = "iris:terrain/rock-palette", name = "Rock palette", key = "rockPalette", root = "palette", targets = Level.DIMENSION)
public final class RockPaletteSettings implements IrisFeature {
    private static final String DEFAULT_BLOCK = "stone";

    @Desc("The palette of blocks for 'stone'")
    private PaletteSpec palette = PaletteSpec.of(List.of(BlockSpec.of(DEFAULT_BLOCK)));

    public RockPaletteSettings() {
    }

    public PaletteSpec palette() {
        return palette == null ? PaletteSpec.of(List.of(BlockSpec.of(DEFAULT_BLOCK))) : palette;
    }
}
