package com.volmit.iris.util.misc;

import io.github.slimjar.app.builder.ApplicationBuilder;
import io.github.slimjar.logging.ProcessLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SlimJar {
    private static final Logger LOGGER = Logger.getLogger("Iris");
    private static final ReentrantLock lock = new ReentrantLock();
    private static final AtomicBoolean loaded = new AtomicBoolean();

    public static void debug(boolean debug) {
        LOGGER.setLevel(debug ? Level.FINE : Level.INFO);
    }

    public static void load(@Nullable File localRepository) {
        if (loaded.get()) return;
        lock.lock();

        try {
            if (loaded.getAndSet(true)) return;
            if (localRepository == null) {
                localRepository = new File(".iris/libraries");
            }

            LOGGER.info("Loading libraries...");
            ApplicationBuilder.appending("Iris")
                    .downloadDirectoryPath(localRepository.toPath())
                    .logger(new ProcessLogger() {
                        @Override
                        public void info(@NotNull String message, @Nullable Object... args) {
                            LOGGER.fine(message.formatted(args));
                        }

                        @Override
                        public void error(@NotNull String message, @Nullable Object... args) {
                            LOGGER.severe(message.formatted(args));
                        }

                        @Override
                        public void debug(@NotNull String message, @Nullable Object... args) {
                            LOGGER.fine(message.formatted(args));
                        }
                    })
                    .build();
            LOGGER.info("Libraries loaded successfully!");
        } finally {
            lock.unlock();
        }
    }
}
