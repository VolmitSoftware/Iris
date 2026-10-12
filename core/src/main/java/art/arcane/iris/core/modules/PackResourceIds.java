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

import com.volmit.resource.ResourceId;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Names a pack's dimensions, regions and biomes as {@link ResourceId}s where IrisApi needs one. Pack keys
 * such as {@code desert/dunes} have no namespace, so the namespace is the pack folder's name, made a
 * valid namespace: lower case, and every other character outside {@code [a-z0-9_.-]} replaced by
 * {@code _}. A biome of pack {@code overworld} is {@code overworld:desert/dunes}.
 */
final class PackResourceIds {
    private static final Pattern NOT_NAMESPACE = Pattern.compile("[^a-z0-9_.-]");
    private static final Pattern NOT_PATH = Pattern.compile("[^a-z0-9_./-]");
    private static final String REPLACEMENT = "_";
    private static final String UNNAMED_PACK = "pack";
    private static final String PACK_PATH = "pack";

    private final String namespace;
    /** Stages ask for the id of the same few keys for every column, so each is worked out once. */
    private final Map<String, ResourceId> known = new ConcurrentHashMap<>();

    PackResourceIds(File packFolder) {
        String name = NOT_NAMESPACE.matcher(packFolder.getName().toLowerCase(Locale.ROOT)).replaceAll(REPLACEMENT);
        this.namespace = name.isEmpty() ? UNNAMED_PACK : name;
    }

    /** The namespace of every id this pack's resources have. */
    String namespace() {
        return namespace;
    }

    /** The pack itself: its namespace with the path {@code pack}. */
    ResourceId packId() {
        return new ResourceId(namespace, PACK_PATH);
    }

    /** {@code key} of a resource whose type its use makes clear, such as a biome in a biome sample. */
    ResourceId of(String key) {
        return known.computeIfAbsent(key, this::idOf);
    }

    /** {@code key} of a resource of {@code type}, such as {@code overworld:biome/desert/dunes}. */
    ResourceId of(String type, String key) {
        return of(type + "/" + key);
    }

    private ResourceId idOf(String key) {
        return new ResourceId(namespace, NOT_PATH.matcher(key.toLowerCase(Locale.ROOT)).replaceAll(REPLACEMENT));
    }
}
