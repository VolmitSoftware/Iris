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

/** Whether a dimension puts barrier blocks between the layers of its biomes to show them apart, written as {@code "explodeBiomePalettes": true}. */
@Desc("Vertically split up the biome palettes with barrier blocks in between to visualize them. The gap size comes from explodeBiomePaletteSize.")
@Feature(id = "iris:terrain/explode-palettes", name = "Explode biome palettes", key = "explodeBiomePalettes", root = "explode", targets = Level.DIMENSION)
public final class ExplodeBiomePalettesSettings implements IrisFeature {
    @Desc("Vertically split up the biome palettes with barrier blocks in between to visualize them. The gap size comes from explodeBiomePaletteSize.")
    private boolean explode = false;

    public ExplodeBiomePalettesSettings() {
    }

    public boolean explode() {
        return explode;
    }
}
