package art.arcane.iris.world.history;

import art.arcane.iris.pack.datapack.IrisDatapackCompiler;
import art.arcane.iris.pack.AtomicDirectoryPublisher;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.platform.bukkit.nms.datapack.v1217.DataFixerV1217;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ArchivedRegistryCompilerTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void rebuildsArchivedRegistryDefinitionsAndTagsWithoutTheOldPack() throws Exception {
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        try {
            Path firstPack = pack("old", "old_custom", "iris:old_tag");
            Path secondPack = pack("new", "new_custom", "iris:new_tag");
            Path world = temporary.newFolder("world").toPath();
            Path output = temporary.newFolder("output").toPath();
            Captured first = capture(firstPack);
            Captured second = capture(secondPack);
            assertFalse(first.registry().biomeTags().isEmpty());
            assertTrue(first.registry().biomeTags().values().stream().anyMatch(tags -> tags.contains("iris:old_tag")));
            GenerationHistory history = GenerationHistory.create(world, firstPack, first.fingerprint(), 77L,
                    first.dimension(), first.registry());
            String oldEpoch = history.activeEpoch().epochId();
            history.stageUpdate(secondPack, second.fingerprint(), second.dimension(), second.registry(), 256);
            history.promotePending(List.of());
            assertTrue(Files.isDirectory(history.paths().packRoot(oldEpoch)));
            AtomicDirectoryPublisher.deleteTree(history.paths().packRoot(oldEpoch));
            assertFalse(Files.exists(history.paths().packRoot(oldEpoch)));
            List<File> packs = List.of(history.activePackRoot().toFile());

            IrisDatapackCompiler.compile(packs, new KList<>(output.toFile()), List.of(), new DataFixerV1217(), false);

            for (String biome : first.registry().biomeTags().keySet()) {
                int separator = biome.indexOf(':');
                Path biomeFile = output.resolve("data").resolve(biome.substring(0, separator))
                        .resolve("worldgen/biome").resolve(biome.substring(separator + 1) + ".json");
                assertTrue(Files.isRegularFile(biomeFile));
                assertTrue(Files.readString(output.resolve("data/iris/tags/worldgen/biome/old_tag.json")).contains(biome));
            }
            Map<String, String> requirements = IrisDatapackCompiler.computeRegistryRequirements(packs, new DataFixerV1217());
            for (String biome : first.registry().biomeTags().keySet()) {
                assertTrue(requirements.containsKey("worldgen/biome/" + biome));
                assertTrue(requirements.containsKey("worldgen/biome_tags/" + biome));
            }
            assertEquals(history.manifest(), GenerationHistory.open(world).manifest());
            assertTrue(Files.isRegularFile(firstPack.resolve("biomes/test.json")));
        } finally {
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    private Captured capture(Path pack) throws Exception {
        IrisData data = IrisData.openDatapackCompiler(pack.toFile());
        try {
            IrisDimension dimension = data.getDimensionLoader().load("overworld");
            String fingerprint = GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION);
            PlatformRegistries registries = mock(PlatformRegistries.class);
            when(registries.blockTypeKeys()).thenReturn(List.of());
            when(registries.entityKeys()).thenReturn(List.of());
            GenerationRegistryContract contract = GenerationRegistryContractFactory.create(data, dimension, fingerprint,
                    new DataFixerV1217(), registries, new Registry());
            return new Captured(fingerprint, GenerationEpochContractFactory.create(dimension, "overworld", "iris:overworld"), contract);
        } finally {
            data.close();
        }
    }

    private Path pack(String name, String custom, String tag) throws Exception {
        Path pack = temporary.newFolder(name).toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.createDirectories(pack.resolve("biomes"));
        Files.writeString(pack.resolve("dimensions/overworld.json"), """
                {"name":"Test","environment":"NORMAL","logicalHeight":256,"dimensionHeight":{"min":-64,"max":320}}
                """);
        Files.writeString(pack.resolve("biomes/test.json"), """
                {"name":"Test","derivative":"minecraft:plains","vanillaDerivative":"minecraft:plains",
                 "customDerivitives":[{"id":"%s","tags":["%s"]}]}
                """.formatted(custom, tag));
        return pack;
    }

    private record Captured(String fingerprint, GenerationEpoch.DimensionContract dimension, GenerationRegistryContract registry) {
    }

    private static final class Registry implements PlatformGenerationRegistry {
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
