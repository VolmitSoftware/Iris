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

package art.arcane.iris.engine.object.terrain.carving;

import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.util.HashMap;
import java.util.Map;

/**
 * The settings of one type that a world's dimension, regions and biomes declare, found again by the id region and biome
 * samples carry ({@code overworld:desert/dunes}), which is how the layout names the region or biome of a column. A settings
 * entry names its source by kind ({@code overworld:biome/desert/dunes}), so the kind is taken off the id.
 *
 * <p>Only the regions and biomes the dimension reaches declare anything here ({@link com.volmit.config.PackSettings#declared}).
 */
public final class DeclaredByLevel<F extends IrisFeature> {
    private final F dimension;
    private final Map<ResourceId, F> regions;
    private final Map<ResourceId, F> biomes;

    private DeclaredByLevel(F dimension, Map<ResourceId, F> regions, Map<ResourceId, F> biomes) {
        this.dimension = dimension;
        this.regions = Map.copyOf(regions);
        this.biomes = Map.copyOf(biomes);
    }

    public static <F extends IrisFeature> DeclaredByLevel<F> of(PackWorld world, Class<F> type) {
        SettingsStack<F> declared = world.settings().declared(type);
        Map<ResourceId, F> regions = new HashMap<>();
        Map<ResourceId, F> biomes = new HashMap<>();
        for (Entry<F> entry : declared) {
            if (entry.level() == Level.REGION) {
                regions.putIfAbsent(sampleId(entry.source()), entry.value());
            } else if (entry.level() == Level.BIOME) {
                biomes.putIfAbsent(sampleId(entry.source()), entry.value());
            }
        }
        return new DeclaredByLevel<>(declared.dimension().orElse(null), regions, biomes);
    }

    /** The dimension's declaration; null when it declares none. */
    public F ofDimension() {
        return dimension;
    }

    /** The declaration of the region with this id; null when it declares none. */
    public F ofRegion(ResourceId region) {
        return regions.get(region);
    }

    /** The declaration of the biome with this id; null when it declares none. */
    public F ofBiome(ResourceId biome) {
        return biomes.get(biome);
    }

    private static ResourceId sampleId(ResourceId source) {
        String path = source.path();
        return new ResourceId(source.namespace(), path.substring(path.indexOf('/') + 1));
    }
}
