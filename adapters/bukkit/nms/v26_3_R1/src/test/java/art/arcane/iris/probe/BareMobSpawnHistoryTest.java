package art.arcane.iris.probe;

import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.biome.IrisBiomeCustomSpawn;
import art.arcane.iris.generation.biome.IrisBiomeCustomSpawnType;
import art.arcane.iris.pack.datapack.v263.DataFixerV263;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.GenerationRegistryContractFactory;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public final class BareMobSpawnHistoryTest {
    private static final String LEGACY_RENDERER_IDENTITY = "bukkit-v26_3_R1-generated-registry-json-v1";
    private static final String NATURAL_MOB_SPAWNS = "minecraft:gameplay/natural_mob_spawns";
    private static final GenerationRegistryContract.PhysicalResourceKey INSTALLED =
            new GenerationRegistryContract.PhysicalResourceKey(
                    GenerationRegistryContractFactory.BIOME_REGISTRY,
                    "iris:biomes/" + "c".repeat(64)
            );
    private static HeadlessNativeRegistries loaded;
    private static PlatformGenerationRegistry registry;

    @BeforeClass
    public static void installOverlayRendering() throws IOException {
        assertNull(Bukkit.getServer());
        loaded = HeadlessNativeRegistries.load(Map.of(
                "data/iris/worldgen/biome/biomes/" + "c".repeat(64) + ".json",
                spawningBiome().generateJson(new DataFixerV263())
        ));
        registry = new HeadlessNativePlatform(new File("unused-native-platform"), loaded::registries)
                .generationRegistry();
    }

    @AfterClass
    public static void closeRegistries() throws IOException {
        if (loaded != null) {
            loaded.close();
        }
    }

    @Test
    public void bareMobSpawnHistoryValidatesAgainstTheInstalledOverlayRendering() throws IOException {
        GenerationRegistryContract legacy = legacyContract(INSTALLED, spawningBiome());
        GenerationRegistryContract.GeneratedSource source = legacy.generatedSources().get(INSTALLED);
        assertEquals(GenerationRegistryContract.FINGERPRINT_SCHEMA_VERSION_ONE, legacy.fingerprintSchema());
        assertEquals(LEGACY_RENDERER_IDENTITY, source.rendererIdentity());
        assertNotEquals(
                JsonParser.parseString(source.sourceJson()),
                JsonParser.parseString(registry.generatedDefinition(
                        INSTALLED.registryKey(),
                        INSTALLED.resourceKey()
                ).value())
        );

        GenerationRegistryContract available = GenerationRegistryContractFactory.captureRequiredDefinitions(
                Set.of(legacy),
                new DataFixerV263(),
                registry
        );

        legacy.requireDefinitionsAvailableIn(available);
    }

    @Test
    public void bareMobSpawnHistoryStillRejectsDifferentSemantics() throws IOException {
        GenerationRegistryContract changed = legacyContract(INSTALLED, spawningBiome().setTemperature(0.2D));

        IOException failure = assertThrows(IOException.class,
                () -> GenerationRegistryContractFactory.captureRequiredDefinitions(
                        Set.of(changed),
                        new DataFixerV263(),
                        registry
                ));

        assertEquals("Historical generated registry definition changed for minecraft:worldgen/biome / "
                + INSTALLED.resourceKey() + ".", failure.getMessage());
    }

    @Test
    public void bareMobSpawnHistoryStillRejectsAMissingDefinition() throws IOException {
        GenerationRegistryContract missing = legacyContract(new GenerationRegistryContract.PhysicalResourceKey(
                GenerationRegistryContractFactory.BIOME_REGISTRY,
                "iris:biomes/" + "d".repeat(64)
        ), spawningBiome());

        assertThrows(IOException.class, () -> GenerationRegistryContractFactory.captureRequiredDefinitions(
                Set.of(missing),
                new DataFixerV263(),
                registry
        ));
    }

    private static GenerationRegistryContract legacyContract(
            GenerationRegistryContract.PhysicalResourceKey key,
            IrisBiomeCustom biome
    ) {
        JsonObject rendered = JsonParser.parseString(biome.generateJson(new DataFixerV263())).getAsJsonObject();
        JsonObject attributes = rendered.getAsJsonObject("attributes");
        attributes.add(NATURAL_MOB_SPAWNS, attributes.getAsJsonObject(NATURAL_MOB_SPAWNS).get("argument"));
        String captured = registry.canonicalDefinition(key.registryKey(), key.resourceKey(), rendered.toString())
                .value();
        String semantic = GenerationRegistryContractFactory.customBiomeEffectiveSemanticJson(biome, null);
        GenerationRegistryContract.GeneratedSource source = new GenerationRegistryContract.GeneratedSource(
                GenerationRegistryContractFactory.CUSTOM_BIOME_EFFECTIVE_SOURCE_SCHEMA,
                GenerationRegistryContractFactory.fingerprintCustomBiomeAuthoredDefinition(semantic),
                semantic,
                LEGACY_RENDERER_IDENTITY,
                GenerationRegistryContractFactory.fingerprintDefinition(
                        key,
                        PlatformGenerationRegistry.Definition.exactJson(captured),
                        "generated"
                ),
                captured
        );
        return GenerationRegistryContract.fromDefinitionsAndGeneratedSources(
                Map.of(key, GenerationRegistryContractFactory.fingerprintGeneratedSemantic(key, source)),
                Map.of(key, source)
        );
    }

    private static IrisBiomeCustom spawningBiome() {
        return new IrisBiomeCustom()
                .setId("tester")
                .setSpawnRarity(2)
                .setSpawns(new KList<>(
                        new IrisBiomeCustomSpawn()
                                .setType("minecraft:zombie")
                                .setGroup(IrisBiomeCustomSpawnType.MONSTER)
                                .setMinCount(2)
                                .setMaxCount(4)
                                .setWeight(7),
                        new IrisBiomeCustomSpawn()
                                .setType("minecraft:cow")
                                .setGroup(IrisBiomeCustomSpawnType.CREATURE)
                                .setMinCount(3)
                                .setMaxCount(3)
                                .setWeight(5)
                ));
    }
}
