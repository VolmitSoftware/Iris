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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ZedProjectSettingsTest {
    private static final String BIOME_SCHEMA_TEXT = "{\"enum\": [\"land/plains\"]}";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsSettingsFileWithSchemasWhenMissing() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        ZedProjectSettings zed = new ZedProjectSettings(pack);

        zed.writeIrisSettings(biomeSchemas(), List.of(BIOME_SCHEMA_TEXT));

        assertTrue("Zed settings must be created", Files.isRegularFile(zed.getSettingsFile()));
        JsonObject settings = readSettings(zed.getSettingsFile());
        assertEquals(JsonParser.parseString(biomeSchemas().toString()), schemasIn(settings));
        assertEquals(JsonParser.parseString("[\"json\"]"), jsoncGlobsIn(settings));
    }

    @Test
    public void mergeReplacesSchemasAndPreservesForeignKeys() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        ZedProjectSettings zed = new ZedProjectSettings(pack);
        Files.createDirectories(zed.getSettingsFile().getParent());
        Files.writeString(zed.getSettingsFile(), """
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

        zed.writeIrisSettings(biomeSchemas(), List.of(BIOME_SCHEMA_TEXT));

        JsonObject settings = readSettings(zed.getSettingsFile());
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

        zed.writeIrisSettings(biomeSchemas(), List.of(BIOME_SCHEMA_TEXT));

        assertEquals(jsonc, Files.readString(zed.getSettingsFile(), StandardCharsets.UTF_8));
    }

    @Test
    public void unchangedSchemasDoNotRewriteTheFile() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        ZedProjectSettings zed = new ZedProjectSettings(pack);
        zed.writeIrisSettings(biomeSchemas(), List.of(BIOME_SCHEMA_TEXT));
        Path file = zed.getSettingsFile();
        String first = Files.readString(file, StandardCharsets.UTF_8);
        FileTime stamp = FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() - 60_000L);
        Files.setLastModifiedTime(file, stamp);

        zed.writeIrisSettings(biomeSchemas(), List.of(BIOME_SCHEMA_TEXT));

        assertEquals(first, Files.readString(file, StandardCharsets.UTF_8));
        assertEquals("Unchanged Zed settings must not be rewritten", stamp.toMillis(), Files.getLastModifiedTime(file).toMillis());
    }

    @Test
    public void changedSchemaContentChangesTheSchemaHash() throws Exception {
        ZedProjectSettings zed = new ZedProjectSettings(temporaryFolder.newFolder("pack"));
        zed.writeIrisSettings(biomeSchemas(), List.of(BIOME_SCHEMA_TEXT));
        String firstHash = schemaHashIn(readSettings(zed.getSettingsFile()));

        zed.writeIrisSettings(biomeSchemas(), List.of(BIOME_SCHEMA_TEXT.replace("land/plains", "land/meadow")));

        String secondHash = schemaHashIn(readSettings(zed.getSettingsFile()));
        assertNotEquals("A regenerated schema must change Zed's JSON server configuration", firstHash, secondHash);
    }

    @Test
    public void schemaHashIsAStableSixteenDigitHexOfTheTexts() throws Exception {
        ZedProjectSettings zed = new ZedProjectSettings(temporaryFolder.newFolder("pack"));

        zed.writeIrisSettings(biomeSchemas(), List.of("a", "b"));
        String hash = schemaHashIn(readSettings(zed.getSettingsFile()));
        zed.writeIrisSettings(biomeSchemas(), List.of("a", "b"));

        assertTrue(hash.matches("[0-9a-f]{16}"));
        assertEquals(hash, schemaHashIn(readSettings(zed.getSettingsFile())));
    }

    @Test
    public void textBoundariesChangeTheSchemaHash() throws Exception {
        ZedProjectSettings zed = new ZedProjectSettings(temporaryFolder.newFolder("pack"));
        zed.writeIrisSettings(biomeSchemas(), List.of("ab", "c"));
        String firstHash = schemaHashIn(readSettings(zed.getSettingsFile()));

        zed.writeIrisSettings(biomeSchemas(), List.of("a", "bc"));

        assertNotEquals(firstHash, schemaHashIn(readSettings(zed.getSettingsFile())));
    }

    private static String schemaHashIn(JsonObject settings) {
        return settings.getAsJsonObject("lsp").getAsJsonObject("json-language-server")
                .getAsJsonObject("settings").get("irisSchemaHash").getAsString();
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
