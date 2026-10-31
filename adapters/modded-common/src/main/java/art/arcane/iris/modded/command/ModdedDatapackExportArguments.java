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

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.DatapackExportDestinations;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

record ModdedDatapackExportArguments(String dimension, String version, String mode, String namespace, String output,
                                     String format, boolean objects, boolean replace) {
    private static final Pattern DIMENSION = Pattern.compile("[A-Za-z0-9_/.-]+");
    private static final String VERSION_KEY = "version";
    private static final String MODE_KEY = "mode";
    private static final String NAMESPACE_KEY = "namespace";
    private static final String OUTPUT_KEY = "output";
    private static final String FORMAT_KEY = "format";
    private static final String OBJECTS_KEY = "objects";
    private static final String REPLACE_KEY = "replace";
    private static final Set<String> OPTIONS = Set.of(VERSION_KEY, MODE_KEY, NAMESPACE_KEY, OUTPUT_KEY, FORMAT_KEY,
            OBJECTS_KEY, REPLACE_KEY);

    ModdedDatapackExportArguments {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(format, "format");
    }

    static ModdedDatapackExportArguments parse(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        String[] tokens = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
        String dimension = null;
        Map<String, String> options = new HashMap<>(OPTIONS.size());
        for (String token : tokens) {
            int equals = token.indexOf('=');
            if (equals < 0) {
                if (dimension != null) {
                    throw new IllegalArgumentException("unexpected argument '" + token + "'");
                }
                dimension = token;
                continue;
            }
            String key = token.substring(0, equals);
            if (!OPTIONS.contains(key)) {
                throw new IllegalArgumentException("unknown option '" + key + "'");
            }
            if (options.putIfAbsent(key, token.substring(equals + 1)) != null) {
                throw new IllegalArgumentException("option '" + key + "' given twice");
            }
        }
        if (dimension == null) {
            throw new IllegalArgumentException("missing dimension");
        }
        if (!DIMENSION.matcher(dimension).matches() || dimension.contains("..")) {
            throw new IllegalArgumentException("invalid dimension key '" + dimension + "'");
        }
        ModdedDatapackExportArguments arguments = new ModdedDatapackExportArguments(
                dimension,
                options.getOrDefault(VERSION_KEY, ""),
                options.getOrDefault(MODE_KEY, WorldgenExportRequest.DEFAULT_MODE),
                options.getOrDefault(NAMESPACE_KEY, WorldgenExportRequest.DEFAULT_NAMESPACE),
                options.getOrDefault(OUTPUT_KEY, ""),
                options.getOrDefault(FORMAT_KEY, WorldgenExportRequest.DEFAULT_FORMAT),
                flag(OBJECTS_KEY, options.get(OBJECTS_KEY), true),
                flag(REPLACE_KEY, options.get(REPLACE_KEY), false));
        arguments.target();
        WorldgenExportMode.parse(arguments.mode());
        DatapackExportDestinations.parseFormat(arguments.format());
        return arguments;
    }

    DataVersion target() {
        return WorldgenExportTargets.resolve(version);
    }

    WorldgenExportRequest toRequest(Path pack, Path exportRoot) {
        return WorldgenExportRequest.builder()
                .pack(pack)
                .dimension(dimension)
                .version(version)
                .mode(mode)
                .namespace(namespace)
                .output(output)
                .format(format)
                .objects(objects)
                .replace(replace)
                .exportRoot(exportRoot)
                .build();
    }

    private static boolean flag(String key, String raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        return switch (raw) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(key + " must be true or false");
        };
    }
}
