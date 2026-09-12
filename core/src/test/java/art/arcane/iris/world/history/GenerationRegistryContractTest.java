package art.arcane.iris.world.history;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class GenerationRegistryContractTest {
    private static final String DEFINITION_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String DEFINITION_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void canonicalizesPhysicalResourceKeysAndRetainsCurrentFingerprint() {
        GenerationRegistryContract.PhysicalResourceKey plains = key(
                "minecraft:worldgen/biome",
                "minecraft:plains"
        );
        GenerationRegistryContract.PhysicalResourceKey desert = key(
                "minecraft:worldgen/biome",
                "minecraft:desert"
        );
        LinkedHashMap<GenerationRegistryContract.PhysicalResourceKey, String> reverseOrder = new LinkedHashMap<>();
        reverseOrder.put(plains, DEFINITION_A);
        reverseOrder.put(desert, DEFINITION_B);

        GenerationRegistryContract contract = GenerationRegistryContract.fromDefinitions(
                GenerationRegistryContract.FINGERPRINT_SCHEMA_VERSION_ONE,
                reverseOrder
        );

        assertEquals(
                "bf3d52711738c90740c8ac35d7462041625b42705cc0ce7dd82dbd59e0a8244a",
                contract.fingerprint()
        );
        assertEquals(desert, contract.definitions().firstKey());
        assertEquals(plains, contract.definitions().lastKey());
        assertThrows(
                UnsupportedOperationException.class,
                () -> contract.definitions().put(key("minecraft:block", "minecraft:stone"), DEFINITION_A)
        );
    }

    @Test
    public void biomeTagsAreCanonicalPersistedAndFingerprintProtected() {
        GenerationRegistryContract base = GenerationRegistryContract.fromDefinitions(
                Map.of(key("minecraft:worldgen/biome", "iris:old"), DEFINITION_A));
        GenerationRegistryContract tagged = base.withBiomeTags(Map.of("iris:old",
                List.of("minecraft:is_overworld", "iris:humid", "iris:humid")));
        assertEquals(List.of("iris:humid", "minecraft:is_overworld"), tagged.biomeTags().get("iris:old"));
        assertNotEquals(base.fingerprint(), tagged.fingerprint());
        assertEquals(tagged, GenerationRegistryContract.fromJson(tagged.toJson()));
        JsonObject tampered = tagged.toJson();
        tampered.getAsJsonObject("biomeTags").getAsJsonArray("iris:old").add("iris:new");
        assertThrows(IllegalArgumentException.class, () -> GenerationRegistryContract.fromJson(tampered));
        assertThrows(IllegalArgumentException.class, () -> base.withBiomeTags(Map.of("iris:missing", List.of("iris:humid"))));
    }

    @Test
    public void derivesStableFingerprintIndependentOfMapIterationOrder() {
        GenerationRegistryContract.PhysicalResourceKey plains = key(
                "minecraft:worldgen/biome",
                "minecraft:plains"
        );
        GenerationRegistryContract.PhysicalResourceKey desert = key(
                "minecraft:worldgen/biome",
                "minecraft:desert"
        );
        LinkedHashMap<GenerationRegistryContract.PhysicalResourceKey, String> firstOrder = new LinkedHashMap<>();
        firstOrder.put(plains, DEFINITION_A);
        firstOrder.put(desert, DEFINITION_B);
        LinkedHashMap<GenerationRegistryContract.PhysicalResourceKey, String> secondOrder = new LinkedHashMap<>();
        secondOrder.put(desert, DEFINITION_B);
        secondOrder.put(plains, DEFINITION_A);

        GenerationRegistryContract first = GenerationRegistryContract.fromDefinitions(firstOrder);
        GenerationRegistryContract second = GenerationRegistryContract.fromDefinitions(secondOrder);

        assertEquals(first, second);
        assertEquals(first.fingerprint(), second.fingerprint());
    }

    @Test
    public void definitionOrPhysicalKeyMutationChangesFingerprint() {
        GenerationRegistryContract.PhysicalResourceKey plains = key(
                "minecraft:worldgen/biome",
                "minecraft:plains"
        );
        GenerationRegistryContract.PhysicalResourceKey forest = key(
                "minecraft:worldgen/biome",
                "minecraft:forest"
        );
        GenerationRegistryContract original = GenerationRegistryContract.fromDefinitions(Map.of(plains, DEFINITION_A));
        GenerationRegistryContract changedDefinition = GenerationRegistryContract.fromDefinitions(
                Map.of(plains, DEFINITION_B)
        );
        GenerationRegistryContract changedKey = GenerationRegistryContract.fromDefinitions(Map.of(forest, DEFINITION_A));

        assertNotEquals(original.fingerprint(), changedDefinition.fingerprint());
        assertNotEquals(original.fingerprint(), changedKey.fingerprint());
    }

    @Test
    public void roundTripsStrictDeterministicJson() {
        GenerationRegistryContract.PhysicalResourceKey generatedBiome = key(
                "minecraft:worldgen/biome",
                "iris:biomes/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );
        GenerationRegistryContract contract = GenerationRegistryContract.fromDefinitionsAndGeneratedSources(Map.of(
                key("minecraft:worldgen/biome", "minecraft:plains"),
                DEFINITION_A,
                key("minecraft:dimension_type", "minecraft:overworld"),
                DEFINITION_B,
                generatedBiome,
                DEFINITION_A
        ), Map.of(generatedBiome, generatedSource("{\"temperature\":0.5}")));
        JsonObject json = contract.toJson();

        assertEquals(contract, GenerationRegistryContract.fromJson(json.deepCopy()));
        assertEquals(json.toString(), contract.toJson().toString());

        JsonObject unknownField = json.deepCopy();
        unknownField.addProperty("unexpected", true);
        assertThrows(IllegalArgumentException.class, () -> GenerationRegistryContract.fromJson(unknownField));

        JsonObject missingDefinitions = json.deepCopy();
        missingDefinitions.remove("definitions");
        assertThrows(IllegalArgumentException.class, () -> GenerationRegistryContract.fromJson(missingDefinitions));

        JsonObject missingGeneratedSources = json.deepCopy();
        missingGeneratedSources.remove("generatedSources");
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationRegistryContract.fromJson(missingGeneratedSources)
        );
    }

    @Test
    public void generatedSourcesAreDefinitionBoundedAndFingerprintProtected() {
        GenerationRegistryContract.PhysicalResourceKey generatedBiome = key(
                "minecraft:worldgen/biome",
                "iris:biomes/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );
        GenerationRegistryContract contract = GenerationRegistryContract.fromDefinitionsAndGeneratedSources(
                Map.of(generatedBiome, DEFINITION_A),
                Map.of(generatedBiome, generatedSource("{\"temperature\":0.5}"))
        );
        JsonObject changed = contract.toJson();
        changed.getAsJsonArray("generatedSources")
                .get(0)
                .getAsJsonObject()
                .addProperty("sourceJson", "{\"temperature\":0.7}");

        assertThrows(IllegalArgumentException.class, () -> GenerationRegistryContract.fromJson(changed));
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationRegistryContract.fromDefinitionsAndGeneratedSources(
                        Map.of(),
                        Map.of(generatedBiome, generatedSource("{}"))
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationRegistryContract.fromDefinitionsAndGeneratedSources(
                        Map.of(generatedBiome, DEFINITION_A),
                        Map.of(generatedBiome, generatedSource("[]"))
                )
        );
        String oversized = "{\"value\":\"" + "a".repeat(1024 * 1024) + "\"}";
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationRegistryContract.fromDefinitionsAndGeneratedSources(
                        Map.of(generatedBiome, DEFINITION_A),
                        Map.of(generatedBiome, generatedSource(oversized))
                )
        );
    }

    @Test
    public void refusesUnsortedPersistedDefinitionsAndForgedFingerprint() {
        LinkedHashMap<GenerationRegistryContract.PhysicalResourceKey, String> definitions = new LinkedHashMap<>();
        definitions.put(key("minecraft:worldgen/biome", "minecraft:desert"), DEFINITION_B);
        definitions.put(key("minecraft:worldgen/biome", "minecraft:plains"), DEFINITION_A);
        GenerationRegistryContract contract = GenerationRegistryContract.fromDefinitions(definitions);
        JsonObject unsorted = contract.toJson();
        JsonArray definitionArray = unsorted.getAsJsonArray("definitions");
        JsonElement first = definitionArray.get(0).deepCopy();
        JsonElement second = definitionArray.get(1).deepCopy();
        definitionArray.set(0, second);
        definitionArray.set(1, first);

        assertThrows(IllegalArgumentException.class, () -> GenerationRegistryContract.fromJson(unsorted));

        JsonObject forged = contract.toJson();
        forged.addProperty("fingerprint", DEFINITION_A);
        assertThrows(IllegalArgumentException.class, () -> GenerationRegistryContract.fromJson(forged));
    }

    @Test
    public void refusesNonCanonicalKeysHashesAndUnknownSchemas() {
        assertThrows(
                IllegalArgumentException.class,
                () -> key("Minecraft:worldgen/biome", "minecraft:plains")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> key("minecraft:worldgen/biome", "plains")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationRegistryContract.fromDefinitions(Map.of(
                        key("minecraft:worldgen/biome", "minecraft:plains"),
                        "not-a-sha256"
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationRegistryContract.fromDefinitions(2, Map.of())
        );
    }

    private static GenerationRegistryContract.PhysicalResourceKey key(String registryKey, String resourceKey) {
        return new GenerationRegistryContract.PhysicalResourceKey(registryKey, resourceKey);
    }

    private static GenerationRegistryContract.GeneratedSource generatedSource(String sourceJson) {
        return new GenerationRegistryContract.GeneratedSource(
                "test-source-v1",
                DEFINITION_A,
                "{\"authored\":true}",
                "test-renderer-v1",
                DEFINITION_B,
                sourceJson
        );
    }
}
