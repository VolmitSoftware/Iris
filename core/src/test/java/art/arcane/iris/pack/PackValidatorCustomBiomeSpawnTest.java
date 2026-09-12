package art.arcane.iris.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Enclosed.class)
public class PackValidatorCustomBiomeSpawnTest {
    private static final Function<String, PackSpawnValidator.SpawnCategoryResolution> MONSTER =
            key -> PackSpawnValidator.SpawnCategoryResolution.known("monster");
    private static final Function<String, PackSpawnValidator.SpawnCategoryResolution> MISC =
            key -> PackSpawnValidator.SpawnCategoryResolution.known("misc");
    private static final Function<String, PackSpawnValidator.SpawnCategoryResolution> AXOLOTLS =
            key -> PackSpawnValidator.SpawnCategoryResolution.known("axolotls");
    private static final Function<String, PackSpawnValidator.SpawnCategoryResolution> UNKNOWN =
            key -> PackSpawnValidator.SpawnCategoryResolution.unknown();

    private static File createBiomes(TemporaryFolder temporaryFolder, String json) throws Exception {
        File biomes = temporaryFolder.newFolder("biomes");
        File biome = new File(biomes, "test.json");
        Files.writeString(biome.toPath(), json, StandardCharsets.UTF_8);
        return biomes;
    }

    @RunWith(Parameterized.class)
    public static class Accepted {
        @Parameters(name = "{0}")
        public static Collection<Object[]> biomes() {
            return List.of(
                    new Object[]{"explicitMatchingSpawnGroup",
                            "{\"customDerivitives\":[{\"id\":\"swamp\",\"spawns\":[{\"type\":\"minecraft:slime\",\"group\":\"MONSTER\"}]}]}",
                            MONSTER},
                    new Object[]{"biomeWithoutCustomSpawns",
                            "{\"name\":\"Plains\"}",
                            MONSTER},
                    new Object[]{"implicitMiscSpawnGroup",
                            "{\"customDerivitives\":[{\"id\":\"effects\",\"spawns\":[{\"type\":\"minecraft:armor_stand\"}]}]}",
                            MISC},
                    new Object[]{"axolotlSpawnCategory",
                            "{\"customDerivitives\":[{\"id\":\"cave\",\"spawns\":[{\"type\":\"minecraft:axolotl\",\"group\":\"AXOLOTLS\"}]}]}",
                            AXOLOTLS},
                    new Object[]{"nullCustomDerivativeContainerAsAbsent",
                            "{\"customDerivitives\":null}",
                            null},
                    new Object[]{"nullSpawnContainerAsAbsent",
                            "{\"customDerivitives\":[{\"id\":\"swamp\",\"spawns\":null}]}",
                            null},
                    new Object[]{"namespacedCustomBiomeTags",
                            "{\"customDerivitives\":[{\"id\":\"swamp\",\"tags\":[\"minecraft:allows_surface_slime_spawns\"]}]}",
                            null}
            );
        }

        @Rule
        public TemporaryFolder temporaryFolder = new TemporaryFolder();

        private final String scenario;
        private final String json;
        private final Function<String, PackSpawnValidator.SpawnCategoryResolution> categoryResolver;

        public Accepted(
                String scenario,
                String json,
                Function<String, PackSpawnValidator.SpawnCategoryResolution> categoryResolver
        ) {
            this.scenario = scenario;
            this.json = json;
            this.categoryResolver = categoryResolver;
        }

        @Test
        public void reportsNoSpawnErrors() throws Exception {
            File biomes = createBiomes(temporaryFolder, json);

            List<String> errors = PackSpawnValidator.validateCustomBiomeSpawns(biomes, categoryResolver);

            assertTrue(scenario + " " + errors, errors.isEmpty());
        }
    }

    @RunWith(Parameterized.class)
    public static class Rejected {
        @Parameters(name = "{0}")
        public static Collection<Object[]> biomes() {
            return List.of(
                    new Object[]{"missingSpawnGroup",
                            "{\"customDerivitives\":[{\"id\":\"swamp\",\"spawns\":[{\"type\":\"minecraft:slime\"}]}]}",
                            MONSTER, "must declare group 'MONSTER'"},
                    new Object[]{"spawnGroupThatDisagreesWithRegistry",
                            "{\"customDerivitives\":[{\"id\":\"swamp\",\"spawns\":[{\"type\":\"minecraft:slime\",\"group\":\"MISC\"}]}]}",
                            MONSTER, "live entity registry requires 'MONSTER'"},
                    new Object[]{"unknownSpawnEntity",
                            "{\"customDerivitives\":[{\"id\":\"swamp\",\"spawns\":[{\"type\":\"missing:entity\",\"group\":\"MISC\"}]}]}",
                            UNKNOWN, "unknown entity type 'missing:entity'"},
                    new Object[]{"caseNormalizedGroupThatRuntimeWouldNotParse",
                            "{\"customDerivitives\":[{\"id\":\"swamp\",\"spawns\":[{\"type\":\"minecraft:slime\",\"group\":\"monster\"}]}]}",
                            MONSTER, "unknown group 'monster'"},
                    new Object[]{"wrongCustomDerivativeContainerType",
                            "{\"customDerivitives\":{}}",
                            null, "customDerivitives must be an array"},
                    new Object[]{"wrongSpawnContainerType",
                            "{\"customDerivitives\":[{\"id\":\"swamp\",\"spawns\":{}}]}",
                            null, "spawns must be an array"},
                    new Object[]{"unsafeCustomBiomeTags",
                            "{\"customDerivitives\":[{\"id\":\"swamp\",\"tags\":[\"minecraft:../outside\"]}]}",
                            null, "invalid tag"}
            );
        }

        @Rule
        public TemporaryFolder temporaryFolder = new TemporaryFolder();

        private final String scenario;
        private final String json;
        private final Function<String, PackSpawnValidator.SpawnCategoryResolution> categoryResolver;
        private final String expectedError;

        public Rejected(
                String scenario,
                String json,
                Function<String, PackSpawnValidator.SpawnCategoryResolution> categoryResolver,
                String expectedError
        ) {
            this.scenario = scenario;
            this.json = json;
            this.categoryResolver = categoryResolver;
            this.expectedError = expectedError;
        }

        @Test
        public void reportsExactlyOneSpawnError() throws Exception {
            File biomes = createBiomes(temporaryFolder, json);

            List<String> errors = PackSpawnValidator.validateCustomBiomeSpawns(biomes, categoryResolver);

            assertEquals(scenario + " " + errors, 1, errors.size());
            assertTrue(errors.get(0), errors.get(0).contains(expectedError));
        }
    }

    public static class ResolverFailure {
        @Rule
        public TemporaryFolder temporaryFolder = new TemporaryFolder();

        @Test
        public void convertsSpawnCategoryResolverFailureIntoBlockingError() throws Exception {
            File biomes = createBiomes(temporaryFolder,
                    "{\"customDerivitives\":[{\"id\":\"swamp\",\"spawns\":[{\"type\":\"minecraft:slime\",\"group\":\"MONSTER\"}]}]}");

            List<String> errors = PackSpawnValidator.validateCustomBiomeSpawns(biomes, key -> {
                throw new IllegalStateException("registry unavailable");
            });

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).contains("spawn category lookup failed"));
            assertTrue(errors.get(0).contains("registry unavailable"));
        }
    }
}
