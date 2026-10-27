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

package art.arcane.iris.studio.workspace;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.json.JSONArray;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Keeps the JSON schema mappings in a pack's Zed project settings in sync with the VSCode workspace.
 * The file belongs to the user, so only {@code lsp.json-language-server.settings.json.schemas} is replaced.
 */
final class ZedProjectSettings {
    private static final List<String> SCHEMAS_PARENT_PATH = List.of("lsp", "json-language-server", "settings", "json");
    private static final String SCHEMAS_KEY = "schemas";
    // Strict parsing rejects JSONC comments and trailing commas instead of silently dropping them on rewrite.
    private static final Gson STRICT_GSON = new GsonBuilder()
            .setStrictness(Strictness.STRICT)
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private final Path settingsFile;

    ZedProjectSettings(File projectRoot) {
        this.settingsFile = projectRoot.toPath().resolve(".zed").resolve("settings.json");
    }

    Path getSettingsFile() {
        return settingsFile;
    }

    void writeJsonSchemas(JSONArray schemas) throws IOException {
        String existing = Files.isRegularFile(settingsFile) ? Files.readString(settingsFile, StandardCharsets.UTF_8) : null;
        JsonObject settings = parseOrNull(existing);
        if (settings == null) {
            IrisLogging.warn("Could not parse " + settingsFile + " as strict JSON (comments or trailing commas?); leaving it untouched.");
            return;
        }

        JsonObject schemasParent = findOrCreateSchemasParent(settings);
        if (schemasParent == null) {
            IrisLogging.warn("Could not merge JSON schemas into " + settingsFile + ": an entry on the path " + String.join(".", SCHEMAS_PARENT_PATH) + " is not an object; leaving it untouched.");
            return;
        }

        schemasParent.add(SCHEMAS_KEY, JsonParser.parseString(schemas.toString()));
        writeIfChanged(STRICT_GSON.toJson(settings) + "\n", existing);
    }

    private static JsonObject parseOrNull(String existing) {
        if (existing == null || existing.isBlank()) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = STRICT_GSON.fromJson(existing, JsonElement.class);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    private static JsonObject findOrCreateSchemasParent(JsonObject settings) {
        JsonObject current = settings;
        for (String key : SCHEMAS_PARENT_PATH) {
            JsonElement child = current.get(key);
            if (child == null || child.isJsonNull()) {
                child = new JsonObject();
                current.add(key, child);
            }
            if (!child.isJsonObject()) {
                return null;
            }
            current = child.getAsJsonObject();
        }
        return current;
    }

    private void writeIfChanged(String rendered, String existing) throws IOException {
        if (rendered.equals(existing)) {
            return;
        }
        Files.createDirectories(settingsFile.getParent());
        Files.writeString(settingsFile, rendered, StandardCharsets.UTF_8);
    }
}
