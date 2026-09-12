package art.arcane.iris.studio.view;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.world.pregen.PregenApiPhase;
import art.arcane.iris.world.pregen.PregenApiSink;
import art.arcane.iris.world.pregen.PregenListener;
import art.arcane.iris.world.pregen.PregenTask;
import art.arcane.iris.world.pregen.PregeneratorMethod;
import art.arcane.iris.spi.IrisServices;
import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PregeneratorJobTerminalProgressTest {
    @Test
    public void finalApiEventIncludesEveryChunkCompletedDuringClose() throws Exception {
        List<ProgressEvent> events = run(new Completion(false, false, false));

        assertCompleted(events);
    }

    @Test
    public void cachedCompletionsDrainedDuringCloseReachTheSameTerminalTotal() throws Exception {
        List<ProgressEvent> events = run(new Completion(true, false, false));

        assertCompleted(events);
    }

    @Test
    public void failedFinalRequestRemainsIncompleteWithCurrentFailureCount() throws Exception {
        List<ProgressEvent> events = run(new Completion(false, true, false));
        ProgressEvent terminal = events.getLast();

        assertEquals(PregenApiPhase.CANCELLED, terminal.phase());
        assertEquals(8L, terminal.progress().generated());
        assertEquals(9L, terminal.progress().totalChunks());
        assertEquals(1L, terminal.progress().chunksRemaining());
        assertEquals(1L, terminal.progress().failed());
        assertFalse(events.stream().anyMatch(event -> event.phase() == PregenApiPhase.COMPLETED));
    }

    @Test
    public void explicitCancellationPublishesItsDrainedPartialCount() throws Exception {
        List<ProgressEvent> events = run(new Completion(false, false, true));
        ProgressEvent terminal = events.getLast();

        assertEquals(PregenApiPhase.CANCELLED, terminal.phase());
        assertEquals(1L, terminal.progress().generated());
        assertEquals(9L, terminal.progress().totalChunks());
        assertEquals(8L, terminal.progress().chunksRemaining());
        assertEquals(0L, terminal.progress().failed());
    }

    private static void assertCompleted(List<ProgressEvent> events) {
        ProgressEvent terminal = events.getLast();
        assertEquals(PregenApiPhase.COMPLETED, terminal.phase());
        assertEquals(9L, terminal.progress().generated());
        assertEquals(9L, terminal.progress().totalChunks());
        assertEquals(0L, terminal.progress().chunksRemaining());
        assertEquals(0L, terminal.progress().failed());
        assertEquals(100D, terminal.progress().percent(), 0D);
        assertTrue(events.stream().anyMatch(event -> event.phase() == PregenApiPhase.SAVING
                && event.progress().generated() == 0L));
        assertFalse(events.stream().anyMatch(event -> event.phase() == PregenApiPhase.CANCELLED));
        assertEquals(1L, events.stream().filter(event -> event.phase() == PregenApiPhase.COMPLETED).count());
    }

    private static List<ProgressEvent> run(Completion completion) throws Exception {
        IrisSettings previousSettings = IrisSettings.settings;
        PregenApiSink previousSink = IrisServices.getOrNull(PregenApiSink.class);
        IrisSettings.settings = new IrisSettings();
        CopyOnWriteArrayList<ProgressEvent> events = new CopyOnWriteArrayList<>();
        IrisServices.register(PregenApiSink.class,
                (PregenApiSink) (phase, progress) -> events.add(new ProgressEvent(phase, progress)));
        PregeneratorMethod method = mock(PregeneratorMethod.class);
        when(method.getMethod(anyInt(), anyInt())).thenReturn("terminal-progress-test");
        ArrayList<PregenListener> pending = new ArrayList<>();
        doAnswer(invocation -> {
            pending.add(invocation.getArgument(2));
            if (completion.cancel()) {
                PregeneratorJob.shutdownInstance();
            }
            return null;
        }).when(method).generateChunk(anyInt(), anyInt(), any(PregenListener.class));
        doAnswer(invocation -> {
            for (int index = 0; index < pending.size(); index++) {
                PregenListener listener = pending.get(index);
                if (completion.failLast() && index == pending.size() - 1) {
                    listener.onChunkFailed(0, 0);
                } else {
                    listener.onChunkGenerated(0, 0, completion.cached());
                }
            }
            return null;
        }).when(method).close();
        PregenTask task = PregenTask.builder().center(new Position2(0, 0))
                .radiusX(1).radiusZ(1).gui(false).build();
        Thread worker = null;
        try {
            assertNull(PregeneratorJob.getInstance());
            PregeneratorJob job = new PregeneratorJob(new PregeneratorJob.Configuration(task, method, null, () -> {}));
            Field workerField = PregeneratorJob.class.getDeclaredField("worker");
            workerField.setAccessible(true);
            worker = (Thread) workerField.get(job);
            worker.join(10_000L);
            assertFalse(worker.isAlive());
            assertNull(PregeneratorJob.getInstance());
            return List.copyOf(events);
        } finally {
            if (worker != null && worker.isAlive()) {
                PregeneratorJob.shutdownInstance();
                worker.join(5_000L);
            }
            IrisSettings.settings = previousSettings;
            if (previousSink == null) {
                IrisServices.remove(PregenApiSink.class);
            } else {
                IrisServices.register(PregenApiSink.class, previousSink);
            }
        }
    }

    private record Completion(boolean cached, boolean failLast, boolean cancel) {
    }

    private record ProgressEvent(PregenApiPhase phase, PregeneratorJob.PregenProgress progress) {
    }
}
