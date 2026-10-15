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

package art.arcane.iris.modded.command;

import art.arcane.iris.modded.ModdedDimensionReplacement;
import art.arcane.iris.world.IrisEnvironment;

import java.util.Locale;
import java.util.Objects;

record ModdedReplaceTarget(String levelKey, Kind kind) {
    static final String IRIS_WORLD_NAMESPACE = "irisworldgen";
    private static final String PAPER_IRIS_NAMESPACE = "iris";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";

    ModdedReplaceTarget {
        Objects.requireNonNull(levelKey, "levelKey");
        Objects.requireNonNull(kind, "kind");
    }

    static ModdedReplaceTarget resolve(String requestedName, String levelName) {
        String requested = requestedName == null ? "" : requestedName.trim();
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("World name cannot be empty.");
        }
        String normalized = requested.toLowerCase(Locale.ENGLISH);
        if (normalized.contains(":")) {
            return exact(normalized, requestedName);
        }
        if (requested.contains("/") || requested.contains("\\") || requested.contains("..")) {
            throw new IllegalArgumentException("World name must be a safe single path segment.");
        }
        String configuredLevelName = levelName == null ? "" : levelName.trim();
        if (!configuredLevelName.isEmpty()) {
            if (requested.equalsIgnoreCase(configuredLevelName)) {
                return of(OVERWORLD);
            }
            if (requested.equalsIgnoreCase(configuredLevelName + "_nether")) {
                return of(NETHER);
            }
            if (requested.equalsIgnoreCase(configuredLevelName + "_the_end")) {
                return of(END);
            }
        }
        return switch (normalized) {
            case "main", "overworld" -> of(OVERWORLD);
            case "nether", "the_nether" -> of(NETHER);
            case "end", "the_end" -> of(END);
            default -> of(IRIS_WORLD_NAMESPACE + ":" + normalized.replace(' ', '_'));
        };
    }

    static void requireCompatibleEnvironment(Kind kind, IrisEnvironment environment) {
        IrisEnvironment expected = kind.environment();
        if (expected != null && environment != expected) {
            throw new IllegalArgumentException("The " + kind.slotName() + " slot requires a pack environment of "
                    + expected.name() + ".");
        }
    }

    private static ModdedReplaceTarget exact(String normalized, String requestedName) {
        int separator = normalized.indexOf(':');
        if (separator == 0 || separator == normalized.length() - 1 || separator != normalized.lastIndexOf(':')) {
            throw new IllegalArgumentException("World identifier is invalid: " + requestedName);
        }
        String namespace = normalized.substring(0, separator);
        String path = normalized.substring(separator + 1);
        String levelKey = PAPER_IRIS_NAMESPACE.equals(namespace) ? IRIS_WORLD_NAMESPACE + ":" + path : normalized;
        return of(levelKey);
    }

    private static ModdedReplaceTarget of(String levelKey) {
        String level = ModdedDimensionReplacement.requireLevelKey(levelKey);
        Kind kind = switch (level) {
            case OVERWORLD -> Kind.VANILLA_OVERWORLD;
            case NETHER -> Kind.VANILLA_NETHER;
            case END -> Kind.VANILLA_END;
            default -> level.startsWith(IRIS_WORLD_NAMESPACE + ":") ? Kind.IRIS_WORLD : Kind.DIMENSION;
        };
        return new ModdedReplaceTarget(level, kind);
    }

    enum Kind {
        VANILLA_OVERWORLD(IrisEnvironment.NORMAL),
        VANILLA_NETHER(IrisEnvironment.NETHER),
        VANILLA_END(IrisEnvironment.THE_END),
        IRIS_WORLD(null),
        DIMENSION(null);

        private final IrisEnvironment environment;

        Kind(IrisEnvironment environment) {
            this.environment = environment;
        }

        IrisEnvironment environment() {
            return environment;
        }

        String slotName() {
            return name().toLowerCase(Locale.ENGLISH);
        }
    }
}
