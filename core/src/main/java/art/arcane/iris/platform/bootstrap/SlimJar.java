package art.arcane.iris.platform.bootstrap;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.platform.bukkit.plugin.VolmitPlugin;
import io.github.slimjar.app.builder.ApplicationBuilder;
import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import io.github.slimjar.injector.loader.factory.InjectableFactory;
import io.github.slimjar.logging.ProcessLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class SlimJar {
    private static final boolean DEBUG = Boolean.getBoolean("iris.debug-slimjar");

    private static final ReentrantLock lock = new ReentrantLock();
    private static final AtomicBoolean loaded = new AtomicBoolean();

    public static void loadBootstrap(Path downloadPath, BootstrapLogger logger) {
        if (loaded.get()) {
            return;
        }
        lock.lock();
        try {
            if (loaded.get()) {
                return;
            }
            ApplicationBuilder.appending("Iris")
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
            try {
                new SpigotApplicationBuilder(plugin)
                        .downloadDirectoryPath(downloadPath)
                        .debug(DEBUG)
                        .build();
            } catch (Throwable e) {
                // The Spigot builder is a probe: not every server exposes it, and the fallback is the
                // supported path on the ones that do not.
                debug(plugin, "Failed to inject the library loader, falling back to application builder");
                ApplicationBuilder.appending(plugin.getName())
                        .injectableFactory(InjectableFactory.selecting(InjectableFactory.ERROR, InjectableFactory.INJECTABLE, InjectableFactory.WRAPPED, InjectableFactory.UNSAFE))
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
            }
            loaded.set(true);
            debug(plugin, "Libraries loaded successfully!");
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
