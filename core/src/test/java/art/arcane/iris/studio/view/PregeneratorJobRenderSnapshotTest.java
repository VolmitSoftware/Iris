package art.arcane.iris.studio.view;

import art.arcane.iris.world.pregen.IrisPregenerator;
import art.arcane.iris.world.pregen.PregenPhaseTracker;
import art.arcane.iris.world.pregen.PregenRates;
import art.arcane.volmlib.util.format.MemoryMonitor;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

public class PregeneratorJobRenderSnapshotTest {
    @Test
    public void viewSnapshotPublishesAllRatesMemoryAndFailedCountsTogether() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.pregenerator.getFailedChunks()).thenReturn(2L);

        fixture.tick();

        PregenRenderSnapshot view = fixture.job.renderSnapshot();
        assertEquals(PregenRenderSnapshot.Phase.GENERATING, view.phase());
        assertEquals(12D, view.progress().chunksPerSecond(), 0D);
        assertEquals(7D, view.progress().overallChunksPerSecond(), 0D);
        assertEquals(18D, view.progress().thirtySecondChunksPerSecond(), 0D);
        assertEquals(24D, view.progress().sixtySecondChunksPerSecond(), 0D);
        assertEquals(2L, view.progress().failed());
        assertEquals(1234L, view.usedMemoryBytes());
        assertEquals(0.25D, view.memoryUsage(), 0D);
        assertEquals(256L, view.allocationBytesPerSecond());
        assertTrue(view.cached());
        assertSame(view, fixture.job.renderSnapshot());
    }

    @Test
    public void stoppingAndFinishedPhasesCannotBeOverwrittenByTickOrSave() throws Exception {
        Fixture fixture = new Fixture();
        fixture.stopping.set(true);
        fixture.tick();
        assertEquals(PregenRenderSnapshot.Phase.STOPPING, fixture.job.renderSnapshot().phase());
        fixture.job.onSaving();
        assertEquals(PregenRenderSnapshot.Phase.STOPPING, fixture.job.renderSnapshot().phase());
        fixture.stopping.set(false);
        fixture.set("finished", true);
        fixture.tick();
        assertEquals(PregenRenderSnapshot.Phase.STOPPING, fixture.job.renderSnapshot().phase());
    }

    @Test
    public void pausedAndErrorStateKeepTheirPrecedence() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.pregenerator.paused()).thenReturn(true);
        fixture.job.onSaving();
        assertEquals(PregenRenderSnapshot.Phase.PAUSED, fixture.job.renderSnapshot().phase());
        assertTrue(fixture.job.renderSnapshot().progress().paused());
        fixture.set("failure", "Preparation failed");
        fixture.tick();
        assertEquals(PregenRenderSnapshot.Phase.ERROR, fixture.job.renderSnapshot().phase());
        assertEquals("Preparation failed", fixture.job.renderSnapshot().failure());
    }

    @Test
    public void finishedWindowCannotPauseItsReplacementJob() throws Exception {
        Fixture previous = new Fixture();
        Fixture replacement = new Fixture();
        Runnable queuedPause = previous.job::togglePause;
        previous.set("finished", true);
        Field field = PregeneratorJob.class.getDeclaredField("instance");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<PregeneratorJob> instance = (AtomicReference<PregeneratorJob>) field.get(null);
        PregeneratorJob original = instance.getAndSet(replacement.job);
        try {
            queuedPause.run();
            verifyNoInteractions(previous.pregenerator, replacement.pregenerator);
        } finally {
            instance.set(original);
        }
    }

    private static final class Fixture {
        private final PregeneratorJob job = mock(PregeneratorJob.class, CALLS_REAL_METHODS);
        private final IrisPregenerator pregenerator = mock(IrisPregenerator.class);
        private final AtomicBoolean stopping = new AtomicBoolean();

        private Fixture() throws Exception {
            MemoryMonitor monitor = mock(MemoryMonitor.class);
            when(monitor.getUsedBytes()).thenReturn(1234L);
            when(monitor.getUsagePercent()).thenReturn(0.25D);
            when(monitor.getPressure()).thenReturn(256L);
            when(pregenerator.getRates()).thenReturn(new PregenRates(7D, 12D, 18D, 24D));
            set("bounds", new PregenRenderSnapshot.Bounds(-5, 2, 8, 12));
            set("pregenerator", pregenerator);
            set("monitor", monitor);
            set("stopRequested", stopping);
            set("saving", new AtomicBoolean());
            set("onProgress", List.of());
            set("apiPhases", new PregenPhaseTracker());
        }

        private void tick() {
            job.onTick(12D, 720D, 1D, 0.5D, 5L, 10L, 3L, 1000L, 2500L, "Async", true);
        }

        private void set(String name, Object value) throws Exception {
            Field field = PregeneratorJob.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(job, value);
        }
    }
}
