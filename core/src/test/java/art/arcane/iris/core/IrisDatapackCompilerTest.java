package art.arcane.iris.core;

import art.arcane.iris.core.lifecycle.BukkitWorldConfiguration.IrisGeneratorBinding;
import art.arcane.iris.core.nms.datapack.DataVersion;
import art.arcane.iris.core.nms.datapack.v1217.DataFixerV1217;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONObject;
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
import java.util.Optional;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisDatapackCompilerTest {
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
        assertTrue(Files.isRegularFile(datapackRoot.resolve("data/alpha/worldgen/biome/alpha_custom.json")));
        assertTrue(Files.isRegularFile(datapackRoot.resolve("data/beta/worldgen/biome/beta_custom.json")));
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
    public void aPackThatCannotLoadAndNoWorldUsesIsLeftOut() throws Exception {
        Path dataDirectory = temporaryFolder.newFolder("unused-refused-data").toPath();
        Path serverRoot = temporaryFolder.newFolder("unused-refused-server").toPath();
        createPack(dataDirectory.resolve("packs/healthy"), "healthy", "healthy_custom");
        createRefusedPack(dataDirectory.resolve("packs/refused"), "refused");
        Path datapackRoot = temporaryFolder.newFolder("unused-refused-datapack").toPath();

        IrisDatapackCompiler.CompilationResult result = IrisDatapackCompiler.compile(
                IrisDatapackCompiler.collectPackRoots(dataDirectory, serverRoot),
                new KList<File>().qadd(datapackRoot.toFile()),
                List.of(binding("main", "healthy")),
                new DataFixerV1217(),
                false
        );

        assertEquals(1, result.packCount());
        assertTrue(Files.isRegularFile(datapackRoot.resolve("data/iris/dimension_type/healthy.json")));
        assertFalse(Files.exists(datapackRoot.resolve("data/iris/dimension_type/refused.json")));
    }

    @Test
    public void aPackThatCannotLoadAddsNoRegistryRequirements() throws Exception {
        Path healthyPack = temporaryFolder.newFolder("registry-healthy-pack").toPath();
        Path refusedPack = temporaryFolder.newFolder("registry-refused-pack").toPath();
        createPack(healthyPack, "healthy", "healthy_custom");
        createRefusedPack(refusedPack, "refused");
        DataFixerV1217 fixer = new DataFixerV1217();

        Map<String, String> healthyOnly = IrisDatapackCompiler.computeRegistryRequirements(
                List.of(healthyPack.toFile()),
                fixer);
        Map<String, String> withRefused = IrisDatapackCompiler.computeRegistryRequirements(
                List.of(healthyPack.toFile(), refusedPack.toFile()),
                fixer);

        assertEquals(healthyOnly, withRefused);
    }

    @Test
    public void onlyPacksThatCannotLoadStillGiveACompleteDatapack() throws Exception {
        Path packRoot = temporaryFolder.newFolder("only-refused-pack").toPath();
        createRefusedPack(packRoot, "refused");
        Path datapackRoot = temporaryFolder.newFolder("only-refused-datapack").toPath();

        IrisDatapackCompiler.CompilationResult result = IrisDatapackCompiler.compile(
                List.of(packRoot.toFile()),
                new KList<File>().qadd(datapackRoot.toFile()),
                List.of(),
                new DataFixerV1217(),
                false
        );

        assertEquals(0, result.dimensionCount());
        assertTrue(Files.isDirectory(datapackRoot.resolve("data/iris/dimension_type")));
    }

    @Test
    public void aPackThatCannotLoadStopsTheCompileWhenAConfiguredWorldSelectsIt() throws Exception {
        Path packRoot = temporaryFolder.newFolder("selected-refused-pack").toPath();
        createRefusedPack(packRoot, "refused");
        Path datapackRoot = temporaryFolder.newFolder("selected-refused-datapack").toPath();

        IOException failure = assertThrows(IOException.class, () -> IrisDatapackCompiler.compile(
                List.of(packRoot.toFile()),
                new KList<File>().qadd(datapackRoot.toFile()),
                List.of(binding("moon", "refused")),
                new DataFixerV1217(),
                false
        ));

        assertTrue(failure.getMessage(), failure.getMessage().contains("cannot load"));
        assertTrue(failure.getMessage(), failure.getMessage().contains("world_iris_moon"));
    }

    @Test
    public void aWorldsOwnPackThatCannotLoadStopsTheCompileEvenWhenNoWorldIsConfigured() throws Exception {
        Path dataDirectory = temporaryFolder.newFolder("world-refused-data").toPath();
        Path serverRoot = temporaryFolder.newFolder("world-refused-server").toPath();
        createRefusedPack(serverRoot.resolve("dimensions/iris/old_world/iris/pack"), "overworld");
        Path datapackRoot = temporaryFolder.newFolder("world-refused-datapack").toPath();

        IOException failure = assertThrows(IOException.class, () -> IrisDatapackCompiler.compile(
                IrisDatapackCompiler.collectPackRoots(dataDirectory, serverRoot),
                new KList<File>().qadd(datapackRoot.toFile()),
                List.of(),
                new DataFixerV1217(),
                false
        ));

        assertTrue(failure.getMessage(), failure.getMessage().contains("iris:old_world"));
    }

    @Test
    public void aPackInAWorldsFolderBelongsToThatWorld() {
        assertEquals(Optional.of(new WorldSlotKey("iris", "old_world")),
                PackUse.worldOwning(Path.of("/server/world/dimensions/iris/old_world/iris/pack")));
        assertEquals(Optional.empty(), PackUse.worldOwning(Path.of("/server/plugins/Iris/packs/overworld")));
    }

    /** A pack whose modules cannot load: its {@code modules} folder holds a file that is no jar. */
    private static void createRefusedPack(Path root, String dimensionKey) throws Exception {
        createPack(root, dimensionKey, dimensionKey + "_custom");
        Files.createDirectories(root.resolve("modules"));
        Files.writeString(root.resolve("modules/broken.jar"), "not a jar", StandardCharsets.UTF_8);
    }

    private static void createPack(Path root, String dimensionKey, String biomeId) throws Exception {
        createPack(root, dimensionKey, biomeId, "NORMAL");
    }

    private static void createPack(
            Path root,
            String dimensionKey,
            String biomeId,
            String environment
    ) throws Exception {
        Files.createDirectories(root.resolve("dimensions"));
        Files.createDirectories(root.resolve("biomes"));
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
}
