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

package art.arcane.iris.pack.ingest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class IngestPack {
    private static final String SNIPPET_PREFIX = "snippet/";

    private final Path root;
    private final Map<String, JsonObject> loaded = new HashMap<>();
    private final Map<String, JsonObject> pending = new TreeMap<>();
    private final Set<String> generated = new TreeSet<>();

    IngestPack(Path root) {
        this.root = root;
    }

    Path root() {
        return root;
    }

    static String biomePath(String biomeKey) {
        return "biomes/" + biomeKey + ".json";
    }

    static String regionPath(String regionKey) {
        return "regions/" + regionKey + ".json";
    }

    static String dimensionPath(String dimensionKey) {
        return "dimensions/" + dimensionKey + ".json";
    }

    Optional<JsonObject> find(String relative) throws IOException {
        JsonObject cached = loaded.get(relative);
        if (cached != null) {
            return Optional.of(cached);
        }
        Path file = resolve(relative);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        JsonObject json = IngestJson.read(file);
        loaded.put(relative, json);
        return Optional.of(json);
    }

    JsonObject require(String relative) throws IOException {
        Optional<JsonObject> json = find(relative);
        if (json.isEmpty()) {
            throw new IOException("Pack file is missing: " + resolve(relative));
        }
        return json.get();
    }

    JsonObject edit(String relative) throws IOException {
        JsonObject json = require(relative);
        pending.put(relative, json);
        return json;
    }

    void create(String relative, JsonObject json) throws IOException {
        if (loaded.containsKey(relative) || Files.exists(resolve(relative), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ingest would overwrite an existing pack file: " + resolve(relative));
        }
        loaded.put(relative, json);
        pending.put(relative, json);
        generated.add(relative);
    }

    JsonObject snippet(JsonElement value, String snippetType) throws IOException {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonObject()) {
            return value.getAsJsonObject();
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        String reference = value.getAsString();
        if (!reference.startsWith(SNIPPET_PREFIX)) {
            return null;
        }
        String base = SNIPPET_PREFIX + snippetType + "/";
        String key = reference.startsWith(base) ? reference : base + reference.substring(SNIPPET_PREFIX.length());
        return find(key + ".json").map(JsonObject::deepCopy).orElse(null);
    }

    List<String> generatedFiles() {
        return new ArrayList<>(generated);
    }

    List<String> modifiedFiles() {
        List<String> modified = new ArrayList<>();
        for (String relative : pending.keySet()) {
            if (!generated.contains(relative)) {
                modified.add(relative);
            }
        }
        return modified;
    }

    void flush() throws IOException {
        for (Map.Entry<String, JsonObject> entry : pending.entrySet()) {
            write(entry.getKey(), entry.getValue());
        }
    }

    void write(String relative, JsonElement json) throws IOException {
        Path file = resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, IngestJson.format(json), StandardCharsets.UTF_8);
    }

    private Path resolve(String relative) throws IOException {
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root)) {
            throw new IOException("Pack path escapes the pack root: " + relative);
        }
        return file;
    }
}
