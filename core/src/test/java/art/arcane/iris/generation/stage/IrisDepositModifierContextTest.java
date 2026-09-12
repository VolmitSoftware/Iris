package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.decoration.IrisDepositGenerator;
import art.arcane.iris.generation.decoration.IrisDepositHeightDistribution;
import art.arcane.iris.generation.decoration.IrisDepositPlacementScope;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.generation.concurrent.BurstExecutor;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.math.RNG;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisDepositModifierContextTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();

    @Test
    public void workerDepositsRetainRuntimeAndChunkContextUntilCompletion() throws Exception {
        IrisEngine engine = mock(IrisEngine.class, RETURNS_DEEP_STUBS);
        try (Fixture fixture = new Fixture(engine, true)) {
            fixture.generateAndCheckCleanup();
            verify(engine, times(1)).captureGenerationRuntimeBinding();
            verify(engine, times(4)).openGenerationRuntimeScope(fixture.binding);
            assertEquals(4, fixture.closedScopes.get());
        }
    }

    @Test
    public void unscopedDepositCallsDoNotInventARuntimeBinding() throws Exception {
        IrisEngine engine = mock(IrisEngine.class, RETURNS_DEEP_STUBS);
        try (Fixture fixture = new Fixture(engine, false)) {
            fixture.generateAndCheckCleanup();
            verify(engine, never()).captureGenerationRuntimeBinding();
            verify(engine, never()).openGenerationRuntimeScope(any());
        }
    }

    @Test
    public void nonIrisEngineWorkersStillReceiveTheirChunkContext() throws Exception {
        try (Fixture fixture = new Fixture(mock(Engine.class, RETURNS_DEEP_STUBS), false)) {
            fixture.generateAndCheckCleanup();
        }
    }

    @Test
    public void workerPreparationFailurePropagatesAfterAllScopesClose() throws Exception {
        try (Fixture fixture = new Fixture(mock(IrisEngine.class, RETURNS_DEEP_STUBS), true)) {
            IllegalStateException failure = new IllegalStateException("worker clump preparation failed");
            doThrow(failure).when(fixture.dimensionDeposit).getClump(any(), any(), any());
            assertSame(failure, assertThrows(IllegalStateException.class, () ->
                    fixture.modifier.modify(32, -48, Hunk.newArrayHunk(16, 16, 16), true, fixture.context)));
            verify(fixture.chunk).release();
            assertEquals(4, fixture.closedScopes.get());
            assertNull(IrisContext.get());
        }
    }

    @Test
    public void callerPlanFailureStillDrainsWorkersAndRestoresContext() throws Exception {
        try (Fixture fixture = new Fixture(mock(IrisEngine.class, RETURNS_DEEP_STUBS), true)) {
            IllegalStateException failure = new IllegalStateException("caller deposit plan failed");
            doThrow(failure).when(fixture.biomeDeposit).getSpawnChance();
            ChunkContext callerContext = mock(ChunkContext.class);
            try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 19L, callerContext)) {
                assertSame(failure, assertThrows(IllegalStateException.class, () ->
                        fixture.modifier.modify(32, -48, Hunk.newArrayHunk(16, 16, 16), true, fixture.context)));
                assertSame(callerContext, IrisContext.require().getChunkContext());
                assertEquals(19L, IrisContext.require().getGenerationSessionId());
            }
            verify(fixture.chunk).release();
            assertEquals(3, fixture.closedScopes.get());
            assertEquals(10, fixture.completed.get());
            assertNull(IrisContext.get());
        }
    }

    @Test
    public void callerFailureRetainsDistinctWorkerFailuresAfterDraining() throws Exception {
        try (Fixture fixture = new Fixture(mock(IrisEngine.class, RETURNS_DEEP_STUBS), true)) {
            IllegalStateException caller = new IllegalStateException("caller failure");
            IllegalArgumentException firstWorker = new IllegalArgumentException("first worker failure");
            UnsupportedOperationException secondWorker = new UnsupportedOperationException("second worker failure");
            doThrow(firstWorker, secondWorker).when(fixture.dimensionDeposit).getClump(any(), any(), any());
            doThrow(caller).when(fixture.biomeDeposit).getSpawnChance();
            assertSame(caller, assertThrows(IllegalStateException.class, () ->
                    fixture.modifier.modify(32, -48, Hunk.newArrayHunk(16, 16, 16), true, fixture.context)));
            assertEquals(2, caller.getSuppressed().length);
            assertSame(firstWorker, caller.getSuppressed()[0]);
            assertSame(secondWorker, caller.getSuppressed()[1]);
            verify(fixture.chunk).release();
            assertEquals(3, fixture.closedScopes.get());
            assertNull(IrisContext.get());
        }
    }

    @Test
    public void failedClumpChanceDoesNotResampleTheConfiguredAttemptCount() {
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        IrisDepositGenerator generator = mock(IrisDepositGenerator.class);
        when(generator.getSpawnChance()).thenReturn(1D);
        when(generator.getMinPerChunk()).thenReturn(0);
        when(generator.getMaxPerChunk()).thenReturn(4);
        when(generator.getPerClumpSpawnChance()).thenReturn(0D);
        RNG rng = mock(RNG.class);
        when(rng.d()).thenReturn(0.5D);
        when(rng.i(0, 5)).thenReturn(4, 0);

        new IrisDepositModifier(engine).generate(generator, null, null, rng, 0, 0, false, null);

        verify(generator, times(4)).getPerClumpSpawnChance();
        verify(rng, times(1)).i(0, 5);
        verify(generator, never()).getClump(any(), any(), any());
    }

    private static final class Fixture implements AutoCloseable {
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Engine engine;
        private final ChunkContext context = mock(ChunkContext.class, RETURNS_DEEP_STUBS);
        private final IrisEngine.GenerationRuntimeBinding binding = mock(IrisEngine.GenerationRuntimeBinding.class);
        private final ThreadLocal<Boolean> workerRuntime = ThreadLocal.withInitial(() -> false);
        private final AtomicInteger completed = new AtomicInteger();
        private final AtomicInteger closedScopes = new AtomicInteger();
        private final IrisDepositModifier modifier;
        private final IrisDepositGenerator dimensionDeposit = mock(IrisDepositGenerator.class);
        private final IrisDepositGenerator biomeDeposit = mock(IrisDepositGenerator.class);
        private final MantleChunk<Matter> chunk;

        @SuppressWarnings("unchecked")
        private Fixture(Engine engine, boolean scoped) {
            this.engine = engine;
            Thread caller = Thread.currentThread();
            when(context.getGenerationSessionId()).thenReturn(71L);
            MultiBurst pool = mock(MultiBurst.class);
            BurstExecutor burst = new BurstExecutor(executor, 3);
            burst.setMulticore(true);
            when(engine.burst()).thenReturn(pool);
            when(pool.burst(true)).thenReturn(burst);
            IrisDepositGenerator regionDeposit = mock(IrisDepositGenerator.class);
            when(engine.getDimension().getDeposits()).thenReturn(new KList<>(dimensionDeposit));
            when(context.getRegion().get(7, 7).getDeposits())
                    .thenReturn(new KList<>(regionDeposit));
            when(context.getBiome().get(7, 7).getDeposits())
                    .thenReturn(new KList<>(biomeDeposit));
            chunk = mock(MantleChunk.class);
            Mantle<Matter> mantle = engine.getMantle().getMantle();
            doReturn(chunk).when(mantle).getChunk(2, -3);
            when(chunk.use()).thenReturn(chunk);
            if (engine instanceof IrisEngine irisEngine) {
                when(irisEngine.hasGenerationRuntimeScope()).thenReturn(scoped);
                when(irisEngine.captureGenerationRuntimeBinding()).thenReturn(binding);
                IrisEngine.GenerationRuntimeScope scope = mock(IrisEngine.GenerationRuntimeScope.class);
                when(irisEngine.openGenerationRuntimeScope(binding)).thenAnswer(invocation -> {
                    assertNotSame(caller, Thread.currentThread());
                    workerRuntime.set(true);
                    return scope;
                });
                doAnswer(invocation -> {
                    workerRuntime.remove();
                    closedScopes.incrementAndGet();
                    return null;
                }).when(scope).close();
            }
            when(engine.getHeight()).thenReturn(16);
            IrisObject clump = new IrisObject(1, 1, 1);
            for (IrisDepositGenerator generator : new IrisDepositGenerator[]{dimensionDeposit, regionDeposit, biomeDeposit}) {
                when(generator.getSpawnChance()).thenReturn(1D);
                when(generator.getMinPerChunk()).thenReturn(1);
                when(generator.getMaxPerChunk()).thenReturn(1);
                when(generator.getPerClumpSpawnChance()).thenReturn(1D);
                when(generator.getPlacementScope()).thenReturn(IrisDepositPlacementScope.FULL_HEIGHT);
                when(generator.getHeightDistribution()).thenReturn(IrisDepositHeightDistribution.UNIFORM);
                when(generator.matchesBiome(any(), any())).thenReturn(true);
                doAnswer(invocation -> {
                    assertNotSame(caller, Thread.currentThread());
                    assertEquals(scoped, workerRuntime.get());
                    assertSame(engine, IrisContext.require().getEngine());
                    assertSame(context, IrisContext.require().getChunkContext());
                    assertEquals(71L, IrisContext.require().getGenerationSessionId());
                    completed.incrementAndGet();
                    return clump;
                }).when(generator).getClump(any(), any(), any());
            }
            when(dimensionDeposit.getMinPerChunk()).thenReturn(9);
            when(dimensionDeposit.getMaxPerChunk()).thenReturn(9);
            modifier = new IrisDepositModifier(engine);
        }

        private void generateAndCheckCleanup() throws Exception {
            ChunkContext callerContext = mock(ChunkContext.class);
            try (IrisContext.Scope ignored = IrisContext.open(engine, 19L, callerContext)) {
                modifier.generateDeposits(Hunk.<PlatformBlockState>newArrayHunk(16, 16, 16),
                        2, -3, true, context);
                assertSame(callerContext, IrisContext.require().getChunkContext());
                assertEquals(19L, IrisContext.require().getGenerationSessionId());
            }
            assertEquals(11, completed.get());
            verify(chunk).release();
            executor.submit(() -> {
                assertNull(IrisContext.get());
                assertFalse(workerRuntime.get());
            }).get(10L, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10L, TimeUnit.SECONDS));
        }
    }
}
