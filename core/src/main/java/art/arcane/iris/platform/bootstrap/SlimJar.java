package art.arcane.iris.platform.bootstrap;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.iris.platform.bukkit.plugin.VolmitPlugin;
import io.github.slimjar.app.builder.ApplicationBuilder;
import io.github.slimjar.injector.loader.factory.InjectableFactory;
import io.github.slimjar.logging.ProcessLogger;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.net.URLClassLoader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class SlimJar {
    private static final boolean DEBUG = Boolean.getBoolean("iris.debug-slimjar");

    private static final ReentrantLock lock = new ReentrantLock();
    private static final AtomicBoolean loaded = new AtomicBoolean();
    private static URLClassLoader nativeProviders;

    public static void loadBootstrap(Path downloadPath, String minecraftVersion, BootstrapLogger logger) {
        if (loaded.get()) {
            return;
        }
        lock.lock();
        try {
            if (loaded.get()) {
                return;
            }
            NativeRuntimeLibraries libraries = NativeRuntimeLibraries.load(minecraftVersion);
            libraries.configure(ApplicationBuilder.appending("Iris"), downloadPath)
                    .injectableFactory(InjectableFactory.selecting(
                            InjectableFactory.ERROR,
                            InjectableFactory.INJECTABLE,
                            InjectableFactory.WRAPPED,
                            InjectableFactory.UNSAFE))
                    .downloadDirectoryPath(downloadPath)
                    .logger(new ProcessLogger() {
                        @Override
                        public void info(@NotNull String message, @Nullable Object... args) {
                            logger.info(message.formatted(args));
                        }

                        @Override
                        public void error(@NotNull String message, @Nullable Object... args) {
                            logger.error(message.formatted(args));
                        }

                        @Override
                        public void debug(@NotNull String message, @Nullable Object... args) {
                            logger.debug(message.formatted(args));
                        }
                    })
                    .build();
            nativeProviders = libraries.openProviderLoader(downloadPath);
            loaded.set(true);
        } finally {
            lock.unlock();
        }
    }

    public static void load() {
        if (loaded.get()) {
            return;
        }
        lock.lock();

        try {
            if (loaded.get()) {
                return;
            }
            VolmitPlugin plugin = BukkitPlatform.volmitPlugin();
            Path downloadPath = plugin.getDataFolder("cache", "libraries").toPath();
            debug(plugin, "Loading libraries...");
            NativeRuntimeLibraries libraries = NativeRuntimeLibraries.load(Bukkit.getBukkitVersion());
            libraries.configure(ApplicationBuilder.appending(plugin.getName()), downloadPath)
                    .injectableFactory(InjectableFactory.selecting(InjectableFactory.ERROR, InjectableFactory.INJECTABLE,
                            InjectableFactory.WRAPPED, InjectableFactory.UNSAFE))
                    .downloadDirectoryPath(downloadPath)
                    .logger(new ProcessLogger() {
                        @Override
                        public void info(@NotNull String message, @Nullable Object... args) {
                            SlimJar.debug(plugin, message.formatted(args));
                        }

                        @Override
                        public void error(@NotNull String message, @Nullable Object... args) {
                            plugin.getLogger().severe(message.formatted(args));
                        }

                        @Override
                        public void debug(@NotNull String message, @Nullable Object... args) {
                            SlimJar.debug(plugin, message.formatted(args));
                        }
                    })
                    .build();
            nativeProviders = libraries.openProviderLoader(downloadPath);
            loaded.set(true);
            debug(plugin, "Libraries loaded successfully!");
        } finally {
            lock.unlock();
        }
    }

    public static void closeNativeProviders() throws IOException {
        lock.lock();
        try {
            if (nativeProviders != null) {
                NativeAdapters.releaseProviderLoader(nativeProviders);
                nativeProviders.close();
                nativeProviders = null;
            }
        } finally {
            lock.unlock();
        }
    }

    private static void debug(VolmitPlugin plugin, String message) {
        if (DEBUG) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    public interface BootstrapLogger {
        void info(String message);

        void error(String message);

        void debug(String message);
    }
}
