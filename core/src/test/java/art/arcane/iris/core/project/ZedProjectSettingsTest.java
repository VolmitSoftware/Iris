package art.arcane.iris.core.project;

import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZedProjectSettingsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsSettingsFileWithSchemasWhenMissing() throws Exception {
        ZedProjectSettings zed = new ZedProjectSettings(temporaryFolder.newFolder("pack"));

        zed.writeIrisSettings(biomeSchemas());

        assertTrue("Zed settings must be created", Files.isRegularFile(zed.settingsFile()));
        JsonObject settings = readSettings(zed.settingsFile());
        assertEquals(JsonParser.parseString(biomeSchemas().toString()), schemasIn(settings));
        assertEquals(JsonParser.parseString("[\"json\"]"), jsoncGlobsIn(settings));
    }

    @Test
    public void mergeReplacesSchemasAndPreservesForeignKeys() throws Exception {
        ZedProjectSettings zed = new ZedProjectSettings(temporaryFolder.newFolder("pack"));
        Files.createDirectories(zed.settingsFile().getParent());
        Files.writeString(zed.settingsFile(), """
                {
                  "tab_size": 2,
                  "file_types": { "JSONC": ["*.jsonc5"], "Rust": ["rs.in"] },
                  "lsp": {
                    "rust-analyzer": { "initialization_options": { "check": { "command": "clippy" } } },
                    "json-language-server": {
                      "binary": { "path": "/opt/json-ls" },
                      "settings": { "json": { "format": { "enable": true }, "schemas": [{ "fileMatch": ["/stale/*.json"], "url": "./stale.json" }] } }
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        zed.writeIrisSettings(biomeSchemas());

        JsonObject settings = readSettings(zed.settingsFile());
        assertEquals(JsonParser.parseString("[\"*.jsonc5\", \"json\"]"), jsoncGlobsIn(settings));
        assertEquals("rs.in", settings.getAsJsonObject("file_types").getAsJsonArray("Rust").get(0).getAsString());
        JsonObject jsonServer = settings.getAsJsonObject("lsp").getAsJsonObject("json-language-server");
        assertEquals(2, settings.get("tab_size").getAsInt());
        assertEquals("clippy", settings.getAsJsonObject("lsp").getAsJsonObject("rust-analyzer")
                .getAsJsonObject("initialization_options").getAsJsonObject("check").get("command").getAsString());
        assertEquals("/opt/json-ls", jsonServer.getAsJsonObject("binary").get("path").getAsString());
        assertTrue(jsonServer.getAsJsonObject("settings").getAsJsonObject("json").getAsJsonObject("format").get("enable").getAsBoolean());
        assertEquals(JsonParser.parseString(biomeSchemas().toString()), schemasIn(settings));
    }

    @Test
    public void unparseableSettingsFileIsLeftUntouched() throws Exception {
        ZedProjectSettings zed = new ZedProjectSettings(temporaryFolder.newFolder("pack"));
        Files.createDirectories(zed.settingsFile().getParent());
        String jsonc = """
                // Folder-specific settings
                {
                  "tab_size": 2,
                }
                """;
        Files.writeString(zed.settingsFile(), jsonc, StandardCharsets.UTF_8);

        zed.writeIrisSettings(biomeSchemas());

        assertEquals(jsonc, Files.readString(zed.settingsFile(), StandardCharsets.UTF_8));
    }

    @Test
    public void unchangedSchemasDoNotRewriteTheFile() throws Exception {
        ZedProjectSettings zed = new ZedProjectSettings(temporaryFolder.newFolder("pack"));
        zed.writeIrisSettings(biomeSchemas());
        Path file = zed.settingsFile();
        String first = Files.readString(file, StandardCharsets.UTF_8);
        FileTime stamp = FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() - 60_000L);
        Files.setLastModifiedTime(file, stamp);

        zed.writeIrisSettings(biomeSchemas());

        assertEquals(first, Files.readString(file, StandardCharsets.UTF_8));
        assertEquals("Unchanged Zed settings must not be rewritten", stamp.toMillis(), Files.getLastModifiedTime(file).toMillis());
    }

    private static JSONArray biomeSchemas() {
        JSONObject entry = new JSONObject();
        entry.put("fileMatch", new JSONArray(new Object[]{"/biomes/*.json", "/biomes/*/*.json"}));
        entry.put("url", "./.iris/schema/biomes-schema.json");
        JSONArray schemas = new JSONArray();
        schemas.put(entry);
        return schemas;
    }

    private static JsonObject readSettings(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonElement jsoncGlobsIn(JsonObject settings) {
        return settings.getAsJsonObject("file_types").get("JSONC");
    }

    private static JsonElement schemasIn(JsonObject settings) {
        return settings.getAsJsonObject("lsp").getAsJsonObject("json-language-server")
                .getAsJsonObject("settings").getAsJsonObject("json").get("schemas");
    }
}
