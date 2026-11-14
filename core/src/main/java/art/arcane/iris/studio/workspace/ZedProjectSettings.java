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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Keeps the JSON schema mappings in a pack's Zed project settings in sync with the VSCode workspace.
 * The file belongs to the user, so Iris only replaces {@code lsp.json-language-server.settings.json.schemas}
 * and {@code lsp.json-language-server.settings.irisSchemaHash}, and makes sure {@code file_types.JSONC} contains {@code json}.
 */
public final class ZedProjectSettings {
    private static final List<String> JSON_SERVER_SETTINGS_PATH = List.of("lsp", "json-language-server", "settings");
    private static final List<String> SCHEMAS_PARENT_PATH = List.of("lsp", "json-language-server", "settings", "json");
    private static final String SCHEMAS_KEY = "schemas";
    // Zed's JSON server caches schema files until its configuration changes, and Zed never reports schema file edits.
    // Carrying a hash of the schema contents in the configuration makes regenerated schemas reach the editor.
    private static final String SCHEMA_HASH_KEY = "irisSchemaHash";
    private static final int SCHEMA_HASH_HEX_LENGTH = 16;
    private static final List<String> FILE_TYPES_PATH = List.of("file_types");
    // Pack files may contain comments; Zed's JSONC language keeps schema support but stops flagging them.
    private static final String JSONC_LANGUAGE = "JSONC";
    private static final String JSON_EXTENSION = "json";
    // Strict parsing rejects JSONC comments and trailing commas instead of silently dropping them on rewrite.
    private static final Gson STRICT_GSON = new GsonBuilder()
            .setStrictness(Strictness.STRICT)
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private final Path settingsFile;

    public ZedProjectSettings(File projectRoot) {
        this.settingsFile = projectRoot.toPath().resolve(".zed").resolve("settings.json");
    }

    Path getSettingsFile() {
        return settingsFile;
    }

    public void writeIrisSettings(JSONArray schemas, List<String> schemaTexts) throws IOException {
        String existing = Files.isRegularFile(settingsFile) ? Files.readString(settingsFile, StandardCharsets.UTF_8) : null;
        JsonObject settings = parseOrNull(existing);
        if (settings == null) {
            IrisLogging.warn("Could not parse " + settingsFile + " as strict JSON (comments or trailing commas?); leaving it untouched.");
            return;
        }

        if (!mergeSchemas(settings, schemas) || !mergeSchemaHash(settings, schemaTexts) || !mergeJsoncFileType(settings)) {
            IrisLogging.warn("Could not merge Iris settings into " + settingsFile + ": " + String.join(".", SCHEMAS_PARENT_PATH)
                    + " must be an object and " + String.join(".", FILE_TYPES_PATH) + "." + JSONC_LANGUAGE + " a list; leaving it untouched.");
            return;
        }

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

    private static boolean mergeSchemas(JsonObject settings, JSONArray schemas) {
        JsonObject schemasParent = findOrCreateObject(settings, SCHEMAS_PARENT_PATH);
        if (schemasParent == null) {
            return false;
        }
        schemasParent.add(SCHEMAS_KEY, JsonParser.parseString(schemas.toString()));
        return true;
    }

    private static boolean mergeSchemaHash(JsonObject settings, List<String> schemaTexts) {
        JsonObject jsonServerSettings = findOrCreateObject(settings, JSON_SERVER_SETTINGS_PATH);
        if (jsonServerSettings == null) {
            return false;
        }
        jsonServerSettings.addProperty(SCHEMA_HASH_KEY, hashSchemaTexts(schemaTexts));
        return true;
    }

    private static String hashSchemaTexts(List<String> schemaTexts) {
        MessageDigest digest = sha256();
        for (String schemaText : schemaTexts) {
            digest.update(schemaText.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest()).substring(0, SCHEMA_HASH_HEX_LENGTH);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime must provide SHA-256", e);
        }
    }

    private static boolean mergeJsoncFileType(JsonObject settings) {
        JsonObject fileTypes = findOrCreateObject(settings, FILE_TYPES_PATH);
        if (fileTypes == null) {
            return false;
        }
        JsonElement jsoncGlobs = fileTypes.get(JSONC_LANGUAGE);
        if (jsoncGlobs == null || jsoncGlobs.isJsonNull()) {
            jsoncGlobs = new JsonArray();
            fileTypes.add(JSONC_LANGUAGE, jsoncGlobs);
        }
        if (!jsoncGlobs.isJsonArray()) {
            return false;
        }
        JsonPrimitive jsonExtension = new JsonPrimitive(JSON_EXTENSION);
        if (!jsoncGlobs.getAsJsonArray().contains(jsonExtension)) {
            jsoncGlobs.getAsJsonArray().add(jsonExtension);
        }
        return true;
    }

    private static JsonObject findOrCreateObject(JsonObject settings, List<String> path) {
        JsonObject current = settings;
        for (String key : path) {
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
