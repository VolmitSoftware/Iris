package art.arcane.iris.studio.workspace;

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
        File pack = temporaryFolder.newFolder("pack");
        ZedProjectSettings zed = new ZedProjectSettings(pack);

        zed.writeJsonSchemas(biomeSchemas());

        assertTrue("Zed settings must be created", Files.isRegularFile(zed.getSettingsFile()));
        assertEquals(JsonParser.parseString(biomeSchemas().toString()), schemasIn(readSettings(zed.getSettingsFile())));
    }

    @Test
    public void mergeReplacesSchemasAndPreservesForeignKeys() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        ZedProjectSettings zed = new ZedProjectSettings(pack);
        Files.createDirectories(zed.getSettingsFile().getParent());
        Files.writeString(zed.getSettingsFile(), """
                {
                  "tab_size": 2,
                  "lsp": {
                    "rust-analyzer": { "initialization_options": { "check": { "command": "clippy" } } },
                    "json-language-server": {
                      "binary": { "path": "/opt/json-ls" },
                      "settings": { "json": { "format": { "enable": true }, "schemas": [{ "fileMatch": ["/stale/*.json"], "url": "./stale.json" }] } }
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        zed.writeJsonSchemas(biomeSchemas());

        JsonObject settings = readSettings(zed.getSettingsFile());
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
        File pack = temporaryFolder.newFolder("pack");
        ZedProjectSettings zed = new ZedProjectSettings(pack);
        Files.createDirectories(zed.getSettingsFile().getParent());
        String jsonc = """
                // Folder-specific settings
                {
                  "tab_size": 2,
                }
                """;
        Files.writeString(zed.getSettingsFile(), jsonc, StandardCharsets.UTF_8);

        zed.writeJsonSchemas(biomeSchemas());

        assertEquals(jsonc, Files.readString(zed.getSettingsFile(), StandardCharsets.UTF_8));
    }

    @Test
    public void unchangedSchemasDoNotRewriteTheFile() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        ZedProjectSettings zed = new ZedProjectSettings(pack);
        zed.writeJsonSchemas(biomeSchemas());
        Path file = zed.getSettingsFile();
        String first = Files.readString(file, StandardCharsets.UTF_8);
        FileTime stamp = FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() - 60_000L);
        Files.setLastModifiedTime(file, stamp);

        zed.writeJsonSchemas(biomeSchemas());

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

    private static JsonElement schemasIn(JsonObject settings) {
        return settings.getAsJsonObject("lsp").getAsJsonObject("json-language-server")
                .getAsJsonObject("settings").getAsJsonObject("json").get("schemas");
    }
}
