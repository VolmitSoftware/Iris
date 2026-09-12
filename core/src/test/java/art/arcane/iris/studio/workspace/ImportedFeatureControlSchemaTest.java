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

package art.arcane.iris.studio.workspace;

import art.arcane.iris.pack.schema.SchemaBuilder;

import art.arcane.iris.generation.decoration.IrisDecorationStep;
import art.arcane.iris.structure.nativegen.IrisImportedFeatureControl;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ImportedFeatureControlSchemaTest {
    @Test
    public void everyDecorationStepIsSchemaDescribed() throws NoSuchFieldException {
        assertNotNull(IrisDecorationStep.class.getAnnotation(Description.class));
        for (IrisDecorationStep step : IrisDecorationStep.values()) {
            assertNotNull(step.name(),
                    IrisDecorationStep.class.getField(step.name()).getAnnotation(Description.class));
        }
    }

    @Test
    public void controlSchemaExposesEnabledFlagAndStepEnums() {
        JSONObject schema = new SchemaBuilder(IrisImportedFeatureControl.class, null).construct();
        JSONObject properties = schema.getJSONObject("properties");

        assertEquals("boolean", properties.getJSONObject("enabled").getString("type"));
        assertEquals("array", properties.getJSONObject("disabled").getString("type"));
        assertEquals(1, properties.getJSONObject("disabled").getInt("minItems"));

        for (String field : List.of("steps", "disabledSteps")) {
            JSONObject list = properties.getJSONObject(field);
            assertEquals("array", list.getString("type"));
            String definitionKey = list.getJSONObject("items")
                    .getString("$ref").substring("#/definitions/".length());
            JSONArray values = schema.getJSONObject("definitions")
                    .getJSONObject(definitionKey)
                    .getJSONArray("oneOf");
            List<String> constants = new ArrayList<>();
            for (int index = 0; index < values.length(); index++) {
                JSONObject entry = values.getJSONObject(index);
                constants.add(entry.getString("const"));
                assertTrue(field + " " + entry.getString("const"),
                        entry.getString("description").length() > 0);
            }
            List<String> expected = new ArrayList<>();
            for (IrisDecorationStep step : IrisDecorationStep.values()) {
                expected.add(step.name());
            }
            assertEquals(expected, constants);
        }
    }

    @Test
    public void dimensionSchemaCarriesTheControlBlock() {
        JSONObject schema = new SchemaBuilder(ControlHolder.class, null).construct();
        JSONObject importedFeatures = schema.getJSONObject("properties").getJSONObject("importedFeatures");

        assertTrue(importedFeatures.has("$ref") || importedFeatures.has("properties")
                || importedFeatures.has("anyOf"));
    }

    @Description("Schema model for the imported feature control block.")
    public static class ControlHolder {
        @Description("Controls native placed feature generation.")
        private IrisImportedFeatureControl importedFeatures = new IrisImportedFeatureControl();
    }
}
