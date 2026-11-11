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

package art.arcane.iris.core.modules.manifest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A pack's {@code modules.json}, which its author writes: the modules the pack uses with the version of each it was built
 * and tested with, and the modules Iris must leave as they are. Iris reads it and never rewrites it; what is installed
 * right now is in {@link PackModuleLock}.
 *
 * @param modules the version the pack was built with, by module id, in the file's order
 * @param pinned  the ids of the modules Iris never updates
 */
public record PackModuleManifest(Map<String, String> modules, Set<String> pinned) {
    public static final String FILE_NAME = "modules.json";
    private static final String MODULES_FIELD = "modules";
    private static final String PINNED_FIELD = "pinned";

    public PackModuleManifest {
        modules = Collections.unmodifiableMap(new LinkedHashMap<>(modules));
        pinned = Collections.unmodifiableSet(new LinkedHashSet<>(pinned));
    }

    /** A manifest that lists every module Iris ships at the version it ships, with nothing pinned. */
    public static PackModuleManifest listingAll(ShippedModules shipped) {
        Map<String, String> modules = new LinkedHashMap<>();
        shipped.jars().forEach(jar -> jar.modules().forEach(module -> modules.put(module.id(), module.version())));
        return new PackModuleManifest(modules, Set.of());
    }

    /**
     * Empty for a pack without the file.
     *
     * @throws ManifestFormatException when the file is not a manifest, naming what to fix
     */
    public static Optional<PackModuleManifest> read(Path packFolder) throws IOException {
        Path file = packFolder.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(parse(Files.readString(file, StandardCharsets.UTF_8)));
    }

    public void write(Path packFolder) throws IOException {
        Files.writeString(packFolder.resolve(FILE_NAME), toJson(), StandardCharsets.UTF_8);
    }

    public boolean lists(String moduleId) {
        return modules.containsKey(moduleId);
    }

    public boolean pins(String moduleId) {
        return pinned.contains(moduleId);
    }

    static PackModuleManifest parse(String json) {
        JsonObject root = rootObject(json);
        return new PackModuleManifest(versionsIn(root), idsIn(root));
    }

    String toJson() {
        JsonObject root = new JsonObject();
        JsonObject versions = new JsonObject();
        modules.forEach(versions::addProperty);
        JsonArray pinnedIds = new JsonArray();
        pinned.forEach(pinnedIds::add);
        root.add(MODULES_FIELD, versions);
        root.add(PINNED_FIELD, pinnedIds);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
    }

    private static JsonObject rootObject(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonObject()) {
                return root.getAsJsonObject();
            }
        } catch (JsonParseException malformed) {
            throw new ManifestFormatException(FILE_NAME + " is not valid JSON: " + malformed.getMessage());
        }
        throw new ManifestFormatException(FILE_NAME + " must be a JSON object with a \"" + MODULES_FIELD + "\" object");
    }

    private static Map<String, String> versionsIn(JsonObject root) {
        JsonElement listed = root.get(MODULES_FIELD);
        if (listed == null || !listed.isJsonObject()) {
            throw new ManifestFormatException(FILE_NAME + " needs a \"" + MODULES_FIELD
                    + "\" object that maps each module id to a version, such as {\"iris:terrain\": \"1.0.0\"}");
        }
        Map<String, String> versions = new LinkedHashMap<>();
        listed.getAsJsonObject().entrySet().forEach(entry -> versions.put(entry.getKey(), versionOf(entry.getKey(), entry.getValue())));
        return versions;
    }

    private static String versionOf(String moduleId, JsonElement version) {
        if (version.isJsonPrimitive() && version.getAsJsonPrimitive().isString()) {
            return version.getAsString();
        }
        throw new ManifestFormatException(FILE_NAME + " gives " + moduleId + " the version " + version
                + "; a version is text, such as \"1.0.0\"");
    }

    private static Set<String> idsIn(JsonObject root) {
        JsonElement listed = root.get(PINNED_FIELD);
        if (listed == null) {
            return Set.of();
        }
        if (!listed.isJsonArray()) {
            throw new ManifestFormatException(FILE_NAME + " has a \"" + PINNED_FIELD
                    + "\" that is not a list of module ids, such as [\"iris:carve\"]");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement id : listed.getAsJsonArray()) {
            if (!id.isJsonPrimitive()) {
                throw new ManifestFormatException(FILE_NAME + " pins " + id + "; \"" + PINNED_FIELD
                        + "\" lists module ids as text, such as [\"iris:carve\"]");
            }
            ids.add(id.getAsString());
        }
        return ids;
    }

    /** A {@code modules.json} that is not a manifest. The message says what to fix and does not name the pack. */
    public static final class ManifestFormatException extends RuntimeException {
        ManifestFormatException(String message) {
            super(message);
        }
    }
}
