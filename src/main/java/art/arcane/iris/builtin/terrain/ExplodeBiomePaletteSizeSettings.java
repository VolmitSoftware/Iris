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

import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;

/** How many barrier blocks a dimension puts between layers when it explodes its biome palettes, written as {@code "explodeBiomePaletteSize": 3}. */
@Desc("Customize the palette height explosion")
@Feature(id = "iris:terrain/explode-palette-size", name = "Explode biome palette size", key = "explodeBiomePaletteSize", root = "size", targets = Target.DIMENSION)
public final class ExplodeBiomePaletteSizeSettings implements IrisFeature {
    public static final int DEFAULT_SIZE = 3;

    @MinNumber(1)
    @MaxNumber(16)
    @Desc("Customize the palette height explosion")
    private int size = DEFAULT_SIZE;

    public ExplodeBiomePaletteSizeSettings() {
    }

    public int size() {
        return size;
    }
}
