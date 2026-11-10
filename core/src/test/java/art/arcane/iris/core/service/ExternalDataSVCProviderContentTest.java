package art.arcane.iris.core.service;

import art.arcane.iris.core.link.ExternalDataProvider;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.link.data.DataType;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformScheduler;
import org.bukkit.event.server.ServerLoadEvent;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pack validation runs before plugins like CraftEngine register their blocks, so whatever checks a pack against the
 * block registry waits until the server has started and every provider has loaded its content.
 */
public class ExternalDataSVCProviderContentTest {
    private final ExternalDataSVC external = new ExternalDataSVC();
    private final AtomicInteger runs = new AtomicInteger();
    private IrisPlatform previousPlatform;

    @Before
    public void bindPlatformRunningTasksAtOnce() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        PlatformScheduler scheduler = mock(PlatformScheduler.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(scheduler).global(any(Runnable.class));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.scheduler()).thenReturn(scheduler);
        IrisPlatforms.bind(platform);
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void anActionWaitsForTheServerToStart() {
        external.afterProviderContentLoads(runs::incrementAndGet);
        assertEquals(0, runs.get());

        external.onServerLoad(serverStarted());
        assertEquals(1, runs.get());
    }

    @Test
    public void anActionWaitsForAProviderStillLoadingItsContent() {
        LoadingProvider provider = new LoadingProvider();
        external.registerProvider(provider);
        external.afterProviderContentLoads(runs::incrementAndGet);

        external.onServerLoad(serverStarted());
        assertEquals(0, runs.get());

        provider.loading = false;
        external.onProviderContentLoaded(provider);
        assertEquals(1, runs.get());
    }

    @Test
    public void anActionRunsOnce() {
        external.afterProviderContentLoads(runs::incrementAndGet);

        external.onServerLoad(serverStarted());
        external.onServerLoad(serverStarted());

        assertEquals(1, runs.get());
    }

    @Test
    public void anActionQueuedOnceContentHasLoadedRunsAtOnce() {
        external.onServerLoad(serverStarted());

        external.afterProviderContentLoads(runs::incrementAndGet);

        assertEquals(1, runs.get());
    }

    /** Bukkit events check the server's main thread when built, so a test can only mock one. */
    private static ServerLoadEvent serverStarted() {
        return mock(ServerLoadEvent.class);
    }

    private static final class LoadingProvider extends ExternalDataProvider {
        private volatile boolean loading = true;

        private LoadingProvider() {
            super("TestBlocks");
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public boolean isLoadingContent() {
            return loading;
        }

        @Override
        public void init() {
        }

        @Override
        public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
            return List.of();
        }

        @Override
        public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
            return false;
        }
    }
}
