package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.WorldgenDatapackExporter;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.WorldgenExportResult;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WorldgenExportSamplerThreadsTest {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    private static final long SEED = 1337L;
    private static final String DIMENSION_ID = WorldgenExportRequest.DEFAULT_NAMESPACE + ":" + ExportTestPacks.DIMENSION;
    private static final String FINAL_DENSITY_ID = DIMENSION_ID + "/final_density";
    private static final int THREADS = 8;
    private static final int POINTS = 2048;
    private static final int ROW = 64;
    private static final int STEP = 37;
    private static final int ROUNDS = 3;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void concurrentSamplingMatchesSequentialSampling() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        WorldgenExportRequest request = WorldgenExportRequest.builder()
                .pack(pack.toPath())
                .dimension(ExportTestPacks.DIMENSION)
                .exportRoot(temporaryFolder.newFolder("exports").toPath())
                .format("directory")
                .build();
        WorldgenExportResult result = WorldgenDatapackExporter.create().export(request, WorldgenExportProgress.NONE);
        assertTrue(result.message() + " " + result.diagnostics(), result.isSuccess());
        try (WorldgenExportSampler sampler = WorldgenExportSampler.open(result.output(), WorldgenExportMode.OVERWORLD, DIMENSION_ID, SEED)) {
            assertTrue(sampler.densityFunctionIds().toString(), sampler.densityFunctionIds().contains(FINAL_DENSITY_ID));
            Sample expected = sweep(sampler, 0, 1).call();
            ExecutorService executor = Executors.newFixedThreadPool(THREADS);
            try {
                List<Future<Sample>> futures = new ArrayList<>(THREADS);
                for (int thread = 0; thread < THREADS; thread++) {
                    futures.add(executor.submit(sweep(sampler, thread * (POINTS / THREADS), ROUNDS)));
                }
                for (Future<Sample> future : futures) {
                    Sample sample = future.get();
                    assertArrayEquals(expected.density(), sample.density(), 0.0F);
                    assertEquals(List.of(expected.biome()), List.of(sample.biome()));
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static Callable<Sample> sweep(WorldgenExportSampler sampler, int offset, int rounds) {
        return () -> {
            float[] density = new float[POINTS];
            String[] biome = new String[POINTS];
            for (int round = 0; round < rounds; round++) {
                for (int step = 0; step < POINTS; step++) {
                    int index = (offset + step) % POINTS;
                    int x = (index % ROW - ROW / 2) * STEP;
                    int y = (index * 7) % 256 - 64;
                    int z = (index / ROW - POINTS / ROW / 2) * STEP;
                    density[index] = sampler.density(FINAL_DENSITY_ID, x, y, z);
                    biome[index] = sampler.biome(x, y, z);
                }
            }
            return new Sample(density, biome);
        };
    }

    private record Sample(float[] density, String[] biome) {
    }
}
