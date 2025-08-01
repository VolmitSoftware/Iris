package com.volmit.iris.util.misc;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.util.collection.KList;
import io.github.slimjar.app.builder.ApplicationBuilder;
import io.github.slimjar.injector.loader.IsolatedInjectableClassLoader;
import io.github.slimjar.injector.loader.factory.InjectableFactory;
import io.github.slimjar.logging.ProcessLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

public class SlimJar {
    private static final String NAME = "Iris";
    private static final Logger LOGGER = Logger.getLogger(NAME);
    private static final boolean DEBUG = Boolean.getBoolean("iris.debug-slimjar");
    private static final boolean DISABLE_REMAPPER = Boolean.getBoolean("iris.disable-remapper");

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

            LOGGER.info("Loading libraries...");
            load(localRepository.toPath(), new ProcessLogger() {
                @Override
                public void info(@NotNull String message, @Nullable Object... args) {
                    if (!DEBUG) return;
                    LOGGER.info(message.formatted(args));
                }

                @Override
                public void error(@NotNull String message, @Nullable Object... args) {
                    LOGGER.severe(message.formatted(args));
                }

                @Override
                public void debug(@NotNull String message, @Nullable Object... args) {
                    if (!DEBUG) return;
                    LOGGER.info(message.formatted(args));
                }
            });
            LOGGER.info("Libraries loaded successfully!");
        } finally {
            lock.unlock();
        }
    }

    private static void load(Path downloadPath, ProcessLogger logger) {
        try {
            loadSpigot(downloadPath, logger);
        } catch (Throwable e) {
            Iris.warn("Failed to inject the library loader, falling back to application builder");
            ApplicationBuilder.appending(NAME)
                    .injectableFactory(InjectableFactory.selecting(InjectableFactory.ERROR, InjectableFactory.INJECTABLE, InjectableFactory.WRAPPED, InjectableFactory.UNSAFE))
                    .downloadDirectoryPath(downloadPath)
                    .logger(logger)
                    .build();
        }
    }

    private static void loadSpigot(Path downloadPath, ProcessLogger logger) throws Throwable {
        var current = SlimJar.class.getClassLoader();
        var libraryLoaderField = current.getClass().getDeclaredField("libraryLoader");
        libraryLoaderField.setAccessible(true);
        if (!ClassLoader.class.isAssignableFrom(libraryLoaderField.getType())) throw new IllegalStateException("Failed to find library loader");
        final var libraryLoader = (ClassLoader) libraryLoaderField.get(current);

        final var pair = findRemapper();
        final var remapper = pair.getA();
        final var factory = pair.getB();
        final var classpath = new KList<URL>();

        ApplicationBuilder.injecting(NAME, classpath::add)
                .downloadDirectoryPath(downloadPath)
                .logger(logger)
                .build();

        final var urls = remapper.andThen(KList::new)
                .apply(classpath.convertNasty(url -> Path.of(url.toURI())))
                .convertNasty(path -> path.toUri().toURL())
                .toArray(URL[]::new);
        libraryLoaderField.set(current, factory.apply(urls, libraryLoader == null ? current.getParent() : libraryLoader));
    }

    private static Pair<Function<List<Path>, List<Path>>, BiFunction<URL[], ClassLoader, URLClassLoader>> findRemapper() {
        Function<List<Path>, List<Path>> mapper = null;
        BiFunction<URL[], ClassLoader, URLClassLoader> factory = null;
        if (!DISABLE_REMAPPER) {
            try {
                var libraryLoader = Class.forName("org.bukkit.plugin.java.LibraryLoader");
                var mapperField = libraryLoader.getDeclaredField("REMAPPER");
                var factoryField = libraryLoader.getDeclaredField("LIBRARY_LOADER_FACTORY");
                mapperField.setAccessible(true);
                factoryField.setAccessible(true);
                mapper = (Function<List<Path>, List<Path>>) mapperField.get(null);
                factory = (BiFunction<URL[], ClassLoader, URLClassLoader>) factoryField.get(null);
            } catch (Throwable ignored) {}
        }

        if (mapper == null) mapper = Function.identity();
        if (factory == null) factory = (urls, parent) -> new IsolatedInjectableClassLoader(urls, List.of(), parent);
        return new Pair<>(mapper, factory);
    }
}
