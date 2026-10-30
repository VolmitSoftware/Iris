package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportIrFixtures;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.emit.WorldgenJsonWriter;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.SortedMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WorldgenStageBenchTest {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    private static final long SEED = 1337L;
    private static final int SIDE = 2;
    private static final WorldgenStageBench.CallBudget BUDGET = new WorldgenStageBench.CallBudget(32, 8, 0L);

    private static HeadlessNativeRegistries loaded;

    @BeforeClass
    public static void loadFixture() throws IOException {
        HeadlessNativeBootstrap.initialize();
        SortedMap<String, byte[]> files = WorldgenJsonWriter.forTarget(DataVersion.V26_3)
                .write(ExportIrFixtures.flatNoise(DataVersion.V26_3, WorldgenExportMode.PRESET));
        Path directory = Files.createTempDirectory("iris-stage-bench-");
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            Path target = directory.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, file.getValue());
        }
        try (PaperRegistryScope scope = PaperRegistryScope.open()) {
            loaded = HeadlessNativeRegistries.load(directory);
        }
    }

    @AfterClass
    public static void closeFixture() throws IOException {
        if (loaded != null) {
            loaded.close();
        }
    }

    @Test
    public void exportedFixtureRunsEveryStage() {
        assertStages(bench("export", ExportIrFixtures.NAMESPACE + ":" + ExportIrFixtures.DIMENSION));
    }

    @Test
    public void vanillaOverworldRunsEveryStage() {
        assertStages(bench("vanilla", DatapackCostProbe.VANILLA_PRESET));
    }

    private static void assertStages(WorldgenStageBench bench) {
        WorldgenStageBench.ChunkSamples samples = new WorldgenStageBench.ChunkSamples();
        bench.chunks(0, 0, SIDE, samples);
        bench.chunks(SIDE + 4, 0, SIDE, samples);
        WorldgenStageBench.ChunkTimings timings = samples.timings();
        assertEquals(2 * SIDE * SIDE, timings.chunks());
        assertEquals(2 * (SIDE + 2) * (SIDE + 2), timings.biomes().wall().count());
        assertEquals(2 * SIDE * SIDE, timings.noise().cpu().count());
        assertEquals(2 * SIDE * SIDE, timings.surface().wall().count());
        assertEquals(2 * SIDE * SIDE, timings.carverBiome().wall().count());
        assertEquals(0L, timings.biomeFallbacks());
        assertTrue(Double.isFinite(timings.totalMean()));
        assertTrue(Double.isFinite(timings.totalCpuMean()));
        assertCalls(samples(bench, Call.UNCACHED_BIOME));
        assertCalls(samples(bench, Call.BASE_HEIGHT));
        assertCalls(samples(bench, Call.BASE_COLUMN));
    }

    private static void assertCalls(WorldgenStageBench.CallTiming timing) {
        assertEquals(2 * BUDGET.minimumCalls(), timing.calls());
        assertEquals(2 * BUDGET.minimumCalls(), timing.wall().count());
        assertTrue(Double.isFinite(timing.cpuMean()));
    }

    private static WorldgenStageBench.CallTiming samples(WorldgenStageBench bench, Call call) {
        WorldgenStageBench.CallSamples samples = new WorldgenStageBench.CallSamples();
        for (int round = 0; round < 2; round++) {
            switch (call) {
                case UNCACHED_BIOME -> bench.uncachedBiome(BUDGET, samples);
                case BASE_HEIGHT -> bench.baseHeight(BUDGET, Heightmap.Types.WORLD_SURFACE_WG, samples);
                case BASE_COLUMN -> bench.baseColumn(BUDGET, samples);
            }
        }
        return samples.timing();
    }

    private static WorldgenStageBench bench(String label, String preset) {
        return new WorldgenStageBench(new WorldgenStageBench.Options(label,
                DatapackCostProbe.presetOverworld(loaded.registries(), preset), loaded.registries(), SEED));
    }

    private enum Call {
        UNCACHED_BIOME,
        BASE_HEIGHT,
        BASE_COLUMN
    }
}
