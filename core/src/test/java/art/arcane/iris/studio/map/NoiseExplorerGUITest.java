package art.arcane.iris.studio.map;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.MeteredCache;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NoiseExplorerGUITest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Test
    public void allExecutorsRegisterForPreservationAndCloseWithWindow() throws Exception {
        PreservationRegistry previous = IrisServices.getOrNull(PreservationRegistry.class);
        IrisSettings previousSettings = IrisSettings.settings;
        RecordingPreservationRegistry preservation = new RecordingPreservationRegistry();
        AtomicReference<NoiseExplorerGUI> explorerReference = new AtomicReference<>();
        IrisServices.register(PreservationRegistry.class, preservation);
        IrisSettings.settings = new IrisSettings();
        try {
            SwingUtilities.invokeAndWait(() -> explorerReference.set(NoiseExplorerGUI.createNoiseExplorer(null)));

            assertFalse(preservation.executors.isEmpty());
            SwingUtilities.invokeAndWait(explorerReference.get()::close);
            for (ExecutorService executor : preservation.executors) {
                assertTrue(executor.isShutdown());
            }
        } finally {
            NoiseExplorerGUI explorer = explorerReference.get();
            if (explorer != null) {
                SwingUtilities.invokeAndWait(explorer::close);
            }
            if (previous == null) {
                IrisServices.remove(PreservationRegistry.class);
            } else {
                IrisServices.register(PreservationRegistry.class, previous);
            }
            IrisSettings.settings = previousSettings;
        }
    }

    private static final class RecordingPreservationRegistry implements PreservationRegistry {
        private final List<ExecutorService> executors = new ArrayList<>();

        @Override
        public void register(Thread thread) {
        }

        @Override
        public void register(ExecutorService service) {
            executors.add(service);
        }

        @Override
        public void registerCache(MeteredCache cache) {
        }

        @Override
        public void dereference() {
        }
    }
}
