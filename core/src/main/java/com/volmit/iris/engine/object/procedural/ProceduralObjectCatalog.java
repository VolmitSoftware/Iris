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
package com.volmit.iris.engine.object.procedural;

import com.google.gson.Gson;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.engine.object.procedural.coral.ResolvedCoral;
import com.volmit.iris.engine.object.procedural.crystal.ResolvedCrystal;
import com.volmit.iris.engine.object.procedural.formation.ResolvedFormation;
import com.volmit.iris.engine.object.procedural.fungi.ResolvedFungus;
import com.volmit.iris.engine.object.procedural.ruin.ResolvedRuin;
import com.volmit.iris.engine.object.procedural.tree.ResolvedTree;
import com.volmit.iris.util.collection.KMap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The procedural objects of one pack. A definition several biomes share, such as one snippet, is baked once, and every
 * variant can be found again by its key.
 */
public final class ProceduralObjectCatalog {
    private static final Gson DEFINITION_JSON = new Gson();

    private final IrisData data;
    private final KMap<String, ResolvedProceduralPlacement> byDefinition = new KMap<>();
    private final KMap<String, ResolvedProceduralPlacement> byVariantKey = new KMap<>();

    public ProceduralObjectCatalog(IrisData data) {
        this.data = data;
    }

    /** The placements {@code settings} declare, shared with every other declaration of the same definitions. */
    public List<ResolvedProceduralPlacement> resolve(ProceduralObjectSettings settings) {
        List<ResolvedProceduralPlacement> all = new ArrayList<>();
        addAll(all, settings.trees(), tree -> new ResolvedTree(tree, data));
        addAll(all, settings.ruins(), ruin -> new ResolvedRuin(ruin, data));
        addAll(all, settings.formations(), formation -> new ResolvedFormation(formation, data));
        addAll(all, settings.coral(), coral -> new ResolvedCoral(coral, data));
        addAll(all, settings.fungi(), fungus -> new ResolvedFungus(fungus, data));
        addAll(all, settings.crystals(), crystal -> new ResolvedCrystal(crystal, data));
        return List.copyOf(all);
    }

    private <S> void addAll(List<ResolvedProceduralPlacement> all, List<S> definitions, Function<S, ResolvedProceduralPlacement> resolve) {
        for (S definition : definitions) {
            String identity = definition.getClass().getName() + DEFINITION_JSON.toJson(definition);
            all.add(byDefinition.computeIfAbsent(identity, ignored -> register(resolve.apply(definition))));
        }
    }

    private ResolvedProceduralPlacement register(ResolvedProceduralPlacement placement) {
        for (String key : placement.variantKeys()) {
            byVariantKey.putIfAbsent(key, placement);
        }
        return placement;
    }

    /** The key of every variant the pack's regions and biomes declare, whether or not its dimensions reach them. */
    public List<String> variantKeys() {
        Set<String> keys = new LinkedHashSet<>();
        data.getRegionLoader().streamAll().forEach(region -> addVariantKeys(keys, region.getProceduralObjects()));
        data.getBiomeLoader().streamAll().forEach(biome -> addVariantKeys(keys, biome.getProceduralObjects()));
        return List.copyOf(keys);
    }

    private void addVariantKeys(Set<String> keys, ProceduralObjectSettings settings) {
        if (settings == null) {
            return;
        }
        for (ResolvedProceduralPlacement placement : settings.resolved(data)) {
            keys.addAll(placement.variantKeys());
        }
    }

    /** The baked variant loaded under {@code key}; null when no definition resolved so far has it, or it baked no blocks. */
    public IrisObject variant(String key) {
        ResolvedProceduralPlacement placement = byVariantKey.get(key);
        return placement == null ? null : placement.variant(key);
    }

    public static boolean isVariantKey(String key) {
        return key.startsWith("procedural/");
    }
}
