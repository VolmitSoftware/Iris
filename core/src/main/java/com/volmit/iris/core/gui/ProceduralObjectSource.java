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
import com.volmit.iris.engine.object.procedural.ResolvedProceduralPlacement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The objects the pack's procedural settings generate, one entry per definition in a folder per type.
 */
final class ProceduralObjectSource implements ObjectSource {
    private static final Map<String, String> FOLDER_BY_KIND = Map.of(
            "tree", "Trees",
            "formation", "Formations",
            "crystal", "Crystals",
            "coral", "Coral",
            "fungus", "Fungi",
            "ruin", "Ruins");

    private Map<String, ResolvedProceduralPlacement> definitionsByKey = Map.of();

    @Override
    public String noun() {
        return "procedural object";
    }

    @Override
    public List<String> keys(IrisData data) {
        Map<String, ResolvedProceduralPlacement> byKey = new LinkedHashMap<>();
        for (ResolvedProceduralPlacement definition : data.getProceduralObjects().definitions()) {
            byKey.put(FOLDER_BY_KIND.getOrDefault(definition.kind(), definition.kind()) + "/" + definition.name(), definition);
        }
        definitionsByKey = byKey;
        return List.copyOf(byKey.keySet());
    }

    @Override
    public List<IrisObject> variants(IrisData data, String key) {
        ResolvedProceduralPlacement definition = definitionsByKey.get(key);
        return definition == null ? List.of() : definition.variants();
    }
}
