package art.arcane.iris.world.history;

import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.validation.KeyStatus;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.IDataFixer;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.biome.IrisBiomeCustomCategory;
import art.arcane.iris.generation.biome.IrisBiomeCustomParticle;
import art.arcane.iris.generation.biome.IrisBiomeCustomPrecipType;
import art.arcane.iris.generation.biome.IrisBiomeCustomSpawn;
import art.arcane.iris.generation.biome.IrisBiomeCustomSpawnType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionType;
import art.arcane.iris.generation.terrain.IrisDimensionTypeOptions;
import art.arcane.iris.structure.nativegen.IrisNativeStructure;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.volmlib.nativelib.terrain.RegistryClientNames;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class GenerationRegistryContractFactoryTest {
    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void capturesGeneratedAndReferencedPhysicalDefinitions() throws Exception {
        File pack = temporary.newFolder("pack");
        Files.writeString(
                pack.toPath().resolve("content.json"),
                "{\"block\":\"minecraft:stone[axis=y]\",\"type\":\"minecraft:zombie\"}",
                StandardCharsets.UTF_8
        );
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(pack);
        ContentGate contentGate = mock(ContentGate.class);
        when(data.getContentGate()).thenReturn(contentGate);
        IrisBiomeCustom custom = spy(new IrisBiomeCustom().setId("mist"));
        IDataFixer fixer = DataVersion.getLatest().get();
        doReturn("{\"effects\":{},\"temperature\":0.5}")
                .when(custom).generateJson(fixer, contentGate);
        IrisNativeStructure nativeStructure = new IrisNativeStructure().setStructure("minecraft:village_plains");
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setNativeStructures(new KList<>(nativeStructure));
        IrisBiome biome = new IrisBiome()
                .setDerivative("minecraft:plains")
                .setVanillaDerivative("minecraft:forest")
                .setBiomeScatter(new KList<>("minecraft:desert", "overworld:mist"))
                .setBiomeSkyScatter(new KList<>("minecraft:taiga"))
                .setCustomDerivitives(new KList<>(custom));
        biome.setLoadKey("surface");
        IrisDimension dimension = spy(new IrisDimension()
                .setStructures(new KList<>(placement)));
        dimension.setLoadKey("overworld");
        stubDimensions(data, dimension);
        doReturn(new KList<>(biome)).when(dimension).getReachableBiomes(any());
        doReturn(new KList<>()).when(dimension).getAllRegions(any());
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.blockTypeKeys()).thenReturn(new KList<>("minecraft:stone", "minecraft:dirt"));
        when(registries.entityKeys()).thenReturn(new KList<>("minecraft:zombie", "minecraft:pig"));
        FakeGenerationRegistry generationRegistry = new FakeGenerationRegistry("test-runtime");
        String packFingerprint = GenerationPackFingerprint.compute(
                pack.toPath(),
                GenerationPackFingerprint.CURRENT_VERSION
        );

        GenerationRegistryContract contract = GenerationRegistryContractFactory.create(
                data,
                dimension,
                packFingerprint,
                fixer,
                registries,
                generationRegistry
        );

        Set<GenerationRegistryContract.PhysicalResourceKey> keys = contract.definitions().keySet();
        assertTrue(keys.contains(key(GenerationRegistryContractFactory.DIMENSION_TYPE_REGISTRY, "test:overworld_type")));
        String customFingerprint = GenerationRegistryContractFactory.fingerprintCustomBiomeIdentity(
                GenerationRegistryContractFactory.fingerprintCustomBiomeAuthoredDefinition(custom),
                "overworld",
                "mist"
        );
        assertTrue(keys.contains(key(
                GenerationRegistryContractFactory.BIOME_REGISTRY,
                "test:biomes/" + customFingerprint
        )));
        assertFalse(keys.contains(key(
                GenerationRegistryContractFactory.BIOME_REGISTRY,
                "test:overworld/mist"
        )));
        assertFalse(keys.contains(key(
                GenerationRegistryContractFactory.BIOME_REGISTRY,
                "overworld:mist"
        )));
        assertTrue(keys.contains(key(GenerationRegistryContractFactory.BIOME_REGISTRY, "minecraft:plains")));
        assertTrue(keys.contains(key(GenerationRegistryContractFactory.BIOME_REGISTRY, "minecraft:forest")));
        assertTrue(keys.contains(key(GenerationRegistryContractFactory.BIOME_REGISTRY, "minecraft:desert")));
        assertTrue(keys.contains(key(GenerationRegistryContractFactory.BIOME_REGISTRY, "minecraft:taiga")));
        assertTrue(keys.contains(key(
                GenerationRegistryContractFactory.STRUCTURE_REGISTRY,
                "minecraft:village_plains"
        )));
        assertTrue(keys.contains(key(GenerationRegistryContractFactory.BLOCK_REGISTRY, "minecraft:stone")));
        assertTrue(keys.contains(key(GenerationRegistryContractFactory.ENTITY_TYPE_REGISTRY, "minecraft:zombie")));

        GenerationRegistryContract adopted = GenerationRegistryContractFactory.create(
                data,
                dimension,
                packFingerprint,
                fixer,
                registries,
                generationRegistry,
                GenerationRegistryContractFactory.CustomBiomeAliasPolicy.RETAIN_LEGACY_ALIASES
        );
        assertTrue(adopted.definitions().containsKey(key(
                GenerationRegistryContractFactory.BIOME_REGISTRY,
                "test:overworld/mist"
        )));
    }

    @Test
    public void duplicateLogicalBiomeIdsKeepExactDefinitionsAndChooseStableLegacyAlias() throws Exception {
        File pack = temporary.newFolder("duplicate-biomes");
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(pack);
        ContentGate contentGate = mock(ContentGate.class);
        when(contentGate.entity("example:missing")).thenReturn(KeyStatus.MISSING);
        when(data.getContentGate()).thenReturn(contentGate);
        IrisBiomeCustom plateau = new IrisBiomeCustom().setId("savanna_plateau").setGrassColor("#FFCB31")
                .setSpawns(new KList<>(new IrisBiomeCustomSpawn().setType("example:missing")));
        IrisBiomeCustom vanilla = new IrisBiomeCustom().setId("savanna_plateau").setGrassColor("#BFB755");
        IrisBiome first = new IrisBiome().setCustomDerivitives(new KList<>(plateau));
        IrisBiome second = new IrisBiome().setCustomDerivitives(new KList<>(vanilla));
        IrisDimension dimension = spy(new IrisDimension());
        dimension.setLoadKey("overworld");
        stubDimensions(data, dimension);
        doReturn(new KList<>(first, second)).when(dimension).getAllBiomes(any());
        doReturn(new KList<>(first)).when(dimension).getReachableBiomes(any());
        doReturn(new KList<>()).when(dimension).getAllRegions(any());
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.blockTypeKeys()).thenReturn(List.of());
        when(registries.entityKeys()).thenReturn(List.of());
        FakeGenerationRegistry registry = new FakeGenerationRegistry("duplicate-runtime");
        String fingerprint = GenerationPackFingerprint.compute(
                pack.toPath(), GenerationPackFingerprint.CURRENT_VERSION);
        GenerationRegistryContract contract = GenerationRegistryContractFactory.create(
                data, dimension, fingerprint, DataVersion.getLatest().get(), registries, registry,
                GenerationRegistryContractFactory.CustomBiomeAliasPolicy.RETAIN_LEGACY_ALIASES);
        String plateauKey = GenerationRegistryContractFactory.customBiomeResourceKey(
                "overworld", plateau, contentGate, registry);
        String vanillaKey = GenerationRegistryContractFactory.customBiomeResourceKey(
                "overworld", vanilla, contentGate, registry);
        assertNotEquals(plateauKey, vanillaKey);
        assertTrue(contract.definitions().containsKey(key(GenerationRegistryContractFactory.BIOME_REGISTRY, plateauKey)));
        assertTrue(contract.definitions().containsKey(key(GenerationRegistryContractFactory.BIOME_REGISTRY, vanillaKey)));
        String selected = plateauKey.compareTo(vanillaKey) < 0 ? plateauKey : vanillaKey;
        assertEquals(contract.generatedSources().get(key(GenerationRegistryContractFactory.BIOME_REGISTRY, selected)).sourceJson(),
                contract.generatedSources().get(key(GenerationRegistryContractFactory.BIOME_REGISTRY,
                        "test:overworld/savanna_plateau")).sourceJson());
        GenerationRegistryContractFactory.CustomBiomeResourceResolver resolver =
                new GenerationRegistryContractFactory.CustomBiomeResourceResolver(contract, registry::customBiomeResourceKey);
        assertEquals(plateauKey, resolver.resolve("overworld", plateau));
        assertEquals(vanillaKey, resolver.resolve("overworld", vanilla));
        assertEquals(Set.of("iris:overworld/savanna_plateau", "iris_2:overworld/savanna_plateau"),
                Set.copyOf(RegistryClientNames.resolve(GenerationRegistryContractFactory.BIOME_REGISTRY,
                        List.of(plateauKey, vanillaKey))));
        assertNotEquals(plateauKey, GenerationRegistryContractFactory.customBiomeResourceKey("overworld", plateau, registry));
        assertThrows(IOException.class,
                () -> resolver.resolve("overworld", new IrisBiomeCustom().setId("savanna_plateau").setGrassColor("#FF0000")));

        doReturn(new KList<>(second, first)).when(dimension).getAllBiomes(any());
        GenerationRegistryContract reversed = GenerationRegistryContractFactory.create(
                data, dimension, fingerprint, DataVersion.getLatest().get(), registries, registry,
                GenerationRegistryContractFactory.CustomBiomeAliasPolicy.RETAIN_LEGACY_ALIASES);
        assertEquals(contract, reversed);
    }

    @Test
    public void customBiomeIdentityUsesAuthoredDefinitionAndLogicalIdentity() {
        IrisBiomeCustom firstBiome = new IrisBiomeCustom().setId("mist").setTemperature(0.5D);
        IrisBiomeCustom changedBiome = new IrisBiomeCustom().setId("mist").setTemperature(0.7D);
        String firstDefinition = GenerationRegistryContractFactory
                .fingerprintCustomBiomeAuthoredDefinition(firstBiome);
        String changedDefinition = GenerationRegistryContractFactory
                .fingerprintCustomBiomeAuthoredDefinition(changedBiome);
        String first = GenerationRegistryContractFactory.fingerprintCustomBiomeIdentity(
                firstDefinition,
                "Overworld",
                "Mist"
        );
        String normalized = GenerationRegistryContractFactory.fingerprintCustomBiomeIdentity(
                firstDefinition,
                "overworld",
                "mist"
        );
        String changedBiomeIdentity = GenerationRegistryContractFactory.fingerprintCustomBiomeIdentity(
                changedDefinition,
                "overworld",
                "mist"
        );

        assertEquals(first, normalized);
        assertNotEquals(first, changedBiomeIdentity);
        assertEquals("iris:biomes/" + first,
                PlatformGenerationRegistry.contentAddressedCustomBiomeResourceKey(first));
    }

    @Test
    public void clientBiomeNamesUseAuthoredIdsWhilePhysicalIdentitiesStayDistinct() {
        IrisBiomeCustom first = new IrisBiomeCustom().setId("Mist").setTemperature(0.5D);
        IrisBiomeCustom second = new IrisBiomeCustom().setId("Mist").setTemperature(0.7D);
        String firstKey = GenerationRegistryContractFactory.customBiomeResourceKey("Overworld", first);
        String secondKey = GenerationRegistryContractFactory.customBiomeResourceKey("Overworld", second);
        List<String> physicalKeys = List.of("minecraft:plains", firstKey, secondKey);

        List<String> clientKeys = RegistryClientNames.resolve(
                GenerationRegistryContractFactory.BIOME_REGISTRY, physicalKeys);

        assertNotEquals(firstKey, secondKey);
        assertEquals("minecraft:plains", clientKeys.getFirst());
        assertEquals(Set.of("iris:overworld/mist", "iris_2:overworld/mist"),
                Set.of(clientKeys.get(1), clientKeys.get(2)));
        assertTrue(firstKey.matches("iris:biomes/[a-f0-9]{64}"));
        assertEquals(List.of("minecraft:plains", firstKey, secondKey), physicalKeys);
    }

    @Test
    public void contentGateEffectsParticipateInCustomBiomeIdentity() {
        IrisBiomeCustom custom = new IrisBiomeCustom()
                .setId("mist")
                .setSpawns(new KList<>(new IrisBiomeCustomSpawn().setType("example:animal")));
        ContentGate present = mock(ContentGate.class);
        ContentGate missing = mock(ContentGate.class);
        when(present.entity("example:animal")).thenReturn(KeyStatus.PRESENT);
        when(missing.entity("example:animal")).thenReturn(KeyStatus.MISSING);

        String withSpawn = GenerationRegistryContractFactory.customBiomeResourceKey(
                "overworld",
                custom,
                present
        );
        String withoutSpawn = GenerationRegistryContractFactory.customBiomeResourceKey(
                "overworld",
                custom,
                missing
        );

        assertNotEquals(withSpawn, withoutSpawn);
    }

    @Test
    public void unrelatedPackChangesReuseCustomBiomePhysicalIdentity() throws Exception {
        File firstPack = temporary.newFolder("identity-pack-a");
        File secondPack = temporary.newFolder("identity-pack-b");
        Files.writeString(firstPack.toPath().resolve("unrelated.json"), "{\"value\":1}", StandardCharsets.UTF_8);
        Files.writeString(secondPack.toPath().resolve("unrelated.json"), "{\"value\":2}", StandardCharsets.UTF_8);
        ContentGate contentGate = mock(ContentGate.class);
        IrisData firstData = mock(IrisData.class);
        when(firstData.getDataFolder()).thenReturn(firstPack);
        when(firstData.getContentGate()).thenReturn(contentGate);
        IrisData secondData = mock(IrisData.class);
        when(secondData.getDataFolder()).thenReturn(secondPack);
        when(secondData.getContentGate()).thenReturn(contentGate);
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("overworld");
        IDataFixer fixer = mock(IDataFixer.class);
        IrisBiomeCustom custom = spy(new IrisBiomeCustom().setId("mist").setTemperature(0.5D));
        doReturn("{\"temperature\":0.5,\"effects\":{}}")
                .when(custom).generateJson(fixer, contentGate);
        FakeGenerationRegistry registry = new FakeGenerationRegistry("runtime");

        GenerationRegistryContractFactory.CustomBiomeDefinition first =
                GenerationRegistryContractFactory.customBiomeDefinition(
                        firstData,
                        dimension,
                        custom,
                        fixer,
                        registry
                );
        GenerationRegistryContractFactory.CustomBiomeDefinition second =
                GenerationRegistryContractFactory.customBiomeDefinition(
                        secondData,
                        dimension,
                        custom,
                        fixer,
                        registry
                );

        assertNotEquals(
                GenerationPackFingerprint.compute(firstPack.toPath(), GenerationPackFingerprint.CURRENT_VERSION),
                GenerationPackFingerprint.compute(secondPack.toPath(), GenerationPackFingerprint.CURRENT_VERSION)
        );
        assertEquals(first.physicalKey(), second.physicalKey());
        assertEquals(first.identityFingerprint(), second.identityFingerprint());
    }

    @Test
    public void serializerChangesDoNotAlterCustomBiomePhysicalIdentityOrContract() throws Exception {
        File pack = temporary.newFolder("serializer-stable-pack");
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(pack);
        ContentGate contentGate = mock(ContentGate.class);
        when(data.getContentGate()).thenReturn(contentGate);
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("overworld");
        IrisBiomeCustom custom = spy(new IrisBiomeCustom().setId("mist"));
        IDataFixer firstFixer = mock(IDataFixer.class);
        IDataFixer secondFixer = mock(IDataFixer.class);
        doReturn(
                "{\"temperature\":0.5,\"effects\":{}}"
        ).when(custom).generateJson(firstFixer, contentGate);
        doReturn(
                "{\"temperature\":0.5,\"effects\":{},\"new_schema_default\":true}"
        ).when(custom).generateJson(secondFixer, contentGate);
        FakeGenerationRegistry registry = new FakeGenerationRegistry("runtime-a") {
            @Override
            public Definition canonicalDefinition(String registryKey, String resourceKey, String sourceJson) {
                return Definition.exactJson("{\"effects\":{},\"temperature\":0.5}");
            }
        };

        GenerationRegistryContractFactory.CustomBiomeDefinition first =
                GenerationRegistryContractFactory.customBiomeDefinition(
                        data,
                        dimension,
                        custom,
                        firstFixer,
                        registry
                );
        GenerationRegistryContractFactory.CustomBiomeDefinition second =
                GenerationRegistryContractFactory.customBiomeDefinition(
                        data,
                        dimension,
                        custom,
                        secondFixer,
                        registry
                );

        assertEquals(first.physicalKey(), second.physicalKey());
        assertEquals(first.identityFingerprint(), second.identityFingerprint());
        assertEquals(first.definition(), second.definition());
        assertEquals(first.contentFingerprint(), second.contentFingerprint());
        assertEquals(first.sourceJson(), second.sourceJson());
        assertEquals(
                GenerationRegistryContractFactory.fingerprintDefinition(
                        first.physicalKey(),
                        first.definition(),
                        "runtime-a"
                ),
                GenerationRegistryContractFactory.fingerprintDefinition(
                        second.physicalKey(),
                        second.definition(),
                        "runtime-b"
                )
        );
    }

    @Test
    public void customBiomeDefinitionFingerprintCanonicalizesCurrentEmission() {
        String first = GenerationRegistryContractFactory.fingerprintCustomBiomeDefinition(
                "{\"temperature\":0.50,\"effects\":{\"sky_color\":1}}"
        );
        String reordered = GenerationRegistryContractFactory.fingerprintCustomBiomeDefinition(
                "{\"effects\":{\"sky_color\":1.0},\"temperature\":0.5}"
        );
        String changed = GenerationRegistryContractFactory.fingerprintCustomBiomeDefinition(
                "{\"effects\":{\"sky_color\":2},\"temperature\":0.5}"
        );

        assertEquals(first, reordered);
        assertNotEquals(first, changed);
    }

    @Test
    public void canonicalJsonIgnoresObjectOrderAndNumericFormatting() {
        GenerationRegistryContract.PhysicalResourceKey key = key(
                GenerationRegistryContractFactory.BIOME_REGISTRY,
                "minecraft:plains"
        );

        String first = GenerationRegistryContractFactory.fingerprintDefinition(
                key,
                PlatformGenerationRegistry.Definition.exactJson("{\"b\":2.0,\"a\":1}"),
                "runtime-a"
        );
        String second = GenerationRegistryContractFactory.fingerprintDefinition(
                key,
                PlatformGenerationRegistry.Definition.exactJson("{\"a\":1.00,\"b\":2}"),
                "runtime-b"
        );

        assertEquals(first, second);
    }

    @Test
    public void resourceIdentitySurvivesCompatibleRuntimeUpdates() {
        GenerationRegistryContract.PhysicalResourceKey key = key(
                GenerationRegistryContractFactory.BLOCK_REGISTRY,
                "minecraft:stone"
        );
        PlatformGenerationRegistry.Definition definition = PlatformGenerationRegistry.Definition.resourceIdentity(
                key.registryKey(),
                key.resourceKey()
        );

        String first = GenerationRegistryContractFactory.fingerprintDefinition(key, definition, "runtime-a");
        String second = GenerationRegistryContractFactory.fingerprintDefinition(key, definition, "runtime-b");

        assertEquals(first, second);
    }

    @Test
    public void capturesAndValidatesTheRetainedContractUnion() throws Exception {
        FakeGenerationRegistry registry = new FakeGenerationRegistry("runtime");
        GenerationRegistryContract.PhysicalResourceKey plains = key(
                GenerationRegistryContractFactory.BIOME_REGISTRY,
                "minecraft:plains"
        );
        GenerationRegistryContract.PhysicalResourceKey village = key(
                GenerationRegistryContractFactory.STRUCTURE_REGISTRY,
                "minecraft:village_plains"
        );
        GenerationRegistryContract first = contract(registry, plains);
        GenerationRegistryContract second = contract(registry, village);

        GenerationRegistryContract available = GenerationRegistryContractFactory.captureRequiredDefinitions(
                Set.of(first, second),
                registry
        );

        assertEquals(Set.of(plains, village), available.definitions().keySet());
        first.requireDefinitionsAvailableIn(available);
        second.requireDefinitionsAvailableIn(available);
    }

    @Test
    public void rejectsConflictingRetainedPhysicalDefinitions() {
        GenerationRegistryContract.PhysicalResourceKey plains = key(
                GenerationRegistryContractFactory.BIOME_REGISTRY,
                "minecraft:plains"
        );
        GenerationRegistryContract first = GenerationRegistryContract.fromDefinitions(Map.of(plains, HASH_A));
        GenerationRegistryContract second = GenerationRegistryContract.fromDefinitions(Map.of(plains, HASH_B));

        assertThrows(
                IOException.class,
                () -> GenerationRegistryContractFactory.captureRequiredDefinitions(
                        Set.of(first, second),
                        new FakeGenerationRegistry("runtime")
                )
        );
    }

    @Test
    public void acceptsChangedExternalRuntimeDetailsButRejectsMissingKeys() throws Exception {
        FakeGenerationRegistry original = new FakeGenerationRegistry("runtime");
        GenerationRegistryContract.PhysicalResourceKey plains = key(
                GenerationRegistryContractFactory.BIOME_REGISTRY,
                "minecraft:plains"
        );
        GenerationRegistryContract required = contract(original, plains);
        FakeGenerationRegistry changed = new FakeGenerationRegistry("different-runtime");
        changed.put(
                plains,
                PlatformGenerationRegistry.Definition.resourceIdentity(
                        plains.registryKey(),
                        plains.resourceKey()
                )
        );
        FakeGenerationRegistry missing = new FakeGenerationRegistry("runtime");
        missing.remove(plains);

        assertEquals(
                required,
                GenerationRegistryContractFactory.captureRequiredDefinitions(Set.of(required), changed)
        );
        assertThrows(
                IOException.class,
                () -> GenerationRegistryContractFactory.captureRequiredDefinitions(Set.of(required), missing)
        );
    }

    @Test
    public void validatesGeneratedDefinitionsByRecordedCanonicalContent() throws Exception {
        FakeGenerationRegistry registry = new FakeGenerationRegistry("runtime-a");
        GenerationRegistryContract.PhysicalResourceKey generated = key(
                GenerationRegistryContractFactory.BIOME_REGISTRY,
                "iris:biomes/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );
        IrisBiomeCustom customBiome = new IrisBiomeCustom().setId("mist").setTemperature(0.5D);
        String semantic = GenerationRegistryContractFactory.customBiomeEffectiveSemanticJson(customBiome, null);
        String semanticFingerprint = GenerationRegistryContractFactory
                .fingerprintCustomBiomeAuthoredDefinition(semantic);
        IDataFixer firstFixer = new TaggedBiomeFixer("first");
        String source = customBiome.generateJson(
                firstFixer,
                new ContentGate(null, Map.of(), null)
        );
        PlatformGenerationRegistry.Definition definition = PlatformGenerationRegistry.Definition.exactJson(source);
        registry.put(generated, definition);
        GenerationRegistryContract.GeneratedSource generatedSource = new GenerationRegistryContract.GeneratedSource(
                GenerationRegistryContractFactory.CUSTOM_BIOME_EFFECTIVE_SOURCE_SCHEMA,
                semanticFingerprint,
                semantic,
                "test-renderer-v1",
                GenerationRegistryContractFactory.fingerprintDefinition(
                        generated,
                        definition,
                        "generated"
                ),
                source
        );
        GenerationRegistryContract required = GenerationRegistryContract.fromDefinitionsAndGeneratedSources(
                Map.of(generated, GenerationRegistryContractFactory.fingerprintGeneratedSemantic(
                        generated,
                        generatedSource
                )),
                Map.of(generated, generatedSource)
        );

        GenerationRegistryContract available = GenerationRegistryContractFactory.captureRequiredDefinitions(
                Set.of(required),
                firstFixer,
                registry
        );

        required.requireDefinitionsAvailableIn(available);
        assertEquals(
                JsonParser.parseString(source),
                JsonParser.parseString(GenerationRegistryContractFactory.renderGeneratedSource(
                        required,
                        generated,
                        firstFixer
                ))
        );

        registry.put(generated, PlatformGenerationRegistry.Definition.exactJson(
                "{\"effects\":{},\"temperature\":0.7}"
        ));
        assertThrows(
                IOException.class,
                () -> GenerationRegistryContractFactory.captureRequiredDefinitions(
                        Set.of(required),
                        firstFixer,
                        registry
                )
        );
        registry.remove(generated);
        assertThrows(
                IOException.class,
                () -> GenerationRegistryContractFactory.captureRequiredDefinitions(
                        Set.of(required),
                        firstFixer,
                        registry
                )
        );
    }

    @Test
    public void validatesTheCurrentRenderingWhenTheRendererChangedUnderTheSameIdentity() throws Exception {
        FakeGenerationRegistry registry = new FakeGenerationRegistry("runtime-a");
        GenerationRegistryContract.PhysicalResourceKey generated = key(
                GenerationRegistryContractFactory.BIOME_REGISTRY,
                "iris:biomes/" + HASH_A
        );
        IrisBiomeCustom customBiome = new IrisBiomeCustom().setId("mist").setTemperature(0.5D);
        String semantic = GenerationRegistryContractFactory.customBiomeEffectiveSemanticJson(customBiome, null);
        String capturedSource = customBiome.generateJson(
                new TaggedBiomeFixer("first"),
                new ContentGate(null, Map.of(), null)
        );
        GenerationRegistryContract.GeneratedSource generatedSource = new GenerationRegistryContract.GeneratedSource(
                GenerationRegistryContractFactory.CUSTOM_BIOME_EFFECTIVE_SOURCE_SCHEMA,
                GenerationRegistryContractFactory.fingerprintCustomBiomeAuthoredDefinition(semantic),
                semantic,
                "test-renderer-v1",
                GenerationRegistryContractFactory.fingerprintDefinition(
                        generated,
                        PlatformGenerationRegistry.Definition.exactJson(capturedSource),
                        "generated"
                ),
                capturedSource
        );
        GenerationRegistryContract required = GenerationRegistryContract.fromDefinitionsAndGeneratedSources(
                Map.of(generated, GenerationRegistryContractFactory.fingerprintGeneratedSemantic(
                        generated,
                        generatedSource
                )),
                Map.of(generated, generatedSource)
        );
        IDataFixer currentFixer = new TaggedBiomeFixer("second");
        String installed = GenerationRegistryContractFactory.renderGeneratedSource(required, generated, currentFixer);
        assertNotEquals(JsonParser.parseString(capturedSource), JsonParser.parseString(installed));
        registry.put(generated, PlatformGenerationRegistry.Definition.exactJson(installed));

        GenerationRegistryContract available = GenerationRegistryContractFactory.captureRequiredDefinitions(
                Set.of(required),
                currentFixer,
                registry
        );

        required.requireDefinitionsAvailableIn(available);
    }

    @Test
    public void semanticRenderingMatchesTheCapturedRenderingForEverySupportedFixer() throws Exception {
        IrisData data = mock(IrisData.class);
        when(data.getContentGate()).thenReturn(new ContentGate(null, Map.of(), null));
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("overworld");
        IrisBiomeCustom biome = richCustomBiome();
        IrisDimensionTypeOptions options = new IrisDimensionTypeOptions()
                .coordinateScale(4.0)
                .ambientLight(0.25F)
                .fixedTime(18000L)
                .cloudHeight(-96)
                .monsterSpawnBlockLightLimit(11)
                .ultrawarm(IrisDimensionTypeOptions.TriState.TRUE)
                .natural(IrisDimensionTypeOptions.TriState.FALSE)
                .piglinSafe(IrisDimensionTypeOptions.TriState.TRUE)
                .respawnAnchorWorks(IrisDimensionTypeOptions.TriState.TRUE)
                .bedWorks(IrisDimensionTypeOptions.TriState.FALSE)
                .raids(IrisDimensionTypeOptions.TriState.FALSE)
                .skylight(IrisDimensionTypeOptions.TriState.FALSE)
                .ceiling(IrisDimensionTypeOptions.TriState.TRUE);
        FakeGenerationRegistry registry = new FakeGenerationRegistry("runtime");
        GenerationRegistryContract.PhysicalResourceKey dimensionKey = key(
                GenerationRegistryContractFactory.DIMENSION_TYPE_REGISTRY,
                "iris:overworld_type"
        );
        int fixers = 0;
        for (DataVersion version : DataVersion.values()) {
            if (version == DataVersion.UNSUPPORTED) {
                continue;
            }
            fixers++;
            IDataFixer fixer = version.get();
            GenerationRegistryContractFactory.CustomBiomeDefinition captured = GenerationRegistryContractFactory
                    .customBiomeDefinition(data, dimension, biome, fixer, registry);
            assertCapturedRenderingRoundTrips(
                    version,
                    captured.physicalKey(),
                    GenerationRegistryContractFactory.CUSTOM_BIOME_EFFECTIVE_SOURCE_SCHEMA,
                    captured.semanticFingerprint(),
                    captured.semanticJson(),
                    captured.definition(),
                    fixer,
                    registry
            );
            for (IDataFixer.Dimension base : IDataFixer.Dimension.values()) {
                IrisDimensionType dimensionType = new IrisDimensionType(base, options, 192, 384, -128);
                assertCapturedRenderingRoundTrips(
                        version,
                        dimensionKey,
                        GenerationRegistryContractFactory.DIMENSION_TYPE_EFFECTIVE_SOURCE_SCHEMA,
                        GenerationEpochContractFactory.fingerprintDimensionType(dimensionType),
                        GenerationEpochContractFactory.dimensionTypeSemanticJson(dimensionType),
                        registry.canonicalDefinition(
                                dimensionKey.registryKey(),
                                dimensionKey.resourceKey(),
                                dimensionType.toJson(fixer)
                        ),
                        fixer,
                        registry
                );
            }
        }
        assertEquals(DataVersion.values().length - 1, fixers);
    }

    private static void assertCapturedRenderingRoundTrips(
            DataVersion version,
            GenerationRegistryContract.PhysicalResourceKey key,
            String sourceSchema,
            String semanticFingerprint,
            String semanticJson,
            PlatformGenerationRegistry.Definition captured,
            IDataFixer fixer,
            PlatformGenerationRegistry registry
    ) throws IOException {
        GenerationRegistryContract.GeneratedSource source = new GenerationRegistryContract.GeneratedSource(
                sourceSchema,
                semanticFingerprint,
                semanticJson,
                "round-trip",
                GenerationRegistryContractFactory.fingerprintDefinition(key, captured, "generated"),
                captured.value()
        );
        GenerationRegistryContract contract = GenerationRegistryContract.fromDefinitionsAndGeneratedSources(
                Map.of(key, GenerationRegistryContractFactory.fingerprintGeneratedSemantic(key, source)),
                Map.of(key, source)
        );
        String rendered = GenerationRegistryContractFactory.renderGeneratedSource(contract, key, fixer);
        assertEquals(
                version + " " + key.resourceKey(),
                JsonParser.parseString(captured.value()),
                JsonParser.parseString(registry.canonicalDefinition(
                        key.registryKey(),
                        key.resourceKey(),
                        rendered
                ).value())
        );
    }

    private static IrisBiomeCustom richCustomBiome() {
        String[] entities = {
                "minecraft:zombie", "minecraft:cow", "minecraft:bat", "minecraft:axolotl",
                "minecraft:glow_squid", "minecraft:squid", "minecraft:cod", "minecraft:allay"
        };
        IrisBiomeCustomSpawnType[] groups = IrisBiomeCustomSpawnType.values();
        KList<IrisBiomeCustomSpawn> spawns = new KList<>();
        for (int index = 0; index < groups.length; index++) {
            spawns.add(new IrisBiomeCustomSpawn()
                    .setType(entities[index % entities.length])
                    .setGroup(groups[index])
                    .setWeight(index + 1)
                    .setMinCount(index + 1)
                    .setMaxCount(index % 2 == 0 ? index + 3 : index + 1));
        }
        return new IrisBiomeCustom()
                .setId("round_trip")
                .setTemperature(1.25D)
                .setHumidity(0.3D)
                .setDownfallType(IrisBiomeCustomPrecipType.snow)
                .setCategory(IrisBiomeCustomCategory.forest)
                .setSpawnRarity(7)
                .setSkyColor("#112233")
                .setFogColor("445566")
                .setWaterColor("#778899")
                .setWaterFogColor("#aabbcc")
                .setGrassColor("#55aa33")
                .setFoliageColor("#335511")
                .setAmbientParticle(new IrisBiomeCustomParticle().setParticle("ash").setRarity(80))
                .setSpawns(spawns);
    }

    @Test
    public void rerendersDimensionTypeSemanticsForANewRenderer() throws Exception {
        assertDimensionRerender(new IrisDimension().getDimensionType());
    }

    @Test
    public void dimensionRerenderPreservesNullableAndNondefaultOptions() throws Exception {
        IrisDimensionType original = new IrisDimension().getDimensionType();
        IrisDimensionTypeOptions options = new IrisDimensionTypeOptions()
                .coordinateScale(8.0)
                .ambientLight(0.375F)
                .fixedTime(null)
                .cloudHeight(null)
                .monsterSpawnBlockLightLimit(7)
                .ultrawarm(IrisDimensionTypeOptions.TriState.TRUE)
                .natural(IrisDimensionTypeOptions.TriState.FALSE)
                .ceiling(IrisDimensionTypeOptions.TriState.TRUE);
        assertDimensionRerender(new IrisDimensionType(original.base(), options, 128, 256, -64));
        options.fixedTime(6000L).cloudHeight(-256);
        assertDimensionRerender(new IrisDimensionType(original.base(), options, 128, 256, -64));
    }

    private void assertDimensionRerender(IrisDimensionType dimensionType) throws Exception {
        GenerationRegistryContract.PhysicalResourceKey key = key(
                GenerationRegistryContractFactory.DIMENSION_TYPE_REGISTRY,
                "iris:overworld_type"
        );
        IDataFixer firstFixer = new TaggedBiomeFixer("first");
        String semantic = GenerationEpochContractFactory.dimensionTypeSemanticJson(
                dimensionType
        );
        String source = dimensionType.toJson(firstFixer);
        PlatformGenerationRegistry.Definition definition = PlatformGenerationRegistry.Definition.exactJson(source);
        GenerationRegistryContract.GeneratedSource generatedSource = new GenerationRegistryContract.GeneratedSource(
                GenerationRegistryContractFactory.DIMENSION_TYPE_EFFECTIVE_SOURCE_SCHEMA,
                GenerationEpochContractFactory.fingerprintDimensionType(dimensionType),
                semantic,
                "test-renderer-v1",
                GenerationRegistryContractFactory.fingerprintDefinition(key, definition, "generated"),
                source
        );
        GenerationRegistryContract contract = GenerationRegistryContract.fromDefinitionsAndGeneratedSources(
                Map.of(key, GenerationRegistryContractFactory.fingerprintGeneratedSemantic(key, generatedSource)),
                Map.of(key, generatedSource)
        );

        String rerendered = GenerationRegistryContractFactory.renderGeneratedSource(
                contract,
                key,
                new TaggedBiomeFixer("second")
        );

        assertTrue(rerendered.contains("second"));
        assertNotEquals(source, rerendered);
        JsonObject expected = JsonParser.parseString(source).getAsJsonObject();
        expected.addProperty("serializer", "second");
        assertEquals(expected, JsonParser.parseString(rerendered));
    }

    @Test
    public void rejectsAChangedPackBeforeCapture() throws Exception {
        File pack = temporary.newFolder("changed-pack");
        String original = GenerationPackFingerprint.compute(
                pack.toPath(),
                GenerationPackFingerprint.CURRENT_VERSION
        );
        Files.writeString(pack.toPath().resolve("dimension.json"), "{}", StandardCharsets.UTF_8);
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(pack);

        assertThrows(
                IOException.class,
                () -> GenerationRegistryContractFactory.create(
                        data,
                        new IrisDimension(),
                        original,
                        DataVersion.getLatest().get(),
                        mock(PlatformRegistries.class),
                        new FakeGenerationRegistry("runtime")
                )
        );
    }

    @Test
    public void rejectsAnUnloadableAdditionalDimensionBeforeCapturingItsRegistry() throws Exception {
        File pack = temporary.newFolder("missing-secondary-dimension");
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(pack);
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("overworld");
        ResourceLoader<IrisDimension> loader = stubDimensions(data, dimension);
        when(loader.getPossibleKeys()).thenReturn(new String[]{"overworld", "upper"});
        String fingerprint = GenerationPackFingerprint.compute(pack.toPath(), GenerationPackFingerprint.CURRENT_VERSION);

        IOException exception = assertThrows(IOException.class, () -> GenerationRegistryContractFactory.create(
                data, dimension, fingerprint, DataVersion.getLatest().get(),
                mock(PlatformRegistries.class), new FakeGenerationRegistry("runtime")));

        assertTrue(exception.getMessage().contains("Unable to load Iris dimension 'upper'"));
    }

    @SuppressWarnings("unchecked")
    private static ResourceLoader<IrisDimension> stubDimensions(IrisData data, IrisDimension dimension) {
        ResourceLoader<IrisDimension> loader = mock(ResourceLoader.class);
        String dimensionKey = dimension.getLoadKey();
        when(data.getDimensionLoader()).thenReturn(loader);
        when(loader.getPossibleKeys()).thenReturn(new String[]{dimensionKey});
        return loader;
    }

    private static GenerationRegistryContract contract(
            FakeGenerationRegistry registry,
            GenerationRegistryContract.PhysicalResourceKey... keys
    ) {
        Map<GenerationRegistryContract.PhysicalResourceKey, String> definitions = new LinkedHashMap<>();
        for (GenerationRegistryContract.PhysicalResourceKey key : keys) {
            PlatformGenerationRegistry.Definition definition = registry.definition(
                    key.registryKey(),
                    key.resourceKey()
            );
            definitions.put(
                    key,
                    GenerationRegistryContractFactory.fingerprintDefinition(
                            key,
                            definition,
                            registry.runtimeIdentity()
                    )
            );
        }
        return GenerationRegistryContract.fromDefinitions(definitions);
    }

    private static GenerationRegistryContract.PhysicalResourceKey key(String registry, String resource) {
        return new GenerationRegistryContract.PhysicalResourceKey(registry, resource);
    }

    private record TaggedBiomeFixer(String tag) implements IDataFixer {
        @Override
        public JSONObject fixCustomBiome(IrisBiomeCustom biome, JSONObject json) {
            return json.put("renderer", tag);
        }

        @Override
        public JSONObject resolve(Dimension dimension, IrisDimensionTypeOptions options) {
            return new JSONObject().put("serializer", tag).put("options",
                    new JSONObject(new GsonBuilder().serializeNulls().create().toJson(options)));
        }

        @Override
        public void fixDimension(Dimension dimension, JSONObject json) {
        }
    }

    private static class FakeGenerationRegistry implements PlatformGenerationRegistry {
        private final String runtimeIdentity;
        private final Map<GenerationRegistryContract.PhysicalResourceKey, Definition> definitions =
                new LinkedHashMap<>();

        private FakeGenerationRegistry(String runtimeIdentity) {
            this.runtimeIdentity = runtimeIdentity;
        }

        @Override
        public String runtimeIdentity() {
            return runtimeIdentity;
        }

        @Override
        public String customBiomeResourceKey(String identitySha256) {
            return "test:biomes/" + identitySha256;
        }

        @Override
        public List<String> legacyCustomBiomeResourceKeys(
                String packName,
                String dimensionKey,
                String customBiomeId
        ) {
            return List.of("test:" + dimensionKey + "/" + customBiomeId);
        }

        @Override
        public String dimensionTypeResourceKey(String packName, String dimensionKey, String dimensionTypeKey) {
            return "test:" + dimensionKey + "_type";
        }

        @Override
        public Definition definition(String registryKey, String resourceKey) {
            GenerationRegistryContract.PhysicalResourceKey key = key(registryKey, resourceKey);
            Definition definition = definitions.get(key);
            if (definition == null && !definitions.containsKey(key)) {
                definition = Definition.resourceIdentity(registryKey, resourceKey);
                definitions.put(key, definition);
            }
            return definition;
        }

        @Override
        public Definition canonicalDefinition(String registryKey, String resourceKey, String sourceJson) {
            return Definition.exactJson(sourceJson);
        }

        @Override
        public Definition generatedDefinition(String registryKey, String resourceKey) {
            Definition definition = definitions.get(key(registryKey, resourceKey));
            if (definition == null || definition.encoding() != Encoding.JSON) {
                throw new IllegalStateException("Missing generated definition " + resourceKey);
            }
            return definition;
        }

        private void put(GenerationRegistryContract.PhysicalResourceKey key, Definition definition) {
            definitions.put(key, definition);
        }

        private void remove(GenerationRegistryContract.PhysicalResourceKey key) {
            definitions.put(key, null);
        }
    }
}
