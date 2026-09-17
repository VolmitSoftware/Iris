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

package art.arcane.iris.modded;

import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustom;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

final class ModdedDimensionMetadata {
    private ModdedDimensionMetadata() {
    }

    static DimensionMetadata dimensionMetadata(IrisDimension dimension) {
        int minY = dimension.getMinHeight();
        int maxY = dimension.getMaxHeight();
        if (maxY <= minY) {
            throw new IllegalStateException("Iris dimension '" + dimension.getLoadKey()
                    + "' has invalid height range " + minY + ".." + maxY);
        }
        return new DimensionMetadata(minY, maxY, minY + dimension.getFluidHeight());
    }

    static Set<String> collectConfiguredBiomeKeys(IrisDimension dimension, IrisData data) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(
                collectConfiguredBiomeKeys(dimension.getReachableBiomes(() -> data), dimension.getLoadKey()));
        for (IrisRegion region : dimension.getAllRegions(() -> data)) {
            if (region == null) {
                continue;
            }
            if (!region.getSeaBiomes().isEmpty()) {
                keys.add("minecraft:the_void");
            }
            if (!region.getShoreBiomes().isEmpty()) {
                keys.add("minecraft:beach");
            }
        }
        return Set.copyOf(keys);
    }

    static Set<String> collectConfiguredBiomeKeys(Iterable<IrisBiome> biomes, String dimensionLoadKey) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        String namespace = dimensionLoadKey.toLowerCase(Locale.ROOT);
        for (IrisBiome irisBiome : biomes) {
            if (irisBiome == null) {
                continue;
            }
            Identifier derivative = Identifier.tryParse(irisBiome.getStructureDerivativeKey());
            if (derivative != null) {
                keys.add(derivative.toString().toLowerCase(Locale.ROOT));
            }
            if (!irisBiome.isCustom()) {
                continue;
            }
            for (IrisBiomeCustom customBiome : irisBiome.getCustomDerivitives()) {
                keys.add(namespace + ":" + customBiome.getId().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(keys);
    }

    static int clampSpawnHeight(int minY, int height) {
        int minimum = minY + 1;
        int maximum = minY + height - 2;
        return Math.max(minimum, Math.min(maximum, 96));
    }

    record DimensionMetadata(int minY, int maxY, int seaLevel) {
        int depth() {
            return maxY - minY;
        }
    }

    record ConfiguredPack(IrisData data, IrisDimension dimension, DimensionMetadata metadata) {
    }
}
