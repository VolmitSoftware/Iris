package art.arcane.iris.world.runtime;

import art.arcane.iris.world.lifecycle.CapabilitySnapshot;
import art.arcane.iris.spi.CapabilityProbe;
import art.arcane.iris.spi.IrisLogging;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

final class PaperLikeRuntimeControlBackend implements WorldRuntimeControlBackend {
    private final CapabilitySnapshot capabilities;
    private final AtomicReference<TimeAccessStrategy> timeAccessStrategy;

    PaperLikeRuntimeControlBackend(CapabilitySnapshot capabilities) {
        this.capabilities = capabilities;
        this.timeAccessStrategy = new AtomicReference<>();
    }

    @Override
    public String backendName() {
        return "paper_like_runtime";
    }

    @Override
    public String describeCapabilities() {
        TimeAccessStrategy strategy = timeAccessStrategy.get();
        String timeAccess = strategy == null ? "deferred" : strategy.description();
        String chunkAsync = capabilities.chunkAtAsyncMethod() != null ? "world#getChunkAtAsync" : "paperlib";
        return "time=" + timeAccess + ", chunkAsync=" + chunkAsync + ", teleport=entity_scheduler";
    }

    @Override
    public OptionalLong readDayTime(World world) {
        if (world == null) {
            return OptionalLong.empty();
        }

        TimeAccessStrategy strategy = resolveTimeAccessStrategy(world);
        if (strategy == null) {
            return OptionalLong.empty();
        }

        try {
            Object handle = strategy.handleMethod().invoke(world);
            if (handle == null) {
                return OptionalLong.empty();
            }

            Object value = strategy.readMethod().invoke(strategy.readOwner(handle), strategy.readArguments(handle));
            if (value instanceof Long longValue) {
                return OptionalLong.of(longValue.longValue());
            }
            if (value instanceof Number number) {
                return OptionalLong.of(number.longValue());
            }
        } catch (Throwable failure) {
            IrisLogging.reportError("Failed to read the runtime day time of world \"" + world.getName()
                    + "\" through " + strategy.description() + ".", failure);
        }

        return OptionalLong.empty();
    }

    @Override
    public boolean writeDayTime(World world, long dayTime) throws ReflectiveOperationException {
        if (world == null) {
            return false;
        }

        TimeAccessStrategy strategy = resolveTimeAccessStrategy(world);
        if (strategy == null || !strategy.writable()) {
            return false;
        }

        Object handle = strategy.handleMethod().invoke(world);
        if (handle == null) {
            return false;
        }

        Object writeOwner = strategy.writeOwner(handle);
        if (writeOwner == null) {
            return false;
        }

        strategy.writeMethod().invoke(writeOwner, dayTime);
        return true;
    }

    @Override
    public void syncTime(World world) {
        TimeAccessStrategy strategy = timeAccessStrategy.get();
        if (strategy == null || strategy.syncMethod() == null) {
            return;
        }

        try {
            Object craftServer = Bukkit.getServer();
            if (craftServer == null) {
                return;
            }

            Object serverHandle = strategy.serverHandleMethod() == null ? null : strategy.serverHandleMethod().invoke(craftServer);
            if (serverHandle == null) {
                return;
            }

            strategy.syncMethod().invoke(serverHandle);
        } catch (Throwable failure) {
            IrisLogging.reportError("Failed to push the runtime day time of world \""
                    + (world == null ? "unknown" : world.getName()) + "\" to its players.", failure);
        }
    }

    @Override
    public CompletableFuture<Chunk> requestChunkAsync(World world, int chunkX, int chunkZ, boolean generate) {
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("World is null."));
        }

        if (capabilities.chunkAtAsyncMethod() != null) {
            try {
                Object result = capabilities.chunkAtAsyncMethod().invoke(world, chunkX, chunkZ, generate);
                if (result instanceof CompletableFuture<?>) {
                    @SuppressWarnings("unchecked")
                    CompletableFuture<Chunk> future = (CompletableFuture<Chunk>) result;
                    return future;
                }
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        CompletableFuture<Chunk> future = PaperLib.getChunkAtAsync(world, chunkX, chunkZ, generate);
        if (future == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("PaperLib did not return a chunk future."));
        }

        return future;
    }

    private TimeAccessStrategy resolveTimeAccessStrategy(World world) {
        TimeAccessStrategy current = timeAccessStrategy.get();
        if (current != null) {
            return current;
        }

        synchronized (timeAccessStrategy) {
            current = timeAccessStrategy.get();
            if (current != null) {
                return current;
            }

            TimeAccessStrategy resolved = probeTimeAccessStrategy(world);
            timeAccessStrategy.set(resolved);
            return resolved;
        }
    }

    private TimeAccessStrategy probeTimeAccessStrategy(World world) {
        if (world == null) {
            return TimeAccessStrategy.unsupported();
        }

        return CapabilityProbe.attempt("runtime world clock", () -> resolveTimeAccess(world),
                TimeAccessStrategy.unsupported());
    }

    private TimeAccessStrategy resolveTimeAccess(World world) throws ReflectiveOperationException {
        Method handleMethod = resolveZeroArgMethod(world.getClass(), "getHandle");
        if (handleMethod == null) {
            return TimeAccessStrategy.unsupported();
        }

        Object handle = handleMethod.invoke(world);
        if (handle == null) {
            return TimeAccessStrategy.unsupported();
        }

        Method readMethod = resolveZeroArgMethod(handle.getClass(), "getDayTime");
        Method writeMethod = resolveLongArgMethod(handle.getClass(), "setDayTime");
        if (readMethod != null && writeMethod != null) {
            return TimeAccessStrategy.forHandle(handleMethod, readMethod, writeMethod, "runtime_handle#setDayTime");
        }

        Method levelDataMethod = resolveZeroArgMethod(handle.getClass(), "serverLevelData");
        if (levelDataMethod == null) {
            levelDataMethod = resolveZeroArgMethod(handle.getClass(), "getLevelData");
        }
        if (levelDataMethod != null) {
            Object levelData = levelDataMethod.invoke(handle);
            if (levelData != null) {
                Method levelDataReadMethod = resolveZeroArgMethod(levelData.getClass(), "getDayTime");
                Method levelDataWriteMethod = resolveLongArgMethod(levelData.getClass(), "setDayTime");
                if (levelDataReadMethod != null && levelDataWriteMethod != null) {
                    return TimeAccessStrategy.forLevelData(handleMethod, levelDataMethod, levelDataReadMethod, levelDataWriteMethod, "world_data#setDayTime");
                }
            }
        }
        return TimeAccessStrategy.unsupported(handleMethod);
    }

    private static Method resolveZeroArgMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    private static Method resolveLongArgMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, long.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    private record TimeAccessStrategy(
            Method handleMethod,
            Method levelDataMethod,
            Method readMethod,
            Method writeMethod,
            Method serverHandleMethod,
            Method syncMethod,
            String description
    ) {
        static TimeAccessStrategy forHandle(Method handleMethod, Method readMethod, Method writeMethod, String description) {
            Method serverHandleMethod = resolveCraftServerMethod("getHandle");
            Method syncMethod = resolveServerMethod(serverHandleMethod, "forceTimeSynchronization");
            return new TimeAccessStrategy(handleMethod, null, readMethod, writeMethod, serverHandleMethod, syncMethod, description);
        }

        static TimeAccessStrategy forLevelData(Method handleMethod, Method levelDataMethod, Method readMethod, Method writeMethod, String description) {
            Method serverHandleMethod = resolveCraftServerMethod("getHandle");
            Method syncMethod = resolveServerMethod(serverHandleMethod, "forceTimeSynchronization");
            return new TimeAccessStrategy(handleMethod, levelDataMethod, readMethod, writeMethod, serverHandleMethod, syncMethod, description);
        }

        static TimeAccessStrategy unsupported() {
            return new TimeAccessStrategy(null, null, null, null, null, null, "unsupported");
        }

        static TimeAccessStrategy unsupported(Method handleMethod) {
            return new TimeAccessStrategy(handleMethod, null, null, null, null, null, "unsupported");
        }

        boolean writable() {
            return handleMethod != null && readMethod != null && writeMethod != null;
        }

        Object readOwner(Object handle) throws ReflectiveOperationException {
            if (levelDataMethod == null) {
                return handle;
            }

            return levelDataMethod.invoke(handle);
        }

        Object[] readArguments(Object handle) {
            return new Object[0];
        }

        Object writeOwner(Object handle) throws ReflectiveOperationException {
            if (levelDataMethod == null) {
                return handle;
            }

            return levelDataMethod.invoke(handle);
        }

        private static Method resolveCraftServerMethod(String name) {
            return CapabilityProbe.attempt("craft server#" + name, () -> {
                Method method = Bukkit.getServer().getClass().getMethod(name);
                method.setAccessible(true);
                return method;
            }, null);
        }

        private static Method resolveServerMethod(Method serverHandleMethod, String name) {
            if (serverHandleMethod == null) {
                return null;
            }

            return CapabilityProbe.attempt("minecraft server#" + name, () -> {
                Object craftServer = Bukkit.getServer();
                if (craftServer == null) {
                    return null;
                }

                Object serverHandle = serverHandleMethod.invoke(craftServer);
                if (serverHandle == null) {
                    return null;
                }

                Method method = serverHandle.getClass().getMethod(name);
                method.setAccessible(true);
                return method;
            }, null);
        }
    }
}
