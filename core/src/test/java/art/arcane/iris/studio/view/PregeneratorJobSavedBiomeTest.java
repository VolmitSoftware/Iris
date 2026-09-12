package art.arcane.iris.studio.view;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.spi.IrisLogging;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PregeneratorJobSavedBiomeTest {
    @Test
    public void generatedChunkLoadingDoesNotEscapeRendererWork() throws Exception {
        Fixture fixture = new Fixture();
        doThrow(new SavedBiomeUnavailableException("Saved biome information is loading.", true))
                .when(fixture.engine).draw(anyDouble(), anyDouble());

        fixture.job.onChunkGenerated(2, -3, false);
        assertEquals(1, fixture.tasks.size());
        fixture.tasks.removeFirst().run();
        verify(fixture.renderer).submit(anyInt(), anyInt(), any());
    }

    @Test
    public void existingChunkPreviewRunsOnTheRendererWorker() throws Exception {
        Fixture fixture = new Fixture();
        doThrow(new SavedBiomeUnavailableException("Saved biome information is loading.", true))
                .when(fixture.engine).draw(anyDouble(), anyDouble());

        fixture.job.onChunkExistsInRegionGen(2, -3);
        verify(fixture.engine, never()).draw(anyDouble(), anyDouble());
        assertEquals(1, fixture.tasks.size());
        fixture.tasks.removeFirst().run();
    }

    @Test
    public void existingChunksDoNotResolveBiomesWhileThePreviewIsHidden() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.renderer.isVisibleFrame()).thenReturn(false);

        fixture.job.onChunkExistsInRegionGen(2, -3);

        assertEquals(0, fixture.tasks.size());
        verify(fixture.engine, never()).draw(anyDouble(), anyDouble());
    }

    @Test
    public void queuedPreviewSkipsReadsWhenTheFrameBecomesHidden() throws Exception {
        Fixture fixture = new Fixture();
        fixture.job.onChunkGenerated(2, -3, false);
        when(fixture.renderer.isVisibleFrame()).thenReturn(false);

        fixture.tasks.removeFirst().run();

        verify(fixture.engine, never()).draw(anyDouble(), anyDouble());
    }

    @Test
    public void actualDrawFailureStillReportsWhenShutdownStartsDuringRendering() throws Exception {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("Palette failure");
        doAnswer(invocation -> {
            when(fixture.engine.isClosing()).thenReturn(true);
            throw failure;
        }).when(fixture.engine).drawForPreview(anyInt(), anyInt());
        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            fixture.job.onChunkGenerated(2, -3, false);
            fixture.tasks.removeFirst().run();

            logging.verify(() -> IrisLogging.reportError(
                    "Unable to draw the pregeneration preview for chunk 2,-3.", failure));
        }
    }

    private static final class Fixture {
        private final PregeneratorJob job = mock(PregeneratorJob.class, CALLS_REAL_METHODS);
        private final Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        private final PregenRenderer renderer = mock(PregenRenderer.class);
        private final ArrayList<Runnable> tasks = new ArrayList<>();

        private Fixture() throws Exception {
            ExecutorService service = mock(ExecutorService.class);
            doAnswer(invocation -> {
                tasks.add(invocation.getArgument(0));
                return null;
            }).when(service).execute(any(Runnable.class));
            when(renderer.isVisibleFrame()).thenReturn(true);
            setField("engine", engine);
            setField("renderer", renderer);
            setField("service", service);
        }

        private void setField(String name, Object value) throws Exception {
            Field field = PregeneratorJob.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(job, value);
        }
    }
}
