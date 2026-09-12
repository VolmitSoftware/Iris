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

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionTerrainContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

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
                collectConfiguredBiomeKeys(
                        dimension.getReachableBiomes(() -> data),
                        customBiome -> data.customBiomeResourceKey(dimension, customBiome)
                ));
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

    static Set<String> collectConfiguredBiomeKeys(Engine engine) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(collectConfiguredBiomeKeys(
                engine.getDimension(), engine.getData()));
        DimensionStackContext stackContext = engine.getDimensionStackContext();
        if (stackContext == null) {
            return Set.copyOf(keys);
        }
        for (DimensionTerrainContext terrainContext : stackContext.getLayersBottomToTop()) {
            if (terrainContext.isSelfReferencing()) {
                continue;
            }
            keys.addAll(collectConfiguredBiomeKeys(
                    terrainContext.getDimension(), terrainContext.getData()));
        }
        return Set.copyOf(keys);
    }

    static Set<String> collectConfiguredBiomeKeys(
            Iterable<IrisBiome> biomes,
            Function<IrisBiomeCustom, String> customBiomeKey
    ) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
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
                keys.add(customBiomeKey.apply(customBiome).toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(keys);
    }

    static Set<String> collectConfiguredBiomeKeys(Iterable<IrisBiome> biomes, String dimensionLoadKey) {
        return collectConfiguredBiomeKeys(
                biomes,
                customBiome -> IrisDimension.customBiomeKey(dimensionLoadKey, customBiome.getId())
        );
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
