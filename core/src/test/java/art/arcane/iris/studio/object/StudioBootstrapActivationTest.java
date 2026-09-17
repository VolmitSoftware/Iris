package art.arcane.iris.studio.object;

import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.world.task.J;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StudioBootstrapActivationTest {
    @Test(timeout = 5000L)
    public void activationReturnsPendingFutureAndFinishesOnlyAfterNativeRings() throws Exception {
        verifyActivation(false);
    }

    @Test(timeout = 5000L)
    public void failedNativeRingsPreserveCauseAndDoNotEndBootstrap() throws Exception {
        verifyActivation(true);
    }

    private void verifyActivation(boolean fail) throws Exception {
        BukkitChunkGenerator provider = mock(BukkitChunkGenerator.class);
        World world = mock(World.class);
        INMSBinding nms = mock(INMSBinding.class);
        CompletableFuture<Void> rings = new CompletableFuture<>();
        when(nms.completeStudioStructureBootstrap(world)).thenReturn(rings);
        try (MockedStatic<INMS> binding = mockStatic(INMS.class);
             MockedStatic<J> scheduling = mockStatic(J.class)) {
            binding.when(INMS::get).thenReturn(nms);
            scheduling.when(() -> J.sfut(any(Supplier.class))).thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return CompletableFuture.completedFuture(supplier.get());
            });
            scheduling.when(() -> J.sfut(any(Runnable.class))).thenAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(0);
                runnable.run();
                return CompletableFuture.completedFuture(null);
            });

            CompletableFuture<Void> activation = activate(world, provider);
            assertFalse(activation.isDone());
            verify(provider, never()).endStudioEntryBootstrap();

            if (fail) {
                IllegalStateException failure = new IllegalStateException("ring calculation failed");
                rings.completeExceptionally(failure);
                CompletionException reported = assertThrows(CompletionException.class, activation::join);
                assertSame(failure, reported.getCause());
                verify(provider, never()).endStudioEntryBootstrap();
            } else {
                rings.complete(null);
                activation.join();
                verify(provider).endStudioEntryBootstrap();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Void> activate(World world, PlatformChunkGenerator provider) throws Exception {
        Method method = StudioOpenCoordinator.class.getDeclaredMethod(
                "endStudioEntryBootstrap", World.class, PlatformChunkGenerator.class);
        method.setAccessible(true);
        return (CompletableFuture<Void>) method.invoke(StudioOpenCoordinator.get(), world, provider);
    }
}
