package com.volmit.iris.util.misc;

import com.volmit.iris.Iris;
import io.github.slimjar.app.builder.ApplicationBuilder;
import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import io.github.slimjar.injector.loader.factory.InjectableFactory;
import io.github.slimjar.logging.ProcessLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static com.volmit.iris.Iris.instance;

public class SlimJar {
    private static final boolean DEBUG = Boolean.getBoolean("iris.debug-slimjar");
    private static final boolean DISABLE_REMAPPER = Boolean.getBoolean("iris.disable-remapper");

    private static final ReentrantLock lock = new ReentrantLock();
    private static final AtomicBoolean loaded = new AtomicBoolean();

    public static void load() {
        if (loaded.get()) return;
        lock.lock();

        try {
            if (loaded.getAndSet(true)) return;
            final var downloadPath = instance.getDataFolder("cache", "libraries").toPath();
            final var logger = instance.getLogger();

            logger.info("Loading libraries...");
            try {
                new SpigotApplicationBuilder(instance)
                        .downloadDirectoryPath(downloadPath)
                        .debug(DEBUG)
                        .remap(!DISABLE_REMAPPER)
                        .build();
            } catch (Throwable e) {
                Iris.warn("Failed to inject the library loader, falling back to application builder");
                ApplicationBuilder.appending(instance.getName())
                        .injectableFactory(InjectableFactory.selecting(InjectableFactory.ERROR, InjectableFactory.INJECTABLE, InjectableFactory.WRAPPED, InjectableFactory.UNSAFE))
                        .downloadDirectoryPath(downloadPath)
                        .logger(new ProcessLogger() {
                            @Override
                            public void info(@NotNull String message, @Nullable Object... args) {
                                if (!DEBUG) return;
                                instance.getLogger().info(message.formatted(args));
                            }

                            @Override
                            public void error(@NotNull String message, @Nullable Object... args) {
                                instance.getLogger().severe(message.formatted(args));
                            }

                            @Override
                            public void debug(@NotNull String message, @Nullable Object... args) {
                                if (!DEBUG) return;
                                instance.getLogger().info(message.formatted(args));
                            }
                        })
                        .build();
            }
            logger.info("Libraries loaded successfully!");
        } finally {
            lock.unlock();
        }
    }
}
