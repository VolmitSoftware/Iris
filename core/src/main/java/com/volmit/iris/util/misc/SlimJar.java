package com.volmit.iris.util.misc;

import io.github.slimjar.app.builder.ApplicationBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class SlimJar {
    private static final Logger LOGGER = Logger.getLogger("Iris");
    private static final ReentrantLock lock = new ReentrantLock();
    private static final AtomicBoolean loaded = new AtomicBoolean();

    public static void load(@Nullable File localRepository) {
        if (loaded.get()) return;
        lock.lock();

        try {
            if (loaded.getAndSet(true)) return;
            if (localRepository == null) {
                localRepository = new File(".iris/libraries");
            }

            ApplicationBuilder.appending("Iris")
                    .downloadDirectoryPath(localRepository.toPath())
                    .logger((message, args) -> {
                        if (!message.startsWith("Loaded library ")) return;
                        LOGGER.info(message.formatted(args));
                    })
                    .build();
        } finally {
            lock.unlock();
        }
    }
}
