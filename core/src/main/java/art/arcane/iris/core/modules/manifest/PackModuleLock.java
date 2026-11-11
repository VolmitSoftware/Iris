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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A pack's {@code modules.lock.json}, which Iris writes: the version of every module in the pack's jars, recorded when
 * Iris installs or updates a jar and when the pack loads. The pack's author never edits it; {@link PackModuleManifest}
 * is theirs.
 *
 * @param installed the installed version by module id
 */
public record PackModuleLock(Map<String, String> installed) {
    public static final String FILE_NAME = "modules.lock.json";
    private static final PackModuleLock NOTHING_INSTALLED = new PackModuleLock(Map.of());

    public PackModuleLock {
        installed = Collections.unmodifiableMap(new TreeMap<>(installed));
    }

    /** What the file records; nothing for a pack without the file or with one Iris cannot read, which the next load rewrites. */
    public static PackModuleLock read(Path packFolder) throws IOException {
        Path file = packFolder.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return NOTHING_INSTALLED;
        }
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Writes the file unless it records the same already, so a load that changes nothing leaves the pack untouched. */
    public void writeIfChanged(Path packFolder) throws IOException {
        if (equals(read(packFolder))) {
            return;
        }
        Files.writeString(packFolder.resolve(FILE_NAME), toJson(), StandardCharsets.UTF_8);
    }

    public Optional<String> versionOf(String moduleId) {
        return Optional.ofNullable(installed.get(moduleId));
    }

    static PackModuleLock parse(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            return root.isJsonObject() ? new PackModuleLock(versionsIn(root.getAsJsonObject())) : NOTHING_INSTALLED;
        } catch (JsonParseException | UnsupportedOperationException | IllegalStateException unreadable) {
            return NOTHING_INSTALLED;
        }
    }

    String toJson() {
        JsonObject root = new JsonObject();
        installed.forEach(root::addProperty);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
    }

    private static Map<String, String> versionsIn(JsonObject root) {
        Map<String, String> versions = new TreeMap<>();
        root.entrySet().forEach(entry -> versions.put(entry.getKey(), entry.getValue().getAsString()));
        return versions;
    }
}
