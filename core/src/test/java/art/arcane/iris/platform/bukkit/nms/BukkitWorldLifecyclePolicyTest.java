package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.platform.agent.Agent;
import art.arcane.iris.platform.bootstrap.SlimJar;
import art.arcane.iris.testsupport.PlatformBinding;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class BukkitWorldLifecyclePolicyTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();

    @Test
    public void nativeProvidersCloseBeforeThePluginClassLoaderIsReleased() throws Exception {
        ClassLoader loader = mock(ClassLoader.class);
        try (MockedStatic<SlimJar> providers = mockStatic(SlimJar.class);
             MockedStatic<Agent> agent = mockStatic(Agent.class)) {
            agent.when(() -> Agent.releaseClassLoader(loader)).thenAnswer(invocation -> {
                providers.verify(SlimJar::closeNativeProviders);
                return null;
            });
            BukkitWorldLifecyclePolicy.INSTANCE.releaseClassLoader(loader);
            agent.verify(() -> Agent.releaseClassLoader(loader));
        }
    }

    @Test
    public void nativeProviderCloseFailureStillReleasesThePluginClassLoader() throws Exception {
        ClassLoader loader = mock(ClassLoader.class);
        IOException failure = new IOException("provider close failed");
        try (MockedStatic<SlimJar> providers = mockStatic(SlimJar.class);
             MockedStatic<Agent> agent = mockStatic(Agent.class)) {
            providers.when(SlimJar::closeNativeProviders).thenThrow(failure);
            IllegalStateException reported = assertThrows(IllegalStateException.class,
                    () -> BukkitWorldLifecyclePolicy.INSTANCE.releaseClassLoader(loader));
            assertSame(failure, reported.getCause());
            agent.verify(() -> Agent.releaseClassLoader(loader));
        }
    }
    @Test
    public void providerAndLoaderCloseFailuresAreBothPreserved() throws Exception {
        ClassLoader loader = mock(ClassLoader.class);
        IOException providerFailure = new IOException("provider close failed");
        IllegalStateException loaderFailure = new IllegalStateException("loader close failed");
        try (MockedStatic<SlimJar> providers = mockStatic(SlimJar.class);
             MockedStatic<Agent> agent = mockStatic(Agent.class)) {
            providers.when(SlimJar::closeNativeProviders).thenThrow(providerFailure);
            agent.when(() -> Agent.releaseClassLoader(loader)).thenThrow(loaderFailure);
            IllegalStateException reported = assertThrows(IllegalStateException.class,
                    () -> BukkitWorldLifecyclePolicy.INSTANCE.releaseClassLoader(loader));
            assertSame(loaderFailure, reported);
            assertEquals(1, reported.getSuppressed().length);
            assertSame(providerFailure, reported.getSuppressed()[0]);
        }
    }
}
