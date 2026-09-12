package art.arcane.iris.generation.context;

import art.arcane.iris.generation.runtime.Engine;
import org.junit.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisContextIsolationTest {
    @Test
    public void scopedContextsIsolateStatePerThread() throws Exception {
        Engine engine = mock(Engine.class);
        ChunkContext leftChunk = mock(ChunkContext.class);
        ChunkContext rightChunk = mock(ChunkContext.class);
        CyclicBarrier writesComplete = new CyclicBarrier(2);
        AtomicReference<IrisContext> leftBound = new AtomicReference<>();
        AtomicReference<IrisContext> rightBound = new AtomicReference<>();
        AtomicReference<ChunkContext> leftObserved = new AtomicReference<>();
        AtomicReference<ChunkContext> rightObserved = new AtomicReference<>();
        AtomicReference<IrisContext> leftAfterClose = new AtomicReference<>();
        AtomicReference<IrisContext> rightAfterClose = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread leftThread = new Thread(() -> exerciseContext(engine, 17L, leftChunk, writesComplete, leftBound, leftObserved, leftAfterClose, failure), "iris-context-left");
        Thread rightThread = new Thread(() -> exerciseContext(engine, 17L, rightChunk, writesComplete, rightBound, rightObserved, rightAfterClose, failure), "iris-context-right");
        leftThread.start();
        rightThread.start();
        leftThread.join(5000L);
        rightThread.join(5000L);

        assertFalse("Left context worker did not finish.", leftThread.isAlive());
        assertFalse("Right context worker did not finish.", rightThread.isAlive());
        assertNull(failure.get());
        assertNotSame(leftBound.get(), rightBound.get());
        assertSame(leftChunk, leftObserved.get());
        assertSame(rightChunk, rightObserved.get());
        assertNull(leftAfterClose.get());
        assertNull(rightAfterClose.get());
    }

    @Test
    public void scopeRebindsWorkerWhenEngineChanges() throws Exception {
        Engine firstEngine = mock(Engine.class);
        Engine secondEngine = mock(Engine.class);
        AtomicReference<IrisContext> first = new AtomicReference<>();
        AtomicReference<IrisContext> second = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try (IrisContext.Scope firstScope = IrisContext.open(firstEngine, 1L, null)) {
                first.set(IrisContext.require());
            }
            try (IrisContext.Scope secondScope = IrisContext.open(secondEngine, 2L, null)) {
                second.set(IrisContext.require());
            }
        }, "iris-context-reused-worker");
        worker.start();
        worker.join(5000L);

        assertFalse("Reused context worker did not finish.", worker.isAlive());
        assertSame(firstEngine, first.get().getEngine());
        assertSame(secondEngine, second.get().getEngine());
        assertNotSame(first.get(), second.get());
    }

    @Test
    public void nestedScopeRestoresOuterContext() {
        Engine outerEngine = mock(Engine.class);
        Engine innerEngine = mock(Engine.class);
        ChunkContext outerChunk = mock(ChunkContext.class);
        ChunkContext innerChunk = mock(ChunkContext.class);

        try (IrisContext.Scope outerScope = IrisContext.open(outerEngine, 11L, outerChunk)) {
            IrisContext outer = IrisContext.require();
            try (IrisContext.Scope innerScope = IrisContext.open(innerEngine, 12L, innerChunk)) {
                IrisContext inner = IrisContext.require();
                assertSame(innerEngine, inner.getEngine());
                assertSame(innerChunk, inner.getChunkContext());
            }
            assertSame(outer, IrisContext.require());
        }

        assertNull(IrisContext.get());
    }

    @Test
    public void nestedScopeRestoresOuterContextAfterEngineCloses() {
        Engine engine = mock(Engine.class);
        when(engine.isClosed()).thenReturn(false);

        try (IrisContext.Scope outerScope = IrisContext.open(engine, 11L, null)) {
            IrisContext outer = IrisContext.require();
            when(engine.isClosed()).thenReturn(true);
            assertTrue(engine.isClosed());
            try (IrisContext.Scope innerScope = IrisContext.open(engine, 11L, null)) {
                assertNotSame(outer, IrisContext.require());
            }
            assertSame(outer, IrisContext.require());
        }

        assertNull(IrisContext.get());
    }

    @Test
    public void closingScopesOutOfOrderFails() {
        Engine outerEngine = mock(Engine.class);
        Engine innerEngine = mock(Engine.class);
        IrisContext.Scope outerScope = IrisContext.open(outerEngine, 21L, null);
        IrisContext.Scope innerScope = IrisContext.open(innerEngine, 22L, null);

        assertThrows(IllegalStateException.class, outerScope::close);
        innerScope.close();
        outerScope.close();
        assertNull(IrisContext.get());
    }

    private void exerciseContext(
            Engine engine,
            long generationSessionId,
            ChunkContext chunk,
            CyclicBarrier writesComplete,
            AtomicReference<IrisContext> bound,
            AtomicReference<ChunkContext> observed,
            AtomicReference<IrisContext> afterClose,
            AtomicReference<Throwable> failure
    ) {
        try {
            try (IrisContext.Scope scope = IrisContext.open(engine, generationSessionId, chunk)) {
                bound.set(IrisContext.require());
                await(writesComplete);
                observed.set(IrisContext.require().getChunkContext());
            }
            afterClose.set(IrisContext.get());
        } catch (Throwable e) {
            failure.compareAndSet(null, e);
        }
    }

    private void await(CyclicBarrier barrier) throws BrokenBarrierException, InterruptedException, TimeoutException {
        barrier.await(5L, TimeUnit.SECONDS);
    }
}
