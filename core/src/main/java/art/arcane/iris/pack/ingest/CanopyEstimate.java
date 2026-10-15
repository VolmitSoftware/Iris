/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.pack.ingest;

import art.arcane.iris.generation.decoration.IrisDecorationStep;
import art.arcane.iris.pack.ingest.catalog.CatalogBiome;
import art.arcane.iris.pack.ingest.catalog.CatalogPlacedFeature;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;

import java.util.List;
import java.util.Optional;
import java.util.Set;

final class CanopyEstimate {
    static final double CROWN_COLUMNS = 20D;
    private static final double UNKNOWN_TREE_COUNT = 1D;
    private static final double COLUMNS_PER_CHUNK = 256D;
    private static final int VEGETAL_STEP = IrisDecorationStep.VEGETAL_DECORATION.ordinal();

    private final WorldgenCatalogIndex index;
    private final TreeFeatures trees;

    CanopyEstimate(WorldgenCatalogIndex index) {
        this.index = index;
        this.trees = new TreeFeatures(index);
    }

    double occlusion(String featureKey, Set<String> declaringBiomes) {
        if (declaringBiomes.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (String biomeKey : declaringBiomes) {
            Optional<CatalogBiome> biome = index.biome(biomeKey);
            total += biome.map((CatalogBiome value) -> canopyBefore(value, featureKey)).orElse(0D);
        }
        return total / declaringBiomes.size();
    }

    private double canopyBefore(CatalogBiome biome, String featureKey) {
        if (biome.featureSteps().size() <= VEGETAL_STEP) {
            return 0D;
        }
        List<String> vegetal = biome.featureSteps().get(VEGETAL_STEP);
        double treesPerChunk = 0D;
        for (String candidate : vegetal) {
            if (candidate.equals(featureKey)) {
                break;
            }
            Optional<CatalogPlacedFeature> feature = index.placedFeature(candidate);
            if (feature.isPresent() && trees.isTree(feature.get())) {
                treesPerChunk += treesPerChunk(feature.get());
            }
        }
        return 1D - Math.exp(-treesPerChunk * CROWN_COLUMNS / COLUMNS_PER_CHUNK);
    }

    private static double treesPerChunk(CatalogPlacedFeature tree) {
        try {
            return VanillaPlacementChain.expectedCount(VanillaJson.placement(tree.placement()));
        } catch (IngestConversionException unknownCount) {
            return UNKNOWN_TREE_COUNT;
        }
    }
}
