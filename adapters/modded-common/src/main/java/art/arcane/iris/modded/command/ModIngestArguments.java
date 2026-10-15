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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

record ModIngestArguments(String sourcePack, String targetPack, String dimension, Set<String> namespaces,
                          Set<String> excludedRegions, boolean replace) {
    static final List<String> OPTION_SUGGESTIONS = List.of("dimension=", "namespaces=", "excludeRegions=", "replace=true");
    private static final Pattern PACK = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern TARGET_PACK = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern DIMENSION = Pattern.compile("[A-Za-z0-9_/.-]+");
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern REGION = Pattern.compile("[A-Za-z0-9_/.-]+");
    private static final String DIMENSION_KEY = "dimension";
    private static final String NAMESPACES_KEY = "namespaces";
    private static final String REPLACE_KEY = "replace";
    private static final String EXCLUDE_REGIONS_KEY = "excludeRegions";

    ModIngestArguments {
        Objects.requireNonNull(sourcePack, "sourcePack");
        Objects.requireNonNull(targetPack, "targetPack");
        Objects.requireNonNull(dimension, "dimension");
        namespaces = Set.copyOf(Objects.requireNonNull(namespaces, "namespaces"));
        excludedRegions = Set.copyOf(Objects.requireNonNull(excludedRegions, "excludedRegions"));
    }

    static ModIngestArguments parse(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        String[] tokens = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
        String source = null;
        String target = null;
        Map<String, String> options = new HashMap<>(4);
        for (String token : tokens) {
            int equals = token.indexOf('=');
            if (equals < 0) {
                if (source == null) {
                    source = token;
                } else if (target == null) {
                    target = token;
                } else {
                    throw new IllegalArgumentException("unexpected argument '" + token + "'");
                }
                continue;
            }
            String key = token.substring(0, equals);
            if (!key.equals(DIMENSION_KEY) && !key.equals(NAMESPACES_KEY) && !key.equals(REPLACE_KEY)
                    && !key.equals(EXCLUDE_REGIONS_KEY)) {
                throw new IllegalArgumentException("unknown option '" + key + "'");
            }
            if (options.putIfAbsent(key, token.substring(equals + 1)) != null) {
                throw new IllegalArgumentException("option '" + key + "' given twice");
            }
        }
        if (source == null) {
            throw new IllegalArgumentException("missing source pack");
        }
        if (target == null) {
            throw new IllegalArgumentException("missing target pack");
        }
        requirePack(source);
        requirePack(target);
        if (!TARGET_PACK.matcher(target).matches()) {
            throw new IllegalArgumentException("invalid target pack name '" + target + "'");
        }
        if (source.equals(target)) {
            throw new IllegalArgumentException("source and target must be different packs");
        }
        String dimension = options.getOrDefault(DIMENSION_KEY, source);
        if (!DIMENSION.matcher(dimension).matches() || dimension.contains("..")) {
            throw new IllegalArgumentException("invalid dimension key '" + dimension + "'");
        }
        return new ModIngestArguments(source, target, dimension, namespaces(options.get(NAMESPACES_KEY)),
                regions(options.get(EXCLUDE_REGIONS_KEY)), replace(options.get(REPLACE_KEY)));
    }

    private static void requirePack(String pack) {
        if (!PACK.matcher(pack).matches() || pack.contains("..")) {
            throw new IllegalArgumentException("invalid pack name '" + pack + "'");
        }
    }

    private static Set<String> namespaces(String raw) {
        if (raw == null) {
            return Set.of();
        }
        Set<String> namespaces = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!NAMESPACE.matcher(part).matches()) {
                throw new IllegalArgumentException("invalid namespace '" + part + "'");
            }
            namespaces.add(part);
        }
        if (namespaces.isEmpty()) {
            throw new IllegalArgumentException("namespaces must not be empty");
        }
        return namespaces;
    }

    private static Set<String> regions(String raw) {
        if (raw == null) {
            return Set.of();
        }
        Set<String> regions = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!REGION.matcher(part).matches() || part.contains("..")) {
                throw new IllegalArgumentException("invalid region '" + part + "'");
            }
            regions.add(part);
        }
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("excludeRegions must not be empty");
        }
        return regions;
    }

    private static boolean replace(String raw) {
        if (raw == null || raw.equals("false")) {
            return false;
        }
        if (raw.equals("true")) {
            return true;
        }
        throw new IllegalArgumentException("replace must be true or false");
    }
}
