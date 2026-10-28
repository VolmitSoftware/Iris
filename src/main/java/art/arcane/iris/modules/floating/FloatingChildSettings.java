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

package art.arcane.iris.modules.floating;

import art.arcane.iris.modules.carve.CarvingSettings;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MinItems;
import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.LinkedBiomes;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The floating child biomes one biome declares, written as {@code "floatingChildBiomes": [...]}, the list of
 * {@link FloatingChildBiome}s. The target biome of every child, and the biome its {@code carving} names, take part in the
 * pack's layout like any biome a dimension reaches.
 */
@Desc("Floating child biomes that procedurally generate above this biome's terrain. Each entry references a target biome whose layers, decorators, and objects drive the floating island's visual design, while the config here drives size, shape, altitude, rarity, and water level. Multiple entries are supported and selected by rarity per column unless mergeFloatingChildBiomes is enabled.")
@Feature(id = "iris:floating/children", name = "Floating child biomes", key = "floatingChildBiomes", root = "children", targets = Level.BIOME)
public final class FloatingChildSettings implements IrisFeature, LinkedBiomes {
    @MinItems(1)
    @Desc("Floating child biomes that procedurally generate above this biome's terrain. Each entry references a target biome whose layers, decorators, and objects drive the floating island's visual design, while the config here drives size, shape, altitude, rarity, and water level. Multiple entries are supported and selected by rarity per column unless mergeFloatingChildBiomes is enabled.")
    private List<FloatingChildBiome> children = List.of();

    public FloatingChildSettings() {
    }

    public FloatingChildSettings(List<FloatingChildBiome> children) {
        this.children = List.copyOf(children);
    }

    public List<FloatingChildBiome> children() {
        return children == null ? List.of() : Collections.unmodifiableList(children);
    }

    @Override
    public Set<ResourceId> usedBiomes(PackResources pack, PackSettings dimension) {
        CarvingSettings carving = dimension.declared(CarvingSettings.class).dimension().orElse(null);
        Set<ResourceId> biomes = new LinkedHashSet<>();
        for (FloatingChildBiome child : children()) {
            if (child != null) {
                addBiome(pack, biomes, child.biome());
                addBiome(pack, biomes, carving == null ? child.carving() : carving.biomeOf(child.carving()));
            }
        }
        return biomes;
    }

    private static void addBiome(PackResources pack, Set<ResourceId> biomes, String key) {
        if (key != null && !key.isBlank()) {
            biomes.add(pack.idOf(key));
        }
    }
}
