package art.arcane.iris.studio.view;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.localization.DesktopUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.spi.IrisLogging;
import org.junit.Test;
import org.mockito.MockedStatic;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

public class PregenRendererTest {
    @Test
    public void keyboardAndButtonActivationToggleExactlyOnceAndTerminalControlsAreDisabled() throws Exception {
        onEdt(() -> {
            Source source = new Source();
            AtomicInteger calls = new AtomicInteger();
            PregenRenderer renderer = renderer(source, () -> {
                calls.incrementAndGet();
                source.snapshot = snapshot(source.snapshot.phase() == PregenRenderSnapshot.Phase.PAUSED
                        ? PregenRenderSnapshot.Phase.GENERATING : PregenRenderSnapshot.Phase.PAUSED, null, 0);
            });
            try {
                JButton button = field(renderer, "pauseButton", JButton.class);
                Object pauseAction = renderer.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                        .get(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0, true));
                renderer.getActionMap().get(pauseAction).actionPerformed(new ActionEvent(renderer, 0, "P"));
                assertEquals(1, calls.get());
                assertEquals(PregenRenderSnapshot.Phase.PAUSED, source.snapshot.phase());
                Object enterAction = button.getInputMap(JComponent.WHEN_FOCUSED)
                        .get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
                button.getActionMap().get(enterAction).actionPerformed(new ActionEvent(button, 0, "Enter"));
                assertEquals(2, calls.get());
                button.doClick(0);
                assertEquals(3, calls.get());
                for (PregenRenderSnapshot.Phase phase : new PregenRenderSnapshot.Phase[]{
                        PregenRenderSnapshot.Phase.STOPPING, PregenRenderSnapshot.Phase.COMPLETED,
                        PregenRenderSnapshot.Phase.ERROR}) {
                    source.snapshot = snapshot(phase, null, 0);
                    refresh(renderer);
                    assertFalse(button.isEnabled());
                    renderer.getActionMap().get(pauseAction).actionPerformed(new ActionEvent(renderer, 0, "P"));
                    button.getActionMap().get(enterAction).actionPerformed(new ActionEvent(button, 0, "Enter"));
                    button.doClick(0);
                    assertEquals(3, calls.get());
                }
            } finally {
                renderer.close();
            }
        });
    }

    @Test
    public void initializingAndPausedEtaRemainPending() throws Exception {
        onEdt(() -> {
            Source source = new Source();
            source.snapshot = snapshot(PregenRenderSnapshot.Phase.INITIALIZING, null, 0);
            PregenRenderer renderer = renderer(source, () -> { });
            try {
                JLabel eta = field(renderer, "etaValue", JLabel.class);
                String pending = IrisLanguage.plain(DesktopUiMessages.PREGEN_METHOD_PENDING);
                assertEquals(pending, eta.getText());
                source.snapshot = snapshot(PregenRenderSnapshot.Phase.PAUSED, null, 0);
                refresh(renderer);
                assertEquals(pending, eta.getText());
                for (PregenRenderSnapshot.Phase phase : new PregenRenderSnapshot.Phase[]{
                        PregenRenderSnapshot.Phase.ERROR, PregenRenderSnapshot.Phase.STOPPING}) {
                    source.snapshot = snapshot(phase, null, 0);
                    refresh(renderer);
                    assertEquals(pending, eta.getText());
                }
                source.snapshot = snapshot(PregenRenderSnapshot.Phase.GENERATING, null, 0);
                refresh(renderer);
                assertFalse(pending.equals(eta.getText()));
            } finally {
                renderer.close();
            }
        });
    }

    @Test
    public void failureDetailsAndPauseFailureRemainVisibleUntilSuccessfulRetry() throws Exception {
        onEdt(() -> {
            Source source = new Source();
            source.snapshot = snapshot(PregenRenderSnapshot.Phase.GENERATING, "chunk save failed", 1);
            AtomicBoolean failPause = new AtomicBoolean(true);
            PregenRenderer renderer = renderer(source, () -> {
                if (failPause.get()) {
                    throw new IllegalStateException("scheduler refused pause");
                }
            });
            try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class, invocation -> {
                if (invocation.getMethod().getName().equals("reportError")
                        && invocation.getMethod().getParameterCount() == 2
                        && invocation.getMethod().getParameterTypes()[0] == String.class
                        && invocation.getMethod().getParameterTypes()[1] == Throwable.class) {
                    return null;
                }
                return invocation.callRealMethod();
            })) {
                JButton button = field(renderer, "pauseButton", JButton.class);
                JTextArea details = field(renderer, "failureLabel", JTextArea.class);
                assertTrue(details.getLineWrap());
                assertTrue(details.getText().contains("chunk save failed"));
                int selectionStart = details.getText().indexOf("chunk save failed");
                details.select(selectionStart, selectionStart + "chunk save failed".length());
                PregenRenderSnapshot previous = source.snapshot;
                source.snapshot = new PregenRenderSnapshot(previous.bounds(), previous.progress(),
                        previous.phase(), previous.cached(), previous.usedMemoryBytes() + 1024,
                        previous.memoryUsage(), previous.allocationBytesPerSecond(), previous.failure());
                refresh(renderer);
                assertEquals("chunk save failed", details.getSelectedText());
                assertEquals(selectionStart, details.getSelectionStart());
                button.doClick(0);
                assertTrue(details.getText().contains("scheduler refused pause"));
                source.snapshot = snapshot(PregenRenderSnapshot.Phase.GENERATING, null, 2);
                refresh(renderer);
                assertTrue(details.getText().contains("scheduler refused pause"));
                failPause.set(false);
                button.doClick(0);
                assertFalse(details.getText().contains("scheduler refused pause"));
            } finally {
                renderer.close();
            }
        });
    }

    private static PregenRenderer renderer(Source source, Runnable pause) throws Exception {
        Constructor<PregenRenderer> constructor = PregenRenderer.class.getDeclaredConstructor(
                PregenRenderSource.class, Runnable.class);
        constructor.setAccessible(true);
        return constructor.newInstance(source, pause);
    }

    private static void refresh(PregenRenderer renderer) throws Exception {
        Method refresh = PregenRenderer.class.getDeclaredMethod("refresh");
        refresh.setAccessible(true);
        refresh.invoke(renderer);
    }

    private static <T> T field(PregenRenderer renderer, String name, Class<T> type) throws Exception {
        Field field = PregenRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(renderer));
    }

    private static void onEdt(CheckedTask task) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (MockedStatic<IrisSettings> settings = mockStatic(IrisSettings.class)) {
                settings.when(IrisSettings::get).thenReturn(new IrisSettings());
                task.run();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
    }

    private static PregenRenderSnapshot snapshot(PregenRenderSnapshot.Phase phase, String failure, long failed) {
        return new PregenRenderSnapshot(new PregenRenderSnapshot.Bounds(-32, -32, 32, 32),
                new PregeneratorJob.PregenProgress(25, 25, 100, 20, 18, 17, 16,
                        75, 4, 3000, 1000, "native", phase == PregenRenderSnapshot.Phase.PAUSED,
                        failed, "Example world", "example"), phase, false, 1024, 0.25, 256, failure);
    }

    private interface CheckedTask {
        void run() throws Exception;
    }

    private static final class Source implements PregenRenderSource {
        private PregenRenderSnapshot snapshot = snapshot(PregenRenderSnapshot.Phase.GENERATING, null, 0);
        private final PregenMapState map = new PregenMapState(snapshot.bounds());

        @Override
        public PregenRenderSnapshot renderSnapshot() {
            return snapshot;
        }

        @Override
        public PregenMapState renderMapState() {
            return map;
        }
    }
}
