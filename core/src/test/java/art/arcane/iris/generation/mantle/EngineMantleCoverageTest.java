package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.runtime.IrisEngineMantle;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EngineMantleCoverageTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        Map<String, NativeBlockState> states = new HashMap<>();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(invocation -> states.computeIfAbsent(
                invocation.getArgument(0),
                key -> {
                    NativeBlockState state = mock(NativeBlockState.class);
                    when(state.key()).thenReturn(key);
                    return state;
                }));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

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
        when(fixture.mantle().hasLoadedFlag(-2, 0, MantleFlag.REAL)).thenAnswer(invocation -> {
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

    @Test
    public void cleanupDefersUnloadedCoverageWithoutReadingFlags() {
        CoverageFixture fixture = fixture(1);
        fill(fixture, 2);
        when(fixture.mantle().isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        List<Chunk> actual = new ArrayList<>();

        fixture.engine().cleanupChunksCoveredBy(0, 0, true,
                (x, z) -> actual.add(new Chunk(x, z)));

        assertTrue(actual.isEmpty());
        verify(fixture.mantle(), never()).hasLoadedFlag(anyInt(), anyInt(), any());
        verify(fixture.mantle(), never()).getChunk(anyInt(), anyInt());

        when(fixture.mantle().isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        fixture.engine().cleanupChunksCoveredBy(0, 0, true,
                (x, z) -> actual.add(new Chunk(x, z)));
        assertEquals(9, actual.size());
    }

    @Test
    public void cleanupSkipsTargetEvictedAfterCoverageWithoutLoadingIt() {
        CoverageFixture fixture = fixture(0);
        fill(fixture, 0);
        doReturn(false).when(fixture.mantle()).withLoadedChunk(eq(0), eq(0), any());
        List<Chunk> actual = new ArrayList<>();

        fixture.engine().cleanupChunksCoveredBy(0, 0, true,
                (x, z) -> actual.add(new Chunk(x, z)));

        assertTrue(actual.isEmpty());
        assertTrue(fixture.cleaned().isEmpty());
        verify(fixture.mantle(), never()).getChunk(anyInt(), anyInt());
        verify(fixture.mantle(), never()).hasFlag(anyInt(), anyInt(), any());
    }

    @Test
    public void cleanupSkipsHaloEvictedAfterResidencyCheck() {
        CoverageFixture fixture = fixture(1);
        fill(fixture, 2);
        when(fixture.mantle().hasLoadedFlag(anyInt(), anyInt(), eq(MantleFlag.REAL))).thenReturn(false);
        List<Chunk> actual = new ArrayList<>();

        fixture.engine().cleanupChunksCoveredBy(0, 0, true,
                (x, z) -> actual.add(new Chunk(x, z)));

        assertTrue(actual.isEmpty());
        verify(fixture.mantle(), never()).withLoadedChunk(anyInt(), anyInt(), any());
        verify(fixture.mantle(), never()).getChunk(anyInt(), anyInt());
        verify(fixture.mantle(), never()).hasFlag(anyInt(), anyInt(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void busyTargetCanBeCleanedOnRetryAndCallbackRunsOutsideGuard() {
        CoverageFixture fixture = fixture(0);
        fill(fixture, 0);
        AtomicBoolean busy = new AtomicBoolean(true);
        AtomicBoolean guarded = new AtomicBoolean();
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(chunk.use()).thenReturn(chunk);
        doAnswer(invocation -> {
            if (busy.getAndSet(false)) {
                return false;
            }
            guarded.set(true);
            try {
                Predicate<MantleChunk<Matter>> action = invocation.getArgument(2);
                boolean cleaned = action.test(chunk);
                if (cleaned) {
                    fixture.cleaned().add(new Chunk(0, 0));
                }
                return cleaned;
            } finally {
                guarded.set(false);
            }
        }).when(fixture.mantle()).withLoadedChunk(eq(0), eq(0), any());
        List<Chunk> actual = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            fixture.engine().cleanupChunksCoveredBy(0, 0, true, (x, z) -> {
                assertTrue("Callback must run after releasing region guard", !guarded.get());
                actual.add(new Chunk(x, z));
            });
        }
        assertEquals(List.of(new Chunk(0, 0)), actual);
        verify(fixture.mantle(), never()).getChunk(anyInt(), anyInt());
        verify(fixture.mantle(), never()).hasFlag(anyInt(), anyInt(), any());
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
        when(mantle.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(mantle.hasLoadedFlag(anyInt(), anyInt(), any())).thenAnswer(invocation -> {
            Chunk position = new Chunk(invocation.getArgument(0), invocation.getArgument(1));
            MantleFlag flag = invocation.getArgument(2);
            if (flag == MantleFlag.REAL) {
                reads.incrementAndGet();
                return real.contains(position);
            }
            return cleaned.contains(position);
        });
        when(mantle.withLoadedChunk(anyInt(), anyInt(), any())).thenAnswer(invocation -> {
            Predicate<MantleChunk<Matter>> action = invocation.getArgument(2);
            boolean result = action.test(chunk);
            if (result) {
                cleaned.add(new Chunk(invocation.getArgument(0), invocation.getArgument(1)));
            }
            return result;
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
