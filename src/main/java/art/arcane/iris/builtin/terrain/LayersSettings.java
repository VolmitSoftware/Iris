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

import java.util.List;

/**
 * The layers of material a biome is made of below its surface, written as {@code "layers": [...]}, the list of layers. A biome
 * that declares none gets one layer of grass, so the settings class default is what a biome without the key has.
 */
@Desc("This defines the layers of materials in this biome. Each layer has a palette and min/max height and some other properties. Usually a grassy/sandy layer then a dirt layer then a stone layer. Iris will fill in the remaining blocks below your layers with stone.")
@Feature(id = "iris:terrain/layers", name = "Layers", key = "layers", root = "layers", targets = Target.BIOME)
public final class LayersSettings implements IrisFeature {
    @Desc("This defines the layers of materials in this biome. Each layer has a palette and min/max height and some other properties. Usually a grassy/sandy layer then a dirt layer then a stone layer. Iris will fill in the remaining blocks below your layers with stone.")
    private List<PaletteLayer> layers = List.of(new PaletteLayer());

    public LayersSettings() {
    }

    public List<PaletteLayer> layers() {
        return layers == null ? List.of() : layers;
    }
}
