package art.arcane.iris.probe;

import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import org.junit.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class GenerationOrderProbeTest {
    @Test
    public void parsesExplicitBoundedOrderInputs() {
        GenerationOrderProbe.ProbeConfiguration configuration = GenerationOrderProbe.ProbeConfiguration.parse(
                new String[]{
                        "/tmp/pack",
                        "underworld",
                        "1337",
                        "-2",
                        "2",
                        "-1",
                        "1",
                        "4",
                        "991",
                        "true",
                        "false"
                });

        assertEquals(new File("/tmp/pack"), configuration.packSource());
        assertEquals("underworld", configuration.dimensionKey());
        assertEquals(1337L, configuration.seed());
        assertEquals(15, configuration.chunkCount());
        assertEquals(4, configuration.parallelism());
        assertTrue(configuration.multicore());
        assertTrue(!configuration.studio());
    }

    @Test
    public void rejectsImplicitUnboundedOrInvalidOrderInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> GenerationOrderProbe.ProbeConfiguration.parse(new String[]{"/tmp/pack"}));
        assertThrows(IllegalArgumentException.class,
                () -> GenerationOrderProbe.ProbeConfiguration.parse(new String[]{
                        "/tmp/pack", "overworld", "1", "0", "40", "0", "40", "4", "2", "false", "false"
                }));
        assertThrows(IllegalArgumentException.class,
                () -> GenerationOrderProbe.ProbeConfiguration.parse(new String[]{
                        "/tmp/pack", "overworld", "1", "0", "0", "0", "0", "1", "2", "false", "false"
                }));
        assertThrows(IllegalArgumentException.class,
                () -> GenerationOrderProbe.ProbeConfiguration.parse(new String[]{
                        "/tmp/pack", "overworld", "1", "0", "0", "0", "0", "2", "2", "maybe", "false"
                }));
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationOrderProbe.ProbeConfiguration(
                        new File("/tmp/pack"), "overworld", 1L,
                        134_217_728, 134_217_728, 0, 0, 2, 2L, false, false));
    }

    @Test
    public void createsForwardReverseAndDeterministicShuffleSchedules() {
        GenerationOrderProbe.ProbeConfiguration configuration = new GenerationOrderProbe.ProbeConfiguration(
                new File("/tmp/pack"), "overworld", 1L, -1, 1, 0, 1, 2, 91L, false, false);
        List<GenerationOrderProbe.ChunkCoordinate> forward = GenerationOrderProbe.coordinates(configuration);
        List<GenerationOrderProbe.ChunkCoordinate> reverse = GenerationOrderProbe.orderedCoordinates(
                forward, GenerationOrderProbe.GenerationOrder.REVERSE, 91L);
        List<GenerationOrderProbe.ChunkCoordinate> shuffled = GenerationOrderProbe.orderedCoordinates(
                forward, GenerationOrderProbe.GenerationOrder.SHUFFLED, 91L);
        List<GenerationOrderProbe.ChunkCoordinate> parallel = GenerationOrderProbe.orderedCoordinates(
                forward, GenerationOrderProbe.GenerationOrder.BOUNDED_PARALLEL, 91L);

        assertEquals(new GenerationOrderProbe.ChunkCoordinate(-1, 0), forward.getFirst());
        assertEquals(new GenerationOrderProbe.ChunkCoordinate(1, 1), reverse.getFirst());
        assertEquals(shuffled, parallel);
        assertEquals(shuffled, GenerationOrderProbe.orderedCoordinates(
                forward, GenerationOrderProbe.GenerationOrder.SHUFFLED, 91L));
    }

    @Test
    public void aggregateSignatureIgnoresMapInsertionOrderButNotChunkContent() {
        GenerationOrderProbe.ChunkCoordinate first = new GenerationOrderProbe.ChunkCoordinate(0, 0);
        GenerationOrderProbe.ChunkCoordinate second = new GenerationOrderProbe.ChunkCoordinate(1, 0);
        GenerationOrderProbe.ChunkHash firstHash = new GenerationOrderProbe.ChunkHash("a", "b", "c");
        GenerationOrderProbe.ChunkHash secondHash = new GenerationOrderProbe.ChunkHash("d", "e", "f");
        LinkedHashMap<GenerationOrderProbe.ChunkCoordinate, GenerationOrderProbe.ChunkHash> forward =
                new LinkedHashMap<>();
        forward.put(first, firstHash);
        forward.put(second, secondHash);
        LinkedHashMap<GenerationOrderProbe.ChunkCoordinate, GenerationOrderProbe.ChunkHash> reverse =
                new LinkedHashMap<>();
        reverse.put(second, secondHash);
        reverse.put(first, firstHash);

        assertEquals(
                GenerationOrderProbe.aggregateSignature(forward),
                GenerationOrderProbe.aggregateSignature(reverse)
        );
        assertEquals(new GenerationOrderProbe.AggregateSignature(
                "f5a500c97351e576fb335317c0a716c48c320b1304a1f4dc9658f1634b80de98",
                "1bf3a08fecb828a69b7c504459d2a53b7d089aa9b16040000bb5e06f9e1e2b5f",
                "d667a0482bb425cee8f7384284472ce915cfdd6a1b9a123fa8a58ee26b67f9f9"),
                GenerationOrderProbe.aggregateSignature(forward));
        reverse.put(first, new GenerationOrderProbe.ChunkHash("changed", "b", "changed"));
        assertNotEquals(
                GenerationOrderProbe.aggregateSignature(forward),
                GenerationOrderProbe.aggregateSignature(reverse)
        );
    }

    @Test
    public void completeChunkHashSeparatesBlockAndBiomeChanges() {
        StubPlatform platform = new StubPlatform(new File("/tmp/iris-order-probe-test"));
        PlatformBlockState stone = StubPlatform.blockStateForTest("minecraft:stone");
        PlatformBlockState water = StubPlatform.blockStateForTest("minecraft:water[level=0]");
        PlatformBiome plains = platform.registries().biome("minecraft:plains");
        PlatformBiome forest = platform.registries().biome("minecraft:forest");
        Hunk<PlatformBlockState> baselineBlocks = Hunk.newArrayHunk(16, 2, 16);
        Hunk<PlatformBiome> baselineBiomes = Hunk.newArrayHunk(16, 2, 16);
        baselineBlocks.set(0, 0, 0, stone);
        baselineBiomes.set(0, 0, 0, plains);
        GenerationOrderProbe.ChunkCoordinate coordinate = new GenerationOrderProbe.ChunkCoordinate(0, 0);
        GenerationOrderProbe.ChunkHash baseline = GenerationOrderProbe.hashChunk(
                coordinate, baselineBlocks, baselineBiomes, 2);

        Hunk<PlatformBiome> changedBiomes = Hunk.newArrayHunk(16, 2, 16);
        changedBiomes.set(0, 0, 0, forest);
        GenerationOrderProbe.ChunkHash biomeChange = GenerationOrderProbe.hashChunk(
                coordinate, baselineBlocks, changedBiomes, 2);
        assertEquals(baseline.blocks(), biomeChange.blocks());
        assertNotEquals(baseline.biomes(), biomeChange.biomes());
        assertNotEquals(baseline.combined(), biomeChange.combined());

        Hunk<PlatformBlockState> changedBlocks = Hunk.newArrayHunk(16, 2, 16);
        changedBlocks.set(0, 0, 0, water);
        GenerationOrderProbe.ChunkHash blockChange = GenerationOrderProbe.hashChunk(
                coordinate, changedBlocks, baselineBiomes, 2);
        assertNotEquals(baseline.blocks(), blockChange.blocks());
        assertEquals(baseline.biomes(), blockChange.biomes());
        assertNotEquals(baseline.combined(), blockChange.combined());
        assertEquals(
                Map.of("minecraft:stone", -1, "minecraft:water[level=0]", 1),
                GenerationOrderProbe.blockCountDelta(baseline, blockChange)
        );
        assertEquals(
                Map.of("0:minecraft:stone", -1, "0:minecraft:water[level=0]", 1),
                GenerationOrderProbe.blockYCountDelta(baseline, blockChange)
        );
    }

    @Test
    public void mismatchDetectionIncludesMissingAndChangedChunks() {
        GenerationOrderProbe.ChunkCoordinate first = new GenerationOrderProbe.ChunkCoordinate(0, 0);
        GenerationOrderProbe.ChunkCoordinate second = new GenerationOrderProbe.ChunkCoordinate(1, 0);
        GenerationOrderProbe.ChunkHash firstHash = new GenerationOrderProbe.ChunkHash("a", "b", "c");
        GenerationOrderProbe.ChunkHash secondHash = new GenerationOrderProbe.ChunkHash("d", "e", "f");
        Map<GenerationOrderProbe.ChunkCoordinate, GenerationOrderProbe.ChunkHash> expected = Map.of(
                first, firstHash,
                second, secondHash
        );
        Map<GenerationOrderProbe.ChunkCoordinate, GenerationOrderProbe.ChunkHash> observed = Map.of(
                first, new GenerationOrderProbe.ChunkHash("changed", "b", "changed")
        );

        assertEquals(List.of(first, second), GenerationOrderProbe.mismatchedChunks(expected, observed));
    }
}
