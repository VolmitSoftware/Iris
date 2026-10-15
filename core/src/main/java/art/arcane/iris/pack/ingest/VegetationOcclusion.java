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

final class VegetationOcclusion {
    static final double CROWN_COLUMNS = 20D;
    private static final double UNKNOWN_TREE_COUNT = 1D;
    private static final double COLUMNS_PER_CHUNK = 256D;
    private static final int VEGETAL_STEP = IrisDecorationStep.VEGETAL_DECORATION.ordinal();

    private final WorldgenCatalogIndex index;
    private final TreeFeatures trees;

    VegetationOcclusion(WorldgenCatalogIndex index) {
        this.index = index;
        this.trees = new TreeFeatures(index);
    }

    Occlusion occlusion(String featureKey, Set<String> declaringBiomes) {
        if (declaringBiomes.isEmpty()) {
            return Occlusion.NONE;
        }
        double canopy = 0D;
        double occupancy = 0D;
        for (String biomeKey : declaringBiomes) {
            Optional<CatalogBiome> biome = index.biome(biomeKey);
            if (biome.isEmpty() || biome.get().featureSteps().size() <= VEGETAL_STEP) {
                continue;
            }
            Occlusion before = before(biome.get().featureSteps().get(VEGETAL_STEP), featureKey);
            canopy += before.canopy();
            occupancy += before.occupancy();
        }
        return new Occlusion(canopy / declaringBiomes.size(), occupancy / declaringBiomes.size());
    }

    private Occlusion before(List<String> vegetal, String featureKey) {
        double treesPerChunk = 0D;
        double open = 1D;
        for (String candidate : vegetal) {
            if (candidate.equals(featureKey)) {
                break;
            }
            Optional<CatalogPlacedFeature> feature = index.placedFeature(candidate);
            if (feature.isEmpty()) {
                continue;
            }
            if (trees.isTree(feature.get())) {
                treesPerChunk += treesPerChunk(feature.get());
            } else if (PatchDecoratorConverter.handles(feature.get())) {
                open *= 1D - surfaceCoverage(feature.get());
            }
        }
        return new Occlusion(1D - Math.exp(-treesPerChunk * CROWN_COLUMNS / COLUMNS_PER_CHUNK), 1D - open);
    }

    private double surfaceCoverage(CatalogPlacedFeature patch) {
        try {
            return PatchDecoratorConverter.columnCoverage(PatchDecoratorConverter.expectedPlacements(patch, index));
        } catch (IngestConversionException unknownDensity) {
            return 0D;
        }
    }

    private static double treesPerChunk(CatalogPlacedFeature tree) {
        try {
            return VanillaPlacementChain.expectedCount(VanillaJson.placement(tree.placement()));
        } catch (IngestConversionException unknownCount) {
            return UNKNOWN_TREE_COUNT;
        }
    }

    record Occlusion(double canopy, double occupancy) {
        static final Occlusion NONE = new Occlusion(0D, 0D);
    }
}
