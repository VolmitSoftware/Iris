package art.arcane.iris.studio.workspace;

import art.arcane.iris.pack.schema.SchemaBuilder;

import art.arcane.iris.structure.nativegen.IrisImportedStructureControl;
import art.arcane.iris.structure.placement.IrisStructureSetFrequencyOverride;
import art.arcane.iris.structure.nativegen.IrisVanillaStructureAdjustment;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.RegistryListVanillaStructure;
import art.arcane.iris.pack.schema.annotation.RegistryListVanillaStructureSet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * importedStructures.disabled and adjustments[].match accept family/namespace PREFIXES
 * ("minecraft:village", "nova_structures:") per IrisImportedStructureControl.matchesKey, so the
 * generated editor schema must not reject them with a strict registered-key enum. Prefix-capable
 * fields emit an anyOf of the registry enum plus a key/prefix pattern. Exact-key fields, including
 * importedStructures.disabledExact and nativeStructures[].structure, keep the strict enum.
 */
public class VanillaStructurePrefixSchemaTest {
    @Test
    public void prefixCapableFieldsDeclareThePrefixAnnotation() throws NoSuchFieldException {
        assertTrue(IrisImportedStructureControl.class.getDeclaredField("disabled")
                .getAnnotation(RegistryListVanillaStructure.class).prefixes());
        assertFalse(IrisImportedStructureControl.class.getDeclaredField("disabledExact")
                .getAnnotation(RegistryListVanillaStructure.class).prefixes());
        assertTrue(IrisVanillaStructureAdjustment.class.getDeclaredField("match")
                .getAnnotation(RegistryListVanillaStructure.class).prefixes());
        assertTrue(IrisStructureSetFrequencyOverride.class.getDeclaredField("structureSet")
                .isAnnotationPresent(RegistryListVanillaStructureSet.class));
    }

    @Test
    public void prefixListSchemaAcceptsFamilyAndNamespacePrefixes() {
        JSONObject schema = new SchemaBuilder(PrefixModel.class, null).construct();
        JSONObject items = schema.getJSONObject("properties").getJSONObject("disabled")
                .getJSONObject("items");
        String definitionKey = items.getString("$ref").substring("#/definitions/".length());
        JSONArray anyOf = schema.getJSONObject("definitions").getJSONObject(definitionKey)
                .getJSONArray("anyOf");

        String pattern = null;
        for (int i = 0; i < anyOf.length(); i++) {
            JSONObject branch = anyOf.getJSONObject(i);
            if (branch.has("pattern")) {
                pattern = branch.getString("pattern");
            }
        }
        assertTrue("anyOf must contain a pattern branch for prefixes", pattern != null);

        Pattern compiled = Pattern.compile(pattern);
        assertTrue(compiled.matcher("minecraft:village").matches());
        assertTrue(compiled.matcher("minecraft:pillager_outpost").matches());
        assertTrue(compiled.matcher("nova_structures:").matches());
        assertTrue(compiled.matcher("towns_and_towers:exclusives/village_piglin").matches());
        assertFalse(compiled.matcher("village").matches());
        assertFalse(compiled.matcher("Nova Structures:tavern").matches());
    }

    @Description("Schema model for prefix-capable vanilla structure lists.")
    public static class PrefixModel {
        @RegistryListVanillaStructure(prefixes = true)
        @ArrayType(type = String.class, min = 1)
        @Description("Prefix-capable deny list.")
        private KList<String> disabled = new KList<>();
    }
}
