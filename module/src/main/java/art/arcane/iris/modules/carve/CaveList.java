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

package art.arcane.iris.modules.carve;

import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.Level;
import com.volmit.config.PackSettings;
import com.volmit.config.ResourceRef;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.LinkedBiomes;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The caves a dimension, region or biome lists under {@code caves}, by their keys in the pack's {@code caves/} folder.
 * The lists of a column's dimension, region and biomes add up: every cave any of them lists carves there.
 */
@Desc("Caves that carve here, by their file in the pack's caves folder. Dimension, region and biome lists add up.")
@Feature(id = "iris:carve/cave-list", name = "Caves", key = "caves", root = "caves", targets = {Level.DIMENSION, Level.REGION, Level.BIOME})
public final class CaveList implements IrisFeature, LinkedBiomes {
    @ResourceRef(Cave.class)
    @Desc("Caves that carve here, by their file in the pack's caves folder. Dimension, region and biome lists add up.")
    private List<String> caves = List.of();

    public CaveList() {
    }

    public CaveList(List<String> caves) {
        this.caves = List.copyOf(caves);
    }

    public List<String> caves() {
        return caves == null ? List.of() : Collections.unmodifiableList(caves);
    }

    @Override
    public Set<ResourceId> usedBiomes(PackResources pack, PackSettings dimension) {
        Set<ResourceId> biomes = new LinkedHashSet<>();
        for (String key : caves()) {
            Cave cave = CaveResources.cave(pack, key);
            if (cave != null && !cave.biome().isBlank()) {
                biomes.add(pack.idOf(cave.biome()));
            }
        }
        return biomes;
    }
}
