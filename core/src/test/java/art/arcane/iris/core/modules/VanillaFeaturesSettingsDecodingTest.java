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

import art.arcane.iris.modules.vanillafeatures.ImportedFeaturesSettings;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.volmit.config.Level;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import com.volmit.vanilla.DecorationStep;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** A dimension's {@code importedFeatures} as packs have always written it, read into {@link ImportedFeaturesSettings}. */
public class VanillaFeaturesSettingsDecodingTest {
    private static final String DIMENSION = """
            {"importedFeatures": {
              "enabled": true,
              "disabled": ["minecraft:ore", "minecraft:trees_plains"],
              "steps": ["UNDERGROUND_ORES", "VEGETAL_DECORATION"],
              "disabledSteps": ["VEGETAL_DECORATION"]
            }}
            """;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private LoadedPack pack;
    private ModuleSettingsDecoder decoder;

    @Before
    public void loadBuiltIns() throws IOException {
        Path packFolder = folder.newFolder("pack").toPath();
        pack = LoadedPack.load(packFolder, ModuleHost.of(BuiltInPack.allBuiltIns())
                .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGE).copyingJarsTo(folder.newFolder("copies").toPath()));
        decoder = new ModuleSettingsDecoder(pack.catalog(), packFolder, Gson::new);
    }

    @After
    public void closePack() {
        pack.close();
    }

    @Test
    public void everyOptionReadsAsPacksWroteIt() {
        ImportedFeaturesSettings settings = settings(DIMENSION);

        assertTrue(settings.enabled());
        assertEquals(List.of("minecraft:ore", "minecraft:trees_plains"), settings.disabled());
        assertEquals(List.of(DecorationStep.UNDERGROUND_ORES, DecorationStep.VEGETAL_DECORATION), settings.steps());
        assertEquals(List.of(DecorationStep.VEGETAL_DECORATION), settings.disabledSteps());
    }

    @Test
    public void emptySettingsAreOffAndTheFeatureIdIsNotAKey() {
        ImportedFeaturesSettings defaults = settings("{\"importedFeatures\": {}}");
        JSONObject byId = new JSONObject("{\"iris:vanilla-features\": {\"enabled\": true}}");

        assertFalse(defaults.enabled());
        assertEquals(List.of(), defaults.disabled());
        assertEquals(List.of(), defaults.steps());
        assertEquals(List.of(), defaults.disabledSteps());
        assertSame(ModuleSettings.NONE, decoder.decode(Level.DIMENSION, byId, "dimension overworld"));
        assertTrue(decoder.keys(Level.DIMENSION).contains("importedFeatures"));
    }

    @Test
    public void aBareSwitchIsRefusedNamingTheKey() {
        JSONObject bare = new JSONObject("{\"importedFeatures\": true}");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> decoder.decode(Level.DIMENSION, bare, "dimension overworld"));

        assertEquals("Cannot read settings iris:vanilla-features under \"importedFeatures\" in dimension overworld:"
                + " write the settings as an object, {\"<option>\": ...}", refused.getMessage());
    }

    private ImportedFeaturesSettings settings(String dimensionJson) {
        return decoder.decode(Level.DIMENSION, new JSONObject(dimensionJson), "dimension overworld")
                .find(ImportedFeaturesSettings.class).orElseThrow();
    }
}
