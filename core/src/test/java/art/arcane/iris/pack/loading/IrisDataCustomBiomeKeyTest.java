package art.arcane.iris.pack.loading;

import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.testsupport.DurabilityMode;
import art.arcane.iris.world.history.GenerationRegistryContractFactory;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class IrisDataCustomBiomeKeyTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void repeatedConcurrentLookupsMatchTheContentAddressedKeyPerDimension() throws Exception {
        Path pack = temporaryFolder.newFolder("custom-keys").toPath().resolve("iris/pack");
        writePack(pack);
        IrisData data = IrisData.openDatapackCompiler(pack.toFile());
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            IrisDimension overworld = data.getDimensionLoader().load("overworld");
            IrisDimension second = data.getDimensionLoader().load("second");
            List<IrisBiomeCustom> customs = data.getBiomeLoader().load("test").getCustomDerivitives();
            assertEquals(2, customs.size());
            List<String> expected = new ArrayList<>();
            for (IrisDimension dimension : List.of(overworld, second)) {
                for (IrisBiomeCustom custom : customs) {
                    expected.add(GenerationRegistryContractFactory.customBiomeResourceKey(
                            dimension.getLoadKey(), custom, data.getContentGate()));
                }
            }
            assertEquals(4, expected.stream().distinct().count());
            List<Future<List<String>>> results = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                results.add(pool.submit(() -> {
                    List<String> seen = new ArrayList<>();
                    for (int round = 0; round < 200; round++) {
                        seen.clear();
                        for (IrisDimension dimension : List.of(overworld, second)) {
                            for (IrisBiomeCustom custom : customs) {
                                seen.add(data.customBiomeResourceKey(dimension, custom));
                            }
                        }
                    }
                    return List.copyOf(seen);
                }));
            }
            for (Future<List<String>> result : results) {
                assertEquals(expected, result.get(60, TimeUnit.SECONDS));
            }
            IrisBiomeCustom equalCopy = new IrisBiomeCustom().setId(customs.getFirst().getId());
            assertEquals(GenerationRegistryContractFactory.customBiomeResourceKey(
                            overworld.getLoadKey(), equalCopy, data.getContentGate()),
                    data.customBiomeResourceKey(overworld, equalCopy));
            assertNotEquals(data.customBiomeResourceKey(overworld, customs.getFirst()),
                    data.customBiomeResourceKey(second, customs.getFirst()));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            data.close();
        }
    }

    private static void writePack(Path root) throws Exception {
        Files.createDirectories(root.resolve("dimensions"));
        Files.createDirectories(root.resolve("biomes"));
        for (String dimension : List.of("overworld", "second")) {
            Files.writeString(root.resolve("dimensions/" + dimension + ".json"), """
                    {
                      "name": "Test Dimension",
                      "environment": "NORMAL",
                      "logicalHeight": 256,
                      "dimensionHeight": {
                        "min": -64,
                        "max": 320
                      }
                    }
                    """, StandardCharsets.UTF_8);
        }
        Files.writeString(root.resolve("biomes/test.json"), """
                {
                  "name": "Test Biome",
                  "derivative": "minecraft:plains",
                  "vanillaDerivative": "minecraft:plains",
                  "customDerivitives": [
                    {
                      "id": "first_custom"
                    },
                    {
                      "id": "second_custom",
                      "temperature": 1.5
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
    }
}
