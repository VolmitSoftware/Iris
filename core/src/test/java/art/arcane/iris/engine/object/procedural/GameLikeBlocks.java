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

package art.arcane.iris.engine.object.procedural;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Registries whose blocks carry properties the way the game's do: a key gets Minecraft's namespace, logs and stems an
 * axis, leaves their distance, persistence and waterlogging, properties are listed by name, and setting a property
 * gives the state with it. A key naming {@code minecraft:missing_*} is a block the platform lacks.
 */
final class GameLikeBlocks {
    private static final String NAMESPACE = "minecraft:";
    private static final String MISSING = NAMESPACE + "missing_";

    private final Map<String, PlatformBlockState> states = new ConcurrentHashMap<>();
    private final PlatformRegistries registries = mock(PlatformRegistries.class, withSettings().stubOnly());

    GameLikeBlocks() {
        when(registries.block(anyString())).thenAnswer(invocation -> {
            PlatformBlockState state = orNull(invocation.getArgument(0));
            return state == null ? state("minecraft:air") : state;
        });
        when(registries.blockOrNull(anyString())).thenAnswer(invocation -> orNull(invocation.getArgument(0)));
        when(registries.blockOrNull(anyString(), anyBoolean())).thenAnswer(invocation -> orNull(invocation.getArgument(0)));
        when(registries.air()).thenAnswer(invocation -> state("minecraft:air"));
    }

    PlatformRegistries registries() {
        return registries;
    }

    private PlatformBlockState orNull(String key) {
        String canonical = canonical(key);
        return canonical.startsWith(MISSING) ? null : state(canonical);
    }

    private PlatformBlockState state(String canonical) {
        return states.computeIfAbsent(canonical, this::create);
    }

    private PlatformBlockState create(String key) {
        PlatformBlockState state = mock(PlatformBlockState.class, withSettings().stubOnly());
        String material = material(key);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(material);
        when(state.isAir()).thenReturn(material.endsWith("air"));
        when(state.withProperty(anyString(), anyString())).thenAnswer(invocation -> {
            Map<String, String> properties = properties(key);
            properties.put(invocation.getArgument(0), invocation.getArgument(1));
            return state(write(material, properties));
        });
        return state;
    }

    private static String canonical(String key) {
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        String namespaced = lower.contains(":") ? lower : NAMESPACE + lower;
        String material = material(namespaced);
        Map<String, String> properties = defaults(material);
        properties.putAll(properties(namespaced));
        return write(material, properties);
    }

    private static Map<String, String> defaults(String material) {
        Map<String, String> properties = new TreeMap<>();
        if (material.endsWith("_leaves")) {
            properties.put("distance", "7");
            properties.put("persistent", "false");
            properties.put("waterlogged", "false");
        } else if (material.endsWith("_log") || material.endsWith("_wood") || material.endsWith("_stem")
                || material.endsWith("_hyphae")) {
            properties.put("axis", "y");
        }
        return properties;
    }

    private static String material(String key) {
        int bracket = key.indexOf('[');
        return bracket < 0 ? key : key.substring(0, bracket);
    }

    private static Map<String, String> properties(String key) {
        Map<String, String> properties = new TreeMap<>();
        int bracket = key.indexOf('[');
        if (bracket < 0 || !key.endsWith("]")) {
            return properties;
        }
        for (String property : key.substring(bracket + 1, key.length() - 1).split(",")) {
            int equals = property.indexOf('=');
            if (equals > 0) {
                properties.put(property.substring(0, equals), property.substring(equals + 1));
            }
        }
        return properties;
    }

    private static String write(String material, Map<String, String> properties) {
        if (properties.isEmpty()) {
            return material;
        }
        return material + properties.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "[", "]"));
    }
}
