package art.arcane.iris.world.lifecycle;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.nativelib.terrain.WorldRuntimeExecution;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

final class WorldLifecycleSupport {
    static final WorldRuntimeExecution EXECUTION = new IrisWorldRuntimeExecution();

    private WorldLifecycleSupport() {
    }

    static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException invocationTargetException && invocationTargetException.getCause() != null) {
            return unwrap(invocationTargetException.getCause());
        }
        if (throwable instanceof java.util.concurrent.CompletionException completionException && completionException.getCause() != null) {
            return unwrap(completionException.getCause());
        }
        if (throwable instanceof ExecutionException executionException && executionException.getCause() != null) {
            return unwrap(executionException.getCause());
        }
        return throwable;
    }

    static Object invokeNamed(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }

    static boolean hasExistingWorldData(NamespacedKey worldKey) {
        File worldFolder = IrisWorldStorage.dimensionRoot(worldKey);
        return new File(worldFolder, "region").exists()
                || new File(worldFolder, "entities").exists()
                || new File(worldFolder, "poi").exists()
                || new File(worldFolder, "data/paper/metadata.dat").exists()
                || new File(worldFolder, "paper-world.yml").exists();
    }

    static CompletableFuture<Boolean> unloadWorldAsync(CapabilitySnapshot capabilities, World world, boolean save) {
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (capabilities.nativeRuntime() != null) {
            return capabilities.nativeRuntime().unload(world, save, EXECUTION);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        EXECUTION.runGlobal(() -> result.complete(Bukkit.unloadWorld(world, save)))
                .whenComplete((unused, failure) -> {
                    if (failure != null) {
                        result.completeExceptionally(unwrap(failure));
                    }
                });
        return result;
    }

    static boolean isGlobalTickThread(CapabilitySnapshot capabilities) {
        return capabilities.nativeRuntime() != null && capabilities.nativeRuntime().isGlobalTickThread();
    }

    private static final class IrisWorldRuntimeExecution implements WorldRuntimeExecution {
        public boolean regionized() {
            return J.isFolia();
        }

        public boolean primaryThread() {
            return J.isPrimaryThread();
        }

        public CompletableFuture<Void> runGlobal(Runnable task) {
            if (!J.isFolia()) {
                return J.sfut(task);
            }
            CompletableFuture<Void> result = new CompletableFuture<>();
            Runnable settlement = () -> {
                try {
                    task.run();
                    result.complete(null);
                } catch (Throwable failure) {
                    result.completeExceptionally(unwrap(failure));
                }
            };
            try {
                if (!FoliaScheduler.runGlobal(BukkitPlatform.plugin(), settlement)) {
                    result.completeExceptionally(new IllegalStateException("Failed to schedule global world lifecycle task."));
                }
            } catch (Throwable failure) {
                result.completeExceptionally(unwrap(failure));
            }
            return result;
        }

        public CompletableFuture<Void> runAsync(Runnable task) {
            return J.afut(task);
        }

        public void reportFailure(String message, Throwable failure) {
            IrisLogging.reportError(message, failure);
        }
    }
}
