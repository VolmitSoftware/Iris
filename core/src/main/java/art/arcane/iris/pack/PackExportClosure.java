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

package art.arcane.iris.pack;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionStack;
import art.arcane.iris.structure.object.IrisObjectMarker;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisStaticObject;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Shared key collection for the pack packagers. Both the Bukkit re-serializing compiler and the
 * modded verbatim-copy compiler must export the complete ambient-spawning graph: object placements
 * carry markers, markers carry spawners, spawners carry entities, entities carry loot. These
 * helpers keep the two packagers walking placements identically.
 */
public final class PackExportClosure {
    private PackExportClosure() {
    }

    public static String copySnippets(File packFolder, File targetFolder) throws IOException {
        Path sourceRoot = new File(packFolder, "snippet").toPath();
        if (!Files.isDirectory(sourceRoot)) {
            return "";
        }
        Path targetRoot = new File(targetFolder, "snippet").toPath();
        StringBuilder hashes = new StringBuilder();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList()) {
                String json = new JSONObject(IO.readAll(source.toFile())).toString(0);
                IO.writeAll(targetRoot.resolve(sourceRoot.relativize(source)).toFile(), json);
                hashes.append(IO.hash(json));
            }
        }
        return hashes.toString();
    }

    public static KSet<String> collectDimensionKeys(IrisDimension dimension) {
        return collectDimensionKeys(dimension, key -> dimension == null || dimension.getLoader() == null
                ? null
                : dimension.getLoader().getDimensionLoader().load(key));
    }

    static KSet<String> collectDimensionKeys(
            IrisDimension dimension,
            Function<String, IrisDimension> resolver
    ) {
        KSet<String> dimensionKeys = new KSet<>();
        if (dimension == null || dimension.getLoadKey() == null || dimension.getLoadKey().isBlank()) {
            return dimensionKeys;
        }

        ArrayDeque<IrisDimension> pending = new ArrayDeque<>();
        pending.add(dimension);
        while (!pending.isEmpty()) {
            IrisDimension current = pending.removeFirst();
            String currentKey = current.getLoadKey();
            if (currentKey == null || currentKey.isBlank() || !dimensionKeys.add(currentKey)) {
                continue;
            }
            IrisDimensionStack stack = current.getDimensionStack();
            if (stack == null || stack.getDimensions() == null) {
                continue;
            }
            for (String key : stack.getDimensions()) {
                if (key == null || key.isBlank() || dimensionKeys.contains(key)) {
                    continue;
                }
                IrisDimension referenced = resolver.apply(key);
                if (referenced == null) {
                    dimensionKeys.add(key);
                } else {
                    pending.addLast(referenced);
                }
            }
        }
        return dimensionKeys;
    }

    public static KSet<String> collectMarkerKeys(Iterable<IrisObjectPlacement> placements) {
        KSet<String> markerKeys = new KSet<>();
        if (placements == null) {
            return markerKeys;
        }
        for (IrisObjectPlacement placement : placements) {
            if (placement == null || placement.getMarkers() == null) {
                continue;
            }
            for (IrisObjectMarker marker : placement.getMarkers()) {
                if (marker == null || marker.getMarker() == null || marker.getMarker().isBlank()) {
                    continue;
                }
                markerKeys.add(marker.getMarker());
            }
        }
        return markerKeys;
    }

    public static KSet<String> collectObjectKeys(Iterable<IrisObjectPlacement> placements) {
        KSet<String> objectKeys = new KSet<>();
        if (placements == null) {
            return objectKeys;
        }
        for (IrisObjectPlacement placement : placements) {
            if (placement == null || placement.getPlace() == null) {
                continue;
            }
            for (String key : placement.getPlace()) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                objectKeys.add(key);
            }
        }
        return objectKeys;
    }

    public static KSet<String> collectStaticObjectKeys(Iterable<IrisStaticObject> placements) {
        KSet<String> objectKeys = new KSet<>();
        if (placements == null) {
            return objectKeys;
        }
        for (IrisStaticObject placement : placements) {
            if (placement != null && placement.getObject() != null && !placement.getObject().isBlank()) {
                objectKeys.add(placement.getObject());
            }
        }
        return objectKeys;
    }
}
