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

import art.arcane.iris.structure.nativegen.IrisImportedFeatureControl;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImportedFeatureWriterTest {
    @Test
    public void sourceThatAlreadyRanImportedFeaturesKeepsVanillaAndOtherModsGenerating() {
        JsonObject dimension = dimension("{\"importedFeatures\": {\"enabled\": true, \"disabled\": [\"minecraft:ore_diamond\"]}}");

        IrisImportedFeatureControl control = ImportedFeatureWriter.write(dimension, Set.of("examplemod:ore_tin"), Set.of("othermod"), Set.of());

        JsonObject imported = dimension.getAsJsonObject("importedFeatures");
        assertEquals(List.of("minecraft:ore_diamond"), ModIngestFixture.strings(imported.get("disabled")));
        assertEquals(List.of("examplemod:ore_tin"), ModIngestFixture.strings(imported.get("disabledExact")));
        assertTrue(control.shouldGenerate("minecraft:ore_iron"));
        assertTrue(control.shouldGenerate("othermod:daisies"));
        assertFalse(control.shouldGenerate("minecraft:ore_diamond"));
        assertFalse(control.shouldGenerate("examplemod:ore_tin"));
    }

    @Test
    public void sourceWithoutImportedFeaturesOnlyRunsTheSelectedModsPassthrough() {
        JsonObject dimension = dimension("{}");

        IrisImportedFeatureControl control = ImportedFeatureWriter.write(dimension, Set.of("examplemod:ore_tin"), Set.of("othermod"), Set.of());

        JsonObject imported = dimension.getAsJsonObject("importedFeatures");
        assertTrue(imported.get("enabled").getAsBoolean());
        assertEquals(List.of("minecraft:", "othermod:"), ModIngestFixture.strings(imported.get("disabled")));
        assertFalse(control.shouldGenerate("minecraft:ore_iron"));
        assertFalse(control.shouldGenerate("othermod:daisies"));
        assertFalse(control.shouldGenerate("examplemod:ore_tin"));
        assertTrue(control.shouldGenerate("examplemod:ore_tin_large"));
        assertTrue(control.shouldGenerate("examplemod:maple_trees"));
    }

    @Test
    public void existingExactEntriesAreKept() {
        JsonObject dimension = dimension("{\"importedFeatures\": {\"enabled\": false, \"disabledExact\": [\"examplemod:old\"]}}");

        ImportedFeatureWriter.write(dimension, Set.of("examplemod:ore_tin"), Set.of(), Set.of());

        assertEquals(List.of("examplemod:old", "examplemod:ore_tin"),
                ModIngestFixture.strings(dimension.getAsJsonObject("importedFeatures").get("disabledExact")));
    }

    private static JsonObject dimension(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
