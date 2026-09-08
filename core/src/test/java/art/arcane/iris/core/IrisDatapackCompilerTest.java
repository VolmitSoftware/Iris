package art.arcane.iris.core;

import art.arcane.iris.core.lifecycle.BukkitWorldConfiguration.IrisGeneratorBinding;
import art.arcane.iris.core.lifecycle.BukkitStartupPaths;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.datapack.DataVersion;
import art.arcane.iris.core.nms.datapack.v1217.DataFixerV1217;
import art.arcane.iris.core.pack.DefaultPackBootstrapProvisioner;
import art.arcane.iris.engine.history.GenerationEpochContractFactory;
import art.arcane.iris.engine.history.GenerationHistory;
import art.arcane.iris.engine.history.GenerationPackFingerprint;
import art.arcane.iris.engine.history.GenerationRegistryContract;
import art.arcane.iris.engine.history.GenerationRegistryContractFactory;
import art.arcane.iris.engine.object.IrisBiomeCustom;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.DurabilityMode;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisDatapackCompilerTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void compilesInstalledAndWorldLocalPacksIntoOneDatapack() throws Exception {
        Path dataDirectory = temporaryFolder.newFolder("data").toPath();
        Path serverRoot = temporaryFolder.newFolder("server").toPath();
        createPack(dataDirectory.resolve("packs/alpha"), "alpha", "alpha_custom");
        createPack(dataDirectory.resolve("packs/beta"), "beta", "beta_custom");
        createPack(dataDirectory.resolve("packs/.iris-import-stale"), "hidden", "hidden_custom");
        createPack(serverRoot.resolve("dimensions/example/world/iris/pack"), "world_local", "world_custom");
        createPack(serverRoot.resolve("dimensions/example/.iris-delete-stale/iris/pack"), "deleted", "deleted_custom");

        List<File> packRoots = IrisDatapackCompiler.collectPackRoots(dataDirectory, serverRoot);
        Path datapackRoot = temporaryFolder.newFolder("datapack").toPath();
        Path stale = datapackRoot.resolve("data/iris/dimension_type/removed.json");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "stale", StandardCharsets.UTF_8);
        IrisDatapackCompiler.CompilationResult result = IrisDatapackCompiler.compile(
                packRoots,
                new KList<File>().qadd(datapackRoot.toFile()),
                List.of(),
                new DataFixerV1217(),
                false
        );

        assertEquals(3, result.packCount());
        assertEquals(3, result.dimensionCount());
        assertEquals(3, result.biomeCount());
        assertTrue(Files.isRegularFile(datapackRoot.resolve("pack.mcmeta")));
        assertTrue(Files.isRegularFile(datapackRoot.resolve("data/iris/dimension_type/alpha.json")));
        assertTrue(Files.isRegularFile(datapackRoot.resolve("data/iris/dimension_type/beta.json")));
        assertTrue(Files.isRegularFile(datapackRoot.resolve("data/iris/dimension_type/world_local.json")));
        assertEquals(3, regularFileCount(datapackRoot.resolve("data/iris/worldgen/biome/biomes")));
        assertFalse(Files.exists(datapackRoot.resolve("data/alpha/worldgen/biome/alpha_custom.json")));
        assertFalse(Files.exists(datapackRoot.resolve("data/beta/worldgen/biome/beta_custom.json")));
        assertTrue(Files.isRegularFile(datapackRoot.resolve("data/world_local/worldgen/biome/world_custom.json")));
        assertFalse(Files.exists(datapackRoot.resolve("data/iris/dimension_type/hidden.json")));
        assertFalse(Files.exists(datapackRoot.resolve("data/iris/dimension_type/deleted.json")));
        assertFalse(Files.exists(stale));
    }

    @Test
    public void compilesExternalPackFixtureWhenConfigured() throws Exception {
        String configuredPack = System.getenv("IRIS_TEST_PACK");
        Assume.assumeTrue(configuredPack != null && !configuredPack.isBlank());
        Path packRoot = Path.of(configuredPack).toAbsolutePath().normalize();
        Assume.assumeTrue(Files.isDirectory(packRoot.resolve("dimensions")));
        Path datapackRoot = temporaryFolder.newFolder("external-datapack").toPath();

        IrisDatapackCompiler.CompilationResult result = IrisDatapackCompiler.compile(
                List.of(packRoot.toFile()),
                new KList<File>().qadd(datapackRoot.toFile()),
                List.of(),
                new DataFixerV1217(),
                false
        );

        assertTrue(result.packCount() > 0);
        assertTrue(result.dimensionCount() > 0);
        assertTrue(result.biomeCount() > 0);
        assertTrue(Files.isRegularFile(datapackRoot.resolve("pack.mcmeta")));
    }

    @Test
    public void compilesRecursiveDimensionBiomeToContentAddressedRegistryPath() throws Exception {
        Path packRoot = temporaryFolder.newFolder("recursive-dimension-pack").toPath();
        Path datapackRoot = temporaryFolder.newFolder("recursive-dimension-datapack").toPath();
        createPack(packRoot, "layers/sky", "Aurora");

        IrisDatapackCompiler.compile(
                List.of(packRoot.toFile()),
                new KList<File>().qadd(datapackRoot.toFile()),
                List.of(),
                new DataFixerV1217(),
                false
        );

        assertEquals(1, regularFileCount(
                datapackRoot.resolve("data/iris/worldgen/biome/biomes")));
        assertFalse(Files.exists(
                datapackRoot.resolve("data/layers/worldgen/biome/sky/aurora.json")));
        assertTrue(Files.isRegularFile(
                datapackRoot.resolve("data/iris/dimension_type/layers_sky.json")));
    }

    @Test
    public void collectsInstalledPacksThroughSymbolicLinkWorkspace() throws Exception {
        Path dataDirectory = temporaryFolder.newFolder("linked-data").toPath();
        Path serverRoot = temporaryFolder.newFolder("linked-server").toPath();
        Path sharedPacks = temporaryFolder.newFolder("linked-shared-packs").toPath();
        Path overworld = sharedPacks.resolve("overworld");
        createPack(overworld, "overworld", "linked_custom");
        try {
            Files.createSymbolicLink(dataDirectory.resolve("packs"), sharedPacks);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assume.assumeNoException(exception);
        }

        assertEquals(List.of(dataDirectory.resolve("packs/overworld").toFile()),
                IrisDatapackCompiler.collectPackRoots(dataDirectory, serverRoot));
    }

    @Test
    public void packMcmetaSpansEverySupportedRuntimeFormat() throws Exception {
        Path datapackRoot = temporaryFolder.newFolder("mcmeta-datapack").toPath();

        IrisDatapackCompiler.compile(
                List.of(),
                new KList<File>().qadd(datapackRoot.toFile()),
                List.of(),
                new DataFixerV1217(),
                false
        );

        JSONObject pack = new JSONObject(Files.readString(datapackRoot.resolve("pack.mcmeta"), StandardCharsets.UTF_8))
                .getJSONObject("pack");
        int minFormat = pack.getInt("min_format");
        int maxFormat = pack.getInt("max_format");

        assertEquals(101, minFormat);
        assertEquals(107, maxFormat);
        assertEquals(maxFormat, pack.getInt("pack_format"));

        // One artifact serves both runtimes: each supported DataVersion's format must be in range.
        assertTrue(minFormat <= DataVersion.V26_1_2.getPackFormat()
                && DataVersion.V26_1_2.getPackFormat() <= maxFormat);
        assertTrue(minFormat <= DataVersion.V26_2.getPackFormat()
                && DataVersion.V26_2.getPackFormat() <= maxFormat);
    }

    @Test
    public void compilingNoPacksPublishesCleanEmptyDatapack() throws Exception {
        Path datapackRoot = temporaryFolder.newFolder("empty-datapack").toPath();
        Path stale = datapackRoot.resolve("data/iris/dimension_type/removed.json");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "stale", StandardCharsets.UTF_8);

        IrisDatapackCompiler.CompilationResult result = IrisDatapackCompiler.compile(
                List.of(),
                new KList<File>().qadd(datapackRoot.toFile()),
                List.of(),
                new DataFixerV1217(),
                false
        );

        assertEquals(0, result.packCount());
        assertEquals(0, result.dimensionCount());
        assertEquals(0, result.biomeCount());
        assertTrue(Files.isRegularFile(datapackRoot.resolve("pack.mcmeta")));
        assertFalse(Files.exists(stale));
    }

    @Test
    public void emitsBootNativeCustomLevelStemBinding() throws Exception {
        Path packRoot = temporaryFolder.newFolder("binding-pack").toPath();
        Path datapackRoot = temporaryFolder.newFolder("binding-datapack").toPath();
        createPack(packRoot, "moon_pack", "moon_custom");

        IrisDatapackCompiler.compile(
                List.of(packRoot.toFile()),
                new KList<File>().qadd(datapackRoot.toFile()),
                List.of(binding("moon", "moon_pack")),
                new DataFixerV1217(),
                false
        );

        Path levelStemPath = datapackRoot.resolve("data/iris/dimension/moon.json");
        JSONObject levelStem = new JSONObject(Files.readString(levelStemPath, StandardCharsets.UTF_8));
        JSONObject generator = levelStem.getJSONObject("generator");
        JSONObject settings = generator.getJSONObject("settings");
        assertEquals("iris:moon_pack", levelStem.getString("type"));
        assertEquals("minecraft:flat", generator.getString("type"));
        assertEquals("minecraft:the_void", settings.getString("biome"));
        assertFalse(settings.getBoolean("features"));
        assertFalse(settings.getBoolean("lakes"));
        assertEquals(1, settings.getJSONArray("layers").length());
        assertEquals(
                "minecraft:air",
                settings.getJSONArray("layers").getJSONObject(0).getString("block")
        );
        assertEquals(1, settings.getJSONArray("layers").getJSONObject(0).getInt("height"));
        assertEquals(0, settings.getJSONArray("structure_overrides").length());
        assertFalse(Files.exists(datapackRoot.resolve("data/minecraft/dimension/overworld.json")));
        assertFalse(Files.exists(datapackRoot.resolve("data/minecraft/dimension/the_nether.json")));
        assertFalse(Files.exists(datapackRoot.resolve("data/minecraft/dimension/the_end.json")));
    }

    @Test
    public void equivalentFrozenPackAndLevelStemBindingDoNotChangeRegistryRequirements() throws Exception {
        Path installedPack = temporaryFolder.newFolder("registry-installed-pack").toPath();
        Path frozenPack = temporaryFolder.newFolder("registry-frozen-pack").toPath();
        createPack(installedPack, "overworld", "forest_custom");
        createPack(frozenPack, "overworld", "forest_custom");
        DataFixerV1217 fixer = new DataFixerV1217();

        Map<String, String> loaded = IrisDatapackCompiler.computeRegistryRequirements(
                List.of(installedPack.toFile()),
                fixer);
        Map<String, String> afterWorldCreation = IrisDatapackCompiler.computeRegistryRequirements(
                List.of(installedPack.toFile(), frozenPack.toFile()),
                fixer);
        String loadedCompilerInputs = IrisDatapackCompiler.computeInputFingerprint(
                List.of(installedPack.toFile()),
                List.of(),
                false,
                "compiler-a");
        String afterWorldCreationCompilerInputs = IrisDatapackCompiler.computeInputFingerprint(
                List.of(installedPack.toFile(), frozenPack.toFile()),
                List.of(binding("ow", "overworld")),
                false,
                "compiler-a");

        assertNotEquals(loadedCompilerInputs, afterWorldCreationCompilerInputs);
        assertEquals(loaded, afterWorldCreation);
        assertTrue(ServerConfigurator.loadedRegistrySatisfies(loaded, afterWorldCreation));
    }

    @Test
    public void changedDimensionRegistryRequirementsDoNotReuseLoadedRuntime() throws Exception {
        Path pack = temporaryFolder.newFolder("registry-changed-pack").toPath();
        createPack(pack, "overworld", "forest_custom", "NORMAL");
        DataFixerV1217 fixer = new DataFixerV1217();
        Map<String, String> loaded = IrisDatapackCompiler.computeRegistryRequirements(
                List.of(pack.toFile()),
                fixer);

        createPack(pack, "overworld", "forest_custom", "NETHER");
        Map<String, String> changedDimensionType = IrisDatapackCompiler.computeRegistryRequirements(
                List.of(pack.toFile()),
                fixer);
        assertFalse(ServerConfigurator.loadedRegistrySatisfies(loaded, changedDimensionType));

        createPack(pack, "overworld", "new_custom", "NORMAL");
        Map<String, String> changedCustomBiome = IrisDatapackCompiler.computeRegistryRequirements(
                List.of(pack.toFile()),
                fixer);
        assertFalse(ServerConfigurator.loadedRegistrySatisfies(loaded, changedCustomBiome));

        createPack(pack, "overworld", "forest_custom", "NORMAL");
        Path biomeFile = pack.resolve("biomes/test.json");
        String taggedBiome = Files.readString(biomeFile, StandardCharsets.UTF_8)
                .replace("\"id\": \"forest_custom\"", "\"id\": \"forest_custom\", \"tags\": [\"is_hot\"]");
        Files.writeString(biomeFile, taggedBiome, StandardCharsets.UTF_8);
        Map<String, String> changedBiomeTags = IrisDatapackCompiler.computeRegistryRequirements(
                List.of(pack.toFile()),
                fixer);
        assertFalse(ServerConfigurator.loadedRegistrySatisfies(loaded, changedBiomeTags));
    }

    @Test
    public void stackRuntimeRequirementsIncludeSourceDimensionRegistries() throws Exception {
        Path pack = temporaryFolder.newFolder("registry-stack-pack").toPath();
        createPack(pack, "host", "stack_custom", "NORMAL");
        Files.writeString(
                pack.resolve("dimensions/host.json"),
                """
                        {
                          "name": "Host",
                          "environment": "NORMAL",
                          "logicalHeight": 256,
                          "dimensionHeight": {"min": -64, "max": 320},
                          "dimensionStack": {
                            "dimensions": ["layers/source", "host"],
                            "spacer": 8
                          }
                        }
                        """,
                StandardCharsets.UTF_8
        );
        Files.createDirectories(pack.resolve("dimensions/layers"));
        Files.writeString(
                pack.resolve("dimensions/layers/source.json"),
                """
                        {
                          "name": "Source",
                          "environment": "NORMAL",
                          "logicalHeight": 256,
                          "dimensionHeight": {"min": -64, "max": 320}
                        }
                        """,
                StandardCharsets.UTF_8
        );

        IrisData data = IrisData.openDatapackCompiler(pack.toFile());
        try {
            IrisDimension host = data.getDimensionLoader().load("host");
            Map<String, String> requirements = IrisDatapackCompiler.computeRegistryRequirements(
                    host,
                    new DataFixerV1217()
            );

            IrisBiomeCustom customBiome = data.getBiomeLoader().load("test")
                    .getCustomDerivitives().getFirst();
            String hostBiomeKey = GenerationRegistryContractFactory.customBiomeResourceKey(
                    "host",
                    customBiome,
                    data.getContentGate()
            );
            String sourceBiomeKey = GenerationRegistryContractFactory.customBiomeResourceKey(
                    "layers/source",
                    customBiome,
                    data.getContentGate()
            );
            assertTrue(requirements.containsKey("dimension_type/iris:host"));
            assertTrue(requirements.containsKey("dimension_type/iris:layers_source"));
            assertTrue(requirements.containsKey("worldgen/biome/" + hostBiomeKey));
            assertTrue(requirements.containsKey("worldgen/biome/" + sourceBiomeKey));
        } finally {
            data.close();
        }
    }

    @Test
    public void rejectsBindingToMissingDimension() throws Exception {
        Path packRoot = temporaryFolder.newFolder("missing-binding-pack").toPath();
        Path datapackRoot = temporaryFolder.newFolder("missing-binding-datapack").toPath();
        createPack(packRoot, "available", "available_custom");

        IOException failure = assertThrows(
                IOException.class,
                () -> IrisDatapackCompiler.compile(
                        List.of(packRoot.toFile()),
                        new KList<File>().qadd(datapackRoot.toFile()),
                        List.of(binding("moon", "missing")),
                        new DataFixerV1217(),
                        false
                )
        );

        assertTrue(failure.getMessage().contains("selects missing dimension \"missing\""));
    }

    @Test
    public void rejectsBindingToConflictingDuplicateDimension() throws Exception {
        Path firstPack = temporaryFolder.newFolder("ambiguous-binding-first").toPath();
        Path secondPack = temporaryFolder.newFolder("ambiguous-binding-second").toPath();
        Path datapackRoot = temporaryFolder.newFolder("ambiguous-binding-datapack").toPath();
        createPack(firstPack, "shared", "first_custom", "NORMAL");
        createPack(secondPack, "shared", "second_custom", "NETHER");

        IOException failure = assertThrows(
                IOException.class,
                () -> IrisDatapackCompiler.compile(
                        List.of(firstPack.toFile(), secondPack.toFile()),
                        new KList<File>().qadd(datapackRoot.toFile()),
                        List.of(binding("moon", "shared")),
                        new DataFixerV1217(),
                        false
                )
        );

        assertTrue(failure.getMessage().contains("selects ambiguous dimension \"shared\""));
    }

    @Test
    public void acceptsIdenticalFrozenAndInstalledDimensionDefinitions() throws Exception {
        Path installedPack = temporaryFolder.newFolder("identical-binding-installed").toPath();
        Path frozenPack = temporaryFolder.newFolder("identical-binding-frozen").toPath();
        Path datapackRoot = temporaryFolder.newFolder("identical-binding-datapack").toPath();
        createPack(installedPack, "shared", "installed_custom");
        createPack(frozenPack, "shared", "frozen_custom");

        IrisDatapackCompiler.compile(
                List.of(installedPack.toFile(), frozenPack.toFile()),
                new KList<File>().qadd(datapackRoot.toFile()),
                List.of(binding("moon", "shared")),
                new DataFixerV1217(),
                false
        );

        assertTrue(Files.isRegularFile(datapackRoot.resolve("data/iris/dimension/moon.json")));
    }

    @Test
    public void changedCustomBiomeDefinitionsCoexistUnderContentKeys() throws Exception {
        Path firstPack = temporaryFolder.newFolder("content-biome-first").toPath();
        Path secondPack = temporaryFolder.newFolder("content-biome-second").toPath();
        Path datapackRoot = temporaryFolder.newFolder("content-biome-datapack").toPath();
        createPack(firstPack, "shared", "shared_custom");
        createPack(secondPack, "shared", "shared_custom");
        setCustomBiomeTemperature(secondPack, 1.25);

        IrisDatapackCompiler.compile(
                List.of(firstPack.toFile(), secondPack.toFile()),
                new KList<File>().qadd(datapackRoot.toFile()),
                List.of(),
                new DataFixerV1217(),
                false
        );

        assertEquals(2, regularFileCount(datapackRoot.resolve("data/iris/worldgen/biome/biomes")));
        assertFalse(Files.exists(datapackRoot.resolve("data/shared/worldgen/biome/shared_custom.json")));
    }

    @Test
    public void retainedPacksCompileBeforeAPlatformIsBound() throws Exception {
        assertRetainedPackCompilesBeforePlatformBinding(false);
    }

    @Test
    public void retainedMultiDimensionPacksCompileAndBootstrapBeforePlatformBinding() throws Exception {
        assertRetainedPackCompilesBeforePlatformBinding(true);
    }

    private void assertRetainedPackCompilesBeforePlatformBinding(boolean multipleDimensions) throws Exception {
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        Path pack = temporaryFolder.newFolder("unbound-retained-pack").toPath();
        Path server = temporaryFolder.newFolder("unbound-retained-server").toPath();
        Path world = server.resolve("world/dimensions/iris/history");
        Files.createDirectories(world);
        Path output = temporaryFolder.newFolder("unbound-retained-output").toPath();
        createPack(pack, "overworld", "retained_custom");
        if (multipleDimensions) {
            createPack(pack, "upper", "retained_custom", "NETHER");
        }
        IrisData data = IrisData.openDatapackCompiler(pack.toFile());
        try {
            IrisDimension dimension = data.getDimensionLoader().load("overworld");
            String fingerprint = GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION);
            PlatformRegistries registries = mock(PlatformRegistries.class);
            when(registries.blockTypeKeys()).thenReturn(List.of());
            when(registries.entityKeys()).thenReturn(List.of());
            PlatformGenerationRegistry registry = new BootstrapGenerationRegistry();
            GenerationRegistryContract contract = GenerationRegistryContractFactory.create(
                    data, dimension, fingerprint, new DataFixerV1217(), registries, registry);
            GenerationHistory history = GenerationHistory.create(world, pack, fingerprint, 77L,
                    GenerationEpochContractFactory.create(dimension, "overworld", "iris:overworld"), contract);
            Path retainedPack = history.paths().packRoot(history.manifest().activeEpoch().epochId());
            IrisDatapackCompiler.CompilationResult compiled = IrisDatapackCompiler.compile(List.of(retainedPack.toFile()),
                    new KList<File>().qadd(output.toFile()), List.of(), new DataFixerV1217(), false);
            assertFalse(IrisPlatforms.isBound());
            int expectedDimensions = multipleDimensions ? 2 : 1;
            assertEquals(expectedDimensions, compiled.dimensionCount());
            assertEquals(expectedDimensions, regularFileCount(output.resolve("data/iris/worldgen/biome/biomes")));
            Map<String, String> requirements = IrisDatapackCompiler.computeRegistryRequirements(
                    List.of(retainedPack.toFile()), new DataFixerV1217());
            assertTrue(requirements.containsKey("dimension_type/iris:overworld"));
            if (multipleDimensions) {
                assertTrue(requirements.containsKey("dimension_type/iris:upper"));
                assertTrue(Files.isRegularFile(output.resolve("data/iris/dimension_type/upper.json")));
                assertNotEquals(Files.readString(output.resolve("data/iris/dimension_type/overworld.json")),
                        Files.readString(output.resolve("data/iris/dimension_type/upper.json")));
                Path dataDirectory = server.resolve("plugins/Iris");
                BukkitStartupPaths startupPaths = BukkitStartupPaths.resolve(server, new String[0]);
                DefaultPackBootstrapProvisioner.ProvisionResult installed =
                        DefaultPackBootstrapProvisioner.provision(dataDirectory, ignored -> {
                        }, startupPaths);
                assertEquals(DefaultPackBootstrapProvisioner.ProvisionStatus.INSTALLED, installed.status());
                assertTrue(Files.isRegularFile(installed.datapackRoot().resolve("data/iris/dimension_type/upper.json")));
                assertEquals(2, regularFileCount(installed.datapackRoot().resolve("data/iris/worldgen/biome/biomes")));
                DefaultPackBootstrapProvisioner.ProvisionResult unchanged =
                        DefaultPackBootstrapProvisioner.provision(dataDirectory, ignored -> {
                        }, startupPaths);
                assertEquals(DefaultPackBootstrapProvisioner.ProvisionStatus.UNCHANGED, unchanged.status());
                assertFalse(IrisPlatforms.isBound());
            }
        } finally {
            data.close();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    @Test
    public void duplicateCustomIdsPreservePhysicalBiomesAndDeterministicLegacyAlias() throws Exception {
        Path pack = temporaryFolder.newFolder("duplicate-custom-biomes").toPath().resolve("iris/pack");
        Path output = temporaryFolder.newFolder("duplicate-custom-output").toPath();
        createPack(pack, "overworld", "savanna_plateau");
        Path secondBiome = pack.resolve("biomes/second.json");
        Files.writeString(secondBiome, """
                {"name":"Second Plateau","derivative":"minecraft:plains",
                 "customDerivitives":[{"id":"savanna_plateau","grassColor":"#BFB755"}]}
                """, StandardCharsets.UTF_8);
        IrisDatapackCompiler.CompilationResult compiled = IrisDatapackCompiler.compile(
                List.of(pack.toFile()), new KList<File>().qadd(output.toFile()),
                List.of(), new DataFixerV1217(), false);
        assertEquals(2, compiled.biomeCount());
        assertEquals(2, regularFileCount(output.resolve("data/iris/worldgen/biome/biomes")));
        Map<String, String> requirements = IrisDatapackCompiler.computeRegistryRequirements(
                List.of(pack.toFile()), new DataFixerV1217());
        assertTrue(requirements.containsKey("worldgen/biome/overworld:savanna_plateau"));
        IrisData data = IrisData.openDatapackCompiler(pack.toFile());
        try {
            IrisDimension dimension = data.getDimensionLoader().load("overworld");
            IrisBiomeCustom first = data.getBiomeLoader().load("test").getCustomDerivitives().getFirst();
            IrisBiomeCustom second = data.getBiomeLoader().load("second").getCustomDerivitives().getFirst();
            String firstKey = data.customBiomeResourceKey(dimension, first);
            String secondKey = data.customBiomeResourceKey(dimension, second);
            assertNotEquals(firstKey, secondKey);
            String selected = firstKey.compareTo(secondKey) < 0 ? firstKey : secondKey;
            assertEquals(selected, data.customBiomeResourceKey(dimension, "savanna_plateau"));
            Path physicalFile = output.resolve("data/iris/worldgen/biome/" + selected.substring("iris:".length()) + ".json");
            assertEquals(Files.readString(physicalFile),
                    Files.readString(output.resolve("data/overworld/worldgen/biome/savanna_plateau.json")));
        } finally {
            data.close();
        }
    }

    @Test
    public void sharedPhysicalBiomesMergeTagRequirementsAcrossPacks() throws Exception {
        Path first = temporaryFolder.newFolder("biome-tags-first").toPath();
        Path second = temporaryFolder.newFolder("biome-tags-second").toPath();
        Path output = temporaryFolder.newFolder("biome-tags-output").toPath();
        createPack(first, "shared", "shared_custom");
        createPack(second, "shared", "shared_custom");
        Path changed = second.resolve("biomes/test.json");
        Files.writeString(changed, Files.readString(changed).replace(
                "\"id\": \"shared_custom\"",
                "\"id\": \"shared_custom\", \"tags\": [\"example:second\"]"));
        Map<String, String> requirements = IrisDatapackCompiler.computeRegistryRequirements(
                List.of(first.toFile(), second.toFile()), new DataFixerV1217());
        Map<String, String> reversed = IrisDatapackCompiler.computeRegistryRequirements(
                List.of(second.toFile(), first.toFile()), new DataFixerV1217());
        assertEquals(requirements, reversed);
        IrisDatapackCompiler.compile(List.of(first.toFile(), second.toFile()),
                new KList<File>().qadd(output.toFile()), List.of(), new DataFixerV1217(), false);
        assertEquals(1, regularFileCount(output.resolve("data/iris/worldgen/biome/biomes")));
        assertTrue(Files.isRegularFile(output.resolve("data/example/tags/worldgen/biome/second.json")));
    }

    @Test
    public void conflictingLegacyBiomeAliasesFailClosed() throws Exception {
        Path firstPack = temporaryFolder.newFolder("legacy-biome-first").toPath().resolve("iris/pack");
        Path secondPack = temporaryFolder.newFolder("legacy-biome-second").toPath().resolve("iris/pack");
        Path datapackRoot = temporaryFolder.newFolder("legacy-biome-datapack").toPath();
        createPack(firstPack, "shared", "shared_custom");
        createPack(secondPack, "shared", "shared_custom");
        setCustomBiomeTemperature(secondPack, 1.25);

        IOException failure = assertThrows(
                IOException.class,
                () -> IrisDatapackCompiler.compile(
                        List.of(firstPack.toFile(), secondPack.toFile()),
                        new KList<File>().qadd(datapackRoot.toFile()),
                        List.of(),
                        new DataFixerV1217(),
                        false
                )
        );

        assertTrue(failure.getMessage().contains("Conflicting custom biome output"));
    }

    private static void createPack(Path root, String dimensionKey, String biomeId) throws Exception {
        createPack(root, dimensionKey, biomeId, "NORMAL");
    }

    private static long regularFileCount(Path root) throws IOException {
        try (Stream<Path> files = Files.list(root)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static void setCustomBiomeTemperature(Path packRoot, double temperature) throws IOException {
        Path biome = packRoot.resolve("biomes/test.json");
        String source = Files.readString(biome, StandardCharsets.UTF_8);
        Files.writeString(
                biome,
                source.replace("\"id\": \"shared_custom\"",
                        "\"id\": \"shared_custom\", \"temperature\": " + temperature),
                StandardCharsets.UTF_8
        );
    }

    private static void createPack(
            Path root,
            String dimensionKey,
            String biomeId,
            String environment
    ) throws Exception {
        Files.createDirectories(root.resolve("dimensions"));
        Files.createDirectories(root.resolve("biomes"));
        Files.createDirectories(root.resolve("dimensions").resolve(dimensionKey).getParent());
        Files.writeString(
                root.resolve("dimensions").resolve(dimensionKey + ".json"),
                """
                        {
                          "name": "Test Dimension",
                          "environment": "%s",
                          "logicalHeight": 256,
                          "dimensionHeight": {
                            "min": -64,
                            "max": 320
                          }
                        }
                        """.formatted(environment),
                StandardCharsets.UTF_8
        );
        Files.writeString(
                root.resolve("biomes/test.json"),
                """
                        {
                          "name": "Test Biome",
                          "derivative": "minecraft:plains",
                          "vanillaDerivative": "minecraft:plains",
                          "customDerivitives": [
                            {
                              "id": "%s"
                            }
                          ]
                        }
                        """.formatted(biomeId),
                StandardCharsets.UTF_8
        );
    }

    private static IrisGeneratorBinding binding(String worldKey, String dimension) {
        return new IrisGeneratorBinding(
                "world_iris_" + worldKey,
                new WorldSlotKey("iris", worldKey),
                dimension
        );
    }

    private static final class BootstrapGenerationRegistry implements PlatformGenerationRegistry {
        @Override
        public String runtimeIdentity() {
            return "bootstrap-test";
        }

        @Override
        public String generatedDefinitionRendererIdentity() {
            return "bootstrap-renderer-v1";
        }

        @Override
        public String dimensionTypeResourceKey(String packName, String dimensionKey, String dimensionTypeKey) {
            return "iris:" + dimensionTypeKey;
        }

        @Override
        public Definition definition(String registryKey, String resourceKey) {
            return Definition.resourceIdentity(registryKey, resourceKey);
        }
    }
}
