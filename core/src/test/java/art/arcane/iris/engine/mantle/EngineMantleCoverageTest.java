package art.arcane.iris.engine.mantle;

import art.arcane.iris.engine.IrisEngineMantle;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EngineMantleCoverageTest {
    @Test
    public void zeroRadiusCleansOnlyItsRealChunkOnce() {
        CoverageFixture fixture = fixture(0);
        List<Chunk> actual = new ArrayList<>();

        fixture.engine().cleanupChunksCoveredBy(0, 0, true,
                (x, z) -> actual.add(new Chunk(x, z)));
        assertTrue(actual.isEmpty());
        fixture.real().add(new Chunk(0, 0));
        fixture.engine().cleanupChunksCoveredBy(0, 0, true,
                (x, z) -> actual.add(new Chunk(x, z)));
        fixture.engine().cleanupChunksCoveredBy(0, 0, true,
                (x, z) -> actual.add(new Chunk(x, z)));

        assertEquals(List.of(new Chunk(0, 0)), actual);
    }

    @Test
    public void realRadiusDefinesTheLargerCoverageHalo() {
        CoverageFixture fixture = fixture(0);
        doReturn(2).when(fixture.engine()).getRealRadius();
        fill(fixture, 2);
        List<Chunk> actual = new ArrayList<>();

        fixture.engine().cleanupChunksCoveredBy(0, 0, false,
                (x, z) -> actual.add(new Chunk(x, z)));

        assertEquals(List.of(new Chunk(0, 0)), actual);
    }

    @Test
    public void cleanupMatchesFullScanAcrossCoverageShapes() {
        for (int radius : new int[]{0, 1, 2, 4}) {
            for (int shape = 0; shape < 4; shape++) {
                CoverageFixture fixture = fixture(radius);
                for (int x = -2 * radius; x <= 2 * radius; x++) {
                    for (int z = -2 * radius; z <= 2 * radius; z++) {
                        if (shape == 0 || shape == 1 && x <= 0 || shape == 2 && z <= x) {
                            fixture.real().add(new Chunk(x - 7, z - 4));
                        }
                    }
                }
                fixture.cleaned().add(new Chunk(-7, -4));
                List<Chunk> expected = fullScan(fixture, -7, -4, radius);
                List<Chunk> actual = new ArrayList<>();

                fixture.engine().cleanupChunksCoveredBy(-7, -4, false,
                        (x, z) -> actual.add(new Chunk(x, z)));

                assertEquals("radius=" + radius + ", shape=" + shape, expected, actual);
            }
        }
    }

    @Test
    public void cleanupRechecksMissingNeighborThatFinishesGeneration() {
        CoverageFixture fixture = fixture(1);
        fill(fixture, 2);
        Chunk neighbor = new Chunk(-2, 0);
        fixture.real().remove(neighbor);
        AtomicInteger reads = new AtomicInteger();
        when(fixture.mantle().hasFlag(-2, 0, MantleFlag.REAL)).thenAnswer(invocation -> {
            if (reads.incrementAndGet() > 1) {
                fixture.real().add(neighbor);
                return true;
            }
            return false;
        });
        List<Chunk> actual = new ArrayList<>();

        fixture.engine().cleanupChunksCoveredBy(0, 0, false,
                (x, z) -> actual.add(new Chunk(x, z)));

        assertEquals(List.of(new Chunk(-1, 0), new Chunk(-1, 1),
                new Chunk(0, -1), new Chunk(0, 0), new Chunk(0, 1),
                new Chunk(1, -1), new Chunk(1, 0), new Chunk(1, 1)), actual);
    }

    @Test
    public void cleanupCallbackCanInvalidateLaterCoverage() {
        CoverageFixture fixture = fixture(1);
        fill(fixture, 2);
        List<Chunk> actual = new ArrayList<>();

        fixture.engine().cleanupChunksCoveredBy(0, 0, true, (x, z) -> {
            actual.add(new Chunk(x, z));
            fixture.real().remove(new Chunk(0, 0));
        });

        assertEquals(List.of(new Chunk(-1, -1)), actual);
    }

    @Test
    public void neighboringFrontierTargetsReuseARecheckedMissingNeighbor() {
        CoverageFixture fixture = fixture(4);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 0; z++) {
                fixture.real().add(new Chunk(x, z));
            }
        }
        List<Chunk> expected = fullScan(fixture, 0, 0, 4);
        List<Chunk> actual = new ArrayList<>();
        int baselineReads = fullScanReads(fixture, 4);

        fixture.engine().cleanupChunksCoveredBy(0, 0, false,
                (x, z) -> actual.add(new Chunk(x, z)));

        assertEquals(expected, actual);
        assertTrue("baseline=" + baselineReads + ", actual=" + fixture.reads().get(),
                fixture.reads().get() < baselineReads * 4 / 5);
    }

    private static void fill(CoverageFixture fixture, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                fixture.real().add(new Chunk(x, z));
            }
        }
    }

    private static List<Chunk> fullScan(CoverageFixture fixture, int x, int z, int radius) {
        List<Chunk> result = new ArrayList<>();
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                Chunk target = new Chunk(x + offsetX, z + offsetZ);
                if (fixture.cleaned().contains(target)) {
                    continue;
                }
                boolean covered = true;
                for (int dx = -radius; dx <= radius && covered; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (!fixture.real().contains(new Chunk(target.x() + dx, target.z() + dz))) {
                            covered = false;
                            break;
                        }
                    }
                }
                if (covered) {
                    result.add(target);
                }
            }
        }
        return result;
    }

    private static int fullScanReads(CoverageFixture fixture, int radius) {
        int reads = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                boolean covered = true;
                for (int dx = -radius; dx <= radius && covered; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        reads++;
                        if (!fixture.real().contains(new Chunk(x + dx, z + dz))) {
                            covered = false;
                            break;
                        }
                    }
                }
            }
        }
        return reads;
    }

    @SuppressWarnings("unchecked")
    private static CoverageFixture fixture(int radius) {
        EngineMantle engine = mock(IrisEngineMantle.class, CALLS_REAL_METHODS);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        Set<Chunk> real = new HashSet<>();
        Set<Chunk> cleaned = new HashSet<>();
        AtomicInteger reads = new AtomicInteger();
        doReturn(mantle).when(engine).getMantle();
        doReturn(radius).when(engine).getRadius();
        doReturn(0).when(engine).getRealRadius();
        when(mantle.hasFlag(anyInt(), anyInt(), any())).thenAnswer(invocation -> {
            Chunk position = new Chunk(invocation.getArgument(0), invocation.getArgument(1));
            MantleFlag flag = invocation.getArgument(2);
            if (flag == MantleFlag.REAL) {
                reads.incrementAndGet();
                return real.contains(position);
            }
            return cleaned.contains(position);
        });
        when(mantle.getChunk(anyInt(), anyInt())).thenAnswer(invocation -> {
            cleaned.add(new Chunk(invocation.getArgument(0), invocation.getArgument(1)));
            return chunk;
        });
        when(chunk.use()).thenReturn(chunk);
        doAnswer(invocation -> null).when(chunk).raiseFlagUnchecked(eq(MantleFlag.CLEANED), any());
        return new CoverageFixture(engine, mantle, real, cleaned, reads);
    }

    private record Chunk(int x, int z) {
    }

    private record CoverageFixture(EngineMantle engine, Mantle<Matter> mantle,
                                   Set<Chunk> real, Set<Chunk> cleaned, AtomicInteger reads) {
    }
}
