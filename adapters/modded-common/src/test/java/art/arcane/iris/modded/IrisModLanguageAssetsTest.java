package art.arcane.iris.modded;

import art.arcane.volmlib.util.localization.VolmitLocales;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IrisModLanguageAssetsTest {
    private static final String ROOT = "assets/irisworldgen/lang/";
    private static final String TOGGLE_KEY = "key.irisworldgen.toggle_pregen_hud";

    @Test
    public void minecraftLanguageAssetsMatchSharedLocaleManifest() throws Exception {
        JsonObject english = read("en_us");
        assertEquals(4, english.size());

        for (String locale : VolmitLocales.nonEnglish()) {
            String minecraftLocale = VolmitLocales.minecraftCode(locale);
            JsonObject translated = read(minecraftLocale);
            assertEquals(minecraftLocale, english.keySet(), translated.keySet());
            for (String key : english.keySet()) {
                JsonElement value = translated.get(key);
                assertTrue(minecraftLocale + ": " + key, value.isJsonPrimitive());
                assertTrue(minecraftLocale + ": " + key, value.getAsJsonPrimitive().isString());
                assertFalse(minecraftLocale + ": " + key, value.getAsString().isBlank());
            }
            assertFalse(
                    minecraftLocale + " contains an English HUD label",
                    english.get(TOGGLE_KEY).getAsString().equals(translated.get(TOGGLE_KEY).getAsString())
            );
        }
    }

    @Test
    public void minecraftLanguageResourceSetMatchesSharedLocaleManifestAndEnglishBaseline() throws Exception {
        Set<String> expected = VolmitLocales.nonEnglish().stream()
                .map(VolmitLocales::minecraftCode)
                .map(locale -> locale + ".json")
                .collect(Collectors.toCollection(LinkedHashSet::new));
        expected.add("en_us.json");

        assertEquals(expected, resourceFiles());
    }

    private JsonObject read(String locale) throws Exception {
        String resource = ROOT + locale + ".json";
        InputStream input = IrisModLanguageAssetsTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull("Missing mod language asset: " + resource, input);
        try (InputStream stream = input;
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            assertTrue("Mod language asset is not an object: " + resource, parsed.isJsonObject());
            return parsed.getAsJsonObject();
        }
    }

    private Set<String> resourceFiles() throws Exception {
        URL resource = IrisModLanguageAssetsTest.class.getClassLoader().getResource(ROOT);
        assertNotNull("Missing mod language resource directory: " + ROOT, resource);
        assertEquals("Mod language resources must resolve to a directory on disk", "file", resource.getProtocol());
        Path directory = Path.of(resource.toURI());
        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}
