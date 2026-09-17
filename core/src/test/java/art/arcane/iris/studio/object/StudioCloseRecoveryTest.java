package art.arcane.iris.studio.object;

import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.studio.workspace.IrisProject;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class StudioCloseRecoveryTest {
    @Test
    public void failedGeneratorCloseKeepsMutationReservationWithoutRestartingServer() throws Exception {
        PlatformChunkGenerator provider = mock(PlatformChunkGenerator.class);
        IllegalStateException failure = new IllegalStateException("generator cleanup failed");
        when(provider.closeAsync()).thenReturn(CompletableFuture.failedFuture(failure));
        LifecycleOperationCoordinator coordinator = mock(LifecycleOperationCoordinator.class);
        LifecycleOperationCoordinator.Lease lease = mock(LifecycleOperationCoordinator.Lease.class);
        when(coordinator.acquire(any(), any(), anyString())).thenReturn(lease);

        try (MockedStatic<LifecycleOperationCoordinator> lifecycle = mockStatic(LifecycleOperationCoordinator.class);
             MockedStatic<ServerConfigurator> configurator = mockStatic(ServerConfigurator.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            lifecycle.when(LifecycleOperationCoordinator::get).thenReturn(coordinator);

            StudioOpenCoordinator.StudioCloseResult result = close(provider, null).join();

            assertSame(failure, result.failureCause());
            configurator.verifyNoInteractions();
            verify(lease, never()).close();
        }
    }

    @Test
    public void pendingGeneratorRetainsProjectAndMutationReservationUntilCleanupFinishes() throws Exception {
        PlatformChunkGenerator provider = mock(PlatformChunkGenerator.class);
        CompletableFuture<Void> generatorClose = new CompletableFuture<>();
        when(provider.closeAsync()).thenReturn(generatorClose);
        IrisProject project = mock(IrisProject.class);
        LifecycleOperationCoordinator coordinator = mock(LifecycleOperationCoordinator.class);
        LifecycleOperationCoordinator.Lease lease = mock(LifecycleOperationCoordinator.Lease.class);
        when(coordinator.acquire(any(), any(), anyString())).thenReturn(lease);

        try (MockedStatic<LifecycleOperationCoordinator> lifecycle = mockStatic(LifecycleOperationCoordinator.class);
             MockedStatic<ServerConfigurator> configurator = mockStatic(ServerConfigurator.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            lifecycle.when(LifecycleOperationCoordinator::get).thenReturn(coordinator);

            CompletableFuture<StudioOpenCoordinator.StudioCloseResult> close = close(provider, project);
            assertFalse(close.isDone());
            verifyNoInteractions(project);
            verify(lease, never()).close();

            generatorClose.complete(null);
            close.join();

            verify(project).setActiveProvider(null);
            verify(project).setActiveOpenKind(null);
            verify(lease).close();
            configurator.verifyNoInteractions();
        }
    }

    @Test
    public void cancellingCloseResultDoesNotSkipCleanupOrLeaseRelease() throws Exception {
        PlatformChunkGenerator provider = mock(PlatformChunkGenerator.class);
        CompletableFuture<Void> generatorClose = new CompletableFuture<>();
        when(provider.closeAsync()).thenReturn(generatorClose);
        IrisProject project = mock(IrisProject.class);
        LifecycleOperationCoordinator coordinator = mock(LifecycleOperationCoordinator.class);
        LifecycleOperationCoordinator.Lease lease = mock(LifecycleOperationCoordinator.Lease.class);
        when(coordinator.acquire(any(), any(), anyString())).thenReturn(lease);

        try (MockedStatic<LifecycleOperationCoordinator> lifecycle = mockStatic(LifecycleOperationCoordinator.class);
             MockedStatic<ServerConfigurator> configurator = mockStatic(ServerConfigurator.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            lifecycle.when(LifecycleOperationCoordinator::get).thenReturn(coordinator);

            CompletableFuture<StudioOpenCoordinator.StudioCloseResult> result = close(provider, project);
            assertTrue(result.cancel(false));
            assertFalse(generatorClose.isDone());
            verifyNoInteractions(project);
            verify(lease, never()).close();

            generatorClose.complete(null);

            assertTrue(result.isCancelled());
            verify(project).setActiveProvider(null);
            verify(project).setActiveOpenKind(null);
            verify(lease).close();
            configurator.verifyNoInteractions();
        }
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<StudioOpenCoordinator.StudioCloseResult> close(
            PlatformChunkGenerator provider,
            IrisProject project
    ) throws Exception {
        Method method = StudioOpenCoordinator.class.getDeclaredMethod("closeWorldCoordinated",
                PlatformChunkGenerator.class, String.class, World.class, boolean.class, IrisProject.class);
        method.setAccessible(true);
        return (CompletableFuture<StudioOpenCoordinator.StudioCloseResult>) method.invoke(
                StudioOpenCoordinator.get(), provider, null, null, false, project);
    }
}
