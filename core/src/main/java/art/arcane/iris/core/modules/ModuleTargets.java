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

package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.JsonFieldNames;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import com.volmit.config.Level;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which Iris class holds the JSON of each attachment target that module settings can appear in.
 */
public final class ModuleTargets {
    private static final Map<Level, Class<?>> HOLDERS = Map.of(
            Level.DIMENSION, IrisDimension.class,
            Level.REGION, IrisRegion.class,
            Level.BIOME, IrisBiome.class,
            Level.PLACEMENT, IrisObjectPlacement.class);

    private ModuleTargets() {
    }

    /** The target whose JSON a resource of {@code resourceType} is, if module settings can appear in it. */
    public static Optional<Level> ofResource(Class<?> resourceType) {
        return HOLDERS.entrySet().stream()
                .filter(entry -> entry.getValue() == resourceType)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** Iris's own JSON fields per target, which no settings key may take. */
    static Map<Level, Set<String>> reservedFields() {
        Map<Level, Set<String>> fields = new EnumMap<>(Level.class);
        HOLDERS.forEach((target, holder) -> fields.put(target, JsonFieldNames.of(holder)));
        return fields;
    }
}
