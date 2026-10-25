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

package art.arcane.iris.studio.map;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisProceduralObjects;
import art.arcane.iris.generation.decoration.IrisProceduralPlacement;
import art.arcane.iris.generation.decoration.coral.IrisCoral;
import art.arcane.iris.generation.decoration.crystal.IrisCrystal;
import art.arcane.iris.generation.decoration.formation.IrisFormation;
import art.arcane.iris.generation.decoration.fungus.IrisFungus;
import art.arcane.iris.generation.decoration.ruin.IrisRuin;
import art.arcane.iris.generation.decoration.tree.IrisProceduralTree;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * The procedural objects the pack's biomes and regions bake, one entry per placement, filed under the biome or region
 * that declares it.
 */
final class ProceduralObjectSource implements ObjectSource {
    private static final int EMPTY_ICON_SIZE = 40;
    private static final String BIOME_FOLDER = "biomes";
    private static final String REGION_FOLDER = "regions";

    private volatile Map<String, IrisProceduralPlacement> placementsByKey = Map.of();

    @Override
    public String noun() {
        return "procedural object";
    }

    @Override
    public EmptyState emptyState() {
        return new EmptyState(MapViewIcon.large(MapView.PROCEDURAL, EMPTY_ICON_SIZE), "No procedural objects yet",
                "Add trees, ruins, formations, coral, fungi or crystals to",
                "a biome's or region's proceduralObjects", "They appear here once the pack reloads.");
    }

    @Override
    public List<String> keys(IrisData data) {
        Map<String, IrisProceduralPlacement> byKey = new LinkedHashMap<>();
        for (String biomeKey : data.getBiomeLoader().getPossibleKeys()) {
            IrisBiome biome = data.getBiomeLoader().load(biomeKey, false);
            if (biome != null) {
                addPlacements(byKey, BIOME_FOLDER + "/" + biomeKey, biome.getProceduralObjects());
            }
        }
        for (String regionKey : data.getRegionLoader().getPossibleKeys()) {
            IrisRegion region = data.getRegionLoader().load(regionKey, false);
            if (region != null) {
                addPlacements(byKey, REGION_FOLDER + "/" + regionKey, region.getProceduralObjects());
            }
        }
        placementsByKey = byKey;
        return List.copyOf(byKey.keySet());
    }

    // Placements keep their default name unless the pack renames them, so repeated names are numbered.
    private static void addPlacements(Map<String, IrisProceduralPlacement> byKey, String ownerFolder, IrisProceduralObjects objects) {
        if (objects == null) {
            return;
        }
        for (IrisProceduralPlacement placement : objects.getAllPlacements()) {
            if (placement == null) {
                continue;
            }
            String key = ownerFolder + "/" + placement.getName();
            for (int repeat = 2; byKey.containsKey(key); repeat++) {
                key = ownerFolder + "/" + placement.getName() + " " + repeat;
            }
            byKey.put(key, placement);
        }
    }

    @Override
    public List<IrisObject> variants(IrisData data, String key) {
        IrisProceduralPlacement placement = placementsByKey.get(key);
        return placement == null ? List.of() : List.copyOf(placement.getVariantObjects(data));
    }

    @Override
    public OptionalLong seed(String key) {
        IrisProceduralPlacement placement = placementsByKey.get(key);
        return placement == null ? OptionalLong.empty() : OptionalLong.of(seedOf(placement));
    }

    // Procedural objects are baked with their ground at height 0, which placement puts one block above the surface.
    @Override
    public OptionalInt groundY(IrisObject object) {
        return OptionalInt.of(1 - object.getH() / 2);
    }

    // The pack's own seed reuses the variants world generation bakes.
    @Override
    public List<IrisObject> variants(IrisData data, String key, long seed) {
        IrisProceduralPlacement placement = placementsByKey.get(key);
        if (placement == null) {
            return List.of();
        }
        IrisProceduralPlacement baked = seedOf(placement) == seed ? placement : reseeded(data, placement, seed);
        return List.copyOf(baked.getVariantObjects(data));
    }

    @Override
    public boolean isBaked() {
        return true;
    }

    // A fresh copy every run, since a placement keeps the variants it baked.
    @Override
    public BakeRun timeBake(IrisData data, String key, long seed) {
        IrisProceduralPlacement placement = placementsByKey.get(key);
        if (placement == null) {
            return null;
        }
        IrisProceduralPlacement copy = reseeded(data, placement, seed);
        long start = System.nanoTime();
        int variantCount = copy.getVariantObjects(data).size();
        return new BakeRun(System.nanoTime() - start, variantCount);
    }

    private static IrisProceduralPlacement reseeded(IrisData data, IrisProceduralPlacement placement, long seed) {
        IrisProceduralPlacement copy = data.getGson().fromJson(data.getGson().toJson(placement), placement.getClass());
        switch (copy) {
            case IrisProceduralTree tree -> tree.setSeed(seed);
            case IrisRuin ruin -> ruin.setSeed(seed);
            case IrisFormation formation -> formation.setSeed(seed);
            case IrisCoral coral -> coral.setSeed(seed);
            case IrisFungus fungus -> fungus.setSeed(seed);
            case IrisCrystal crystal -> crystal.setSeed(seed);
            default -> throw new IllegalArgumentException("Unknown procedural object " + placement.getClass().getName());
        }
        return copy;
    }

    private static long seedOf(IrisProceduralPlacement placement) {
        return switch (placement) {
            case IrisProceduralTree tree -> tree.getSeed();
            case IrisRuin ruin -> ruin.getSeed();
            case IrisFormation formation -> formation.getSeed();
            case IrisCoral coral -> coral.getSeed();
            case IrisFungus fungus -> fungus.getSeed();
            case IrisCrystal crystal -> crystal.getSeed();
            default -> throw new IllegalArgumentException("Unknown procedural object " + placement.getClass().getName());
        };
    }
}
