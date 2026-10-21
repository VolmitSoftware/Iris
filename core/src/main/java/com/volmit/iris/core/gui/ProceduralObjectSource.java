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
package com.volmit.iris.core.gui;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.engine.object.procedural.ProceduralObjectCatalog;
import com.volmit.iris.engine.object.procedural.ResolvedProceduralPlacement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * The objects the pack's procedural settings generate, one entry per definition, in the folders the pack keeps them in.
 */
final class ProceduralObjectSource implements ObjectSource {
    private static final Map<String, String> FOLDER_LABEL_BY_KIND = Map.of(
            "trees", "Trees",
            "formations", "Formations",
            "crystals", "Crystals",
            "coral", "Coral",
            "fungi", "Fungi",
            "ruins", "Ruins");

    private Map<String, ResolvedProceduralPlacement> definitionsByKey = Map.of();

    @Override
    public String noun() {
        return "procedural object";
    }

    // The type folder gets its label; the rest of the path stays as the pack has it.
    @Override
    public List<String> keys(IrisData data) {
        Map<String, ResolvedProceduralPlacement> byKey = new LinkedHashMap<>();
        for (ProceduralObjectCatalog.Definition definition : data.getProceduralObjects().definitions()) {
            String kind = definition.placement().kind();
            String key = FOLDER_LABEL_BY_KIND.getOrDefault(kind, kind) + definition.path().substring(kind.length());
            byKey.put(key, definition.placement());
        }
        definitionsByKey = byKey;
        return List.copyOf(byKey.keySet());
    }

    @Override
    public List<IrisObject> variants(IrisData data, String key) {
        ResolvedProceduralPlacement definition = definitionsByKey.get(key);
        return definition == null ? List.of() : definition.variants();
    }

    @Override
    public OptionalLong seed(String key) {
        ResolvedProceduralPlacement definition = definitionsByKey.get(key);
        return definition == null ? OptionalLong.empty() : OptionalLong.of(definition.seed());
    }

    // The pack's own seed reuses the variants world generation bakes.
    @Override
    public List<IrisObject> variants(IrisData data, String key, long seed) {
        ResolvedProceduralPlacement definition = definitionsByKey.get(key);
        if (definition == null) {
            return List.of();
        }
        return definition.seed() == seed ? definition.variants() : data.getProceduralObjects().reseeded(definition, seed).variants();
    }
}
