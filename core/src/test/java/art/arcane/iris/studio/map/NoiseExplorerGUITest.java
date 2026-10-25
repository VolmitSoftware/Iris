package art.arcane.iris.studio.map;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.MeteredCache;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.studio.view.GuiHost;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class NoiseExplorerGUITest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Test
    public void hotloadKeepsTheWorldThatOpenedTheNoiseView() throws Exception {
        UUID worldId = UUID.randomUUID();
        Engine refreshed = mock(Engine.class);
        Engine otherWorld = mock(Engine.class);
        IrisSettings previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        GuiHost.Provider previous = GuiHost.get();
        GuiHost.Provider provider = mock(GuiHost.Provider.class);
        when(provider.findEngine(worldId)).thenReturn(refreshed);
        when(provider.findActiveEngine()).thenReturn(otherWorld);
        AtomicReference<NoiseExplorerGUI> opened = new AtomicReference<>();
        GuiHost.set(provider);
        try {
            SwingUtilities.invokeAndWait(() -> opened.set(NoiseExplorerGUI.createNoiseExplorer(new PaletteExplorerStart(null, worldId, null, null, 0, 0))));
            Method lookup = NoiseExplorerGUI.class.getDeclaredMethod("engineAfterHotload");
            lookup.setAccessible(true);
            assertSame(refreshed, lookup.invoke(opened.get()));
        } finally {
            NoiseExplorerGUI explorer = opened.get();
            if (explorer != null) {
                SwingUtilities.invokeAndWait(explorer::close);
            }
            GuiHost.set(previous);
            IrisSettings.settings = previousSettings;
        }
    }

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
