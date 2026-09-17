package art.arcane.iris.studio.view;

import art.arcane.iris.configuration.IrisSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;

public class DesktopGuiLaunchTest {
    private IrisSettings previousSettings;
    private boolean previousSuppressed;
    private RecordingEventQueue events;

    @Before
    public void suppressDesktop() throws Exception {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        previousSuppressed = GuiHost.isDesktopSuppressed();
        GuiHost.suppressDesktop(true);
        events = new RecordingEventQueue();
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(events);
        EventQueue.invokeAndWait(() -> {
        });
    }

    @After
    public void restoreDesktop() throws Exception {
        EventQueue.invokeAndWait(events::remove);
        GuiHost.suppressDesktop(previousSuppressed);
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void mapLaunchDoesNotCreateAWindowWithoutDesktop() throws Exception {
        VisionGUI.launch(null, null);

        assertNoDesktopFailure();
    }

    @Test
    public void noiseLaunchDoesNotCreateAWindowWithoutDesktop() throws Exception {
        NoiseExplorerGUI.launch();

        assertNoDesktopFailure();
    }

    @Test
    public void generatorLaunchDoesNotCreateAWindowWithoutDesktop() throws Exception {
        NoiseExplorerGUI.launchGeneratorKey("test", null, 12345L);

        assertNoDesktopFailure();
    }

    @Test
    public void imageMapLaunchDoesNotReadEngineWithoutDesktop() throws Exception {
        ImageMapStudioGUI.launch(null);

        assertNoDesktopFailure();
    }

    private void assertNoDesktopFailure() throws Exception {
        EventQueue.invokeAndWait(() -> {
        });
        assertNull("Desktop launch raised an exception on the AWT event thread", events.failure.get());
    }

    private static final class RecordingEventQueue extends EventQueue {
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override
        protected void dispatchEvent(AWTEvent event) {
            try {
                super.dispatchEvent(event);
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        }

        private void remove() {
            pop();
        }
    }
}
