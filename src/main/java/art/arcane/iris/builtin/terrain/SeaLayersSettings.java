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

import java.util.List;

/**
 * The layers of material filling the water column of a sea biome, written as {@code "seaLayers": [...]}, the list of layers.
 */
@Desc("Layers of materials filling the water column of sea biomes, indexed downward from the water surface. Anything below the last layer is filled with the dimension fluid palette, not stone.")
@Feature(id = "iris:terrain/sea-layers", name = "Sea layers", key = "seaLayers", root = "layers", targets = Level.BIOME)
public final class SeaLayersSettings implements IrisFeature {
    @Desc("Layers of materials filling the water column of sea biomes, indexed downward from the water surface. Anything below the last layer is filled with the dimension fluid palette, not stone.")
    private List<PaletteLayer> layers = List.of();

    public SeaLayersSettings() {
    }

    public List<PaletteLayer> layers() {
        return layers == null ? List.of() : layers;
    }
}
