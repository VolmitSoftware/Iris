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
 * The layers of material on the ceilings of caves in a biome, written as {@code "caveCeilingLayers": [...]}, the list of layers.
 */
@Desc("Layers of materials placed on cave ceilings in this biome, indexed upward from the ceiling surface. Must not have more entries than layers, whose height generators it reuses. Omitting this leaves cave ceilings unchanged.")
@Feature(id = "iris:terrain/cave-ceiling-layers", name = "Cave ceiling layers", key = "caveCeilingLayers", root = "layers", targets = Level.BIOME)
public final class CaveCeilingLayersSettings implements IrisFeature {
    @Desc("Layers of materials placed on cave ceilings in this biome, indexed upward from the ceiling surface. Must not have more entries than layers, whose height generators it reuses. Omitting this leaves cave ceilings unchanged.")
    private List<PaletteLayer> layers = List.of();

    public CaveCeilingLayersSettings() {
    }

    public List<PaletteLayer> layers() {
        return layers == null ? List.of() : layers;
    }
}
