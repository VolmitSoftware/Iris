package com.volmit.iris.core.scripting;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.io.IO;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

@UtilityClass
public class ExecutionEnvironment {
    private static final String BASE_URL = "https://jitpack.io/com/github/VolmitSoftware/Iris-Scripts/%s/Iris-Scripts-%s-all.jar";
    private static final Provider PROVIDER = ServiceLoader.load(Provider.class, buildLoader())
            .findFirst()
            .orElseThrow();

    @NonNull
    public static Engine createEngine(@NonNull com.volmit.iris.engine.framework.Engine engine) {
        return PROVIDER.createEngine(engine);
    }

    @NonNull
    public static Pack createPack(@NonNull IrisData data) {
        return PROVIDER.createPack(data);
    }

    @NonNull
    public static Simple createSimple() {
        return PROVIDER.createSimple();
    }

    @SneakyThrows
    private static URLClassLoader buildLoader() {
        String version = "46d271c6ce";
        String url = BASE_URL.formatted(version, version);
        String hash = IO.hash("Iris-Scripts.jar@" + version);
        var file = Iris.instance.getDataFile("cache", hash.substring(0, 2), hash.substring(3, 5), hash + ".jar");
        var libsDir = new File(file.getParentFile(), "libs");

        KList<String> libs = null;
        if (!file.exists()) {
            libsDir.mkdirs();

            Iris.info("Downloading Script Engine...");
            var tempFile = Iris.getNonCachedFile(UUID.randomUUID().toString(), url);
            try (var jar = new JarFile(tempFile); var out = new JarOutputStream(new FileOutputStream(file)) ) {
                libs = getLibraries(jar);
                for (var it = jar.entries().asIterator(); it.hasNext(); ) {
                    var entry = it.next();
                    if (entry.isDirectory()) {
                        out.putNextEntry(entry);
                        out.closeEntry();
                        continue;
                    }

                    try (var in = jar.getInputStream(entry)) {
                        if (libs.contains(entry.getName())) {
                            var target = new File(libsDir, entry.getName());
                            target.getParentFile().mkdirs();
                            Files.copy(in, target.toPath());
                            continue;
                        }
                        out.putNextEntry(entry);
                        IO.copy(in, out);
                        out.closeEntry();
                    }
                }
            }
            IO.deleteUp(tempFile);
            Iris.info("Downloaded Script Engine!");
        }

        if (libs == null) {
            try (var jar = new JarFile(file)) {
                libs = getLibraries(jar);
            }
        }
        var urls = new URL[libs.size() + 1];
        urls[0] = file.toURI().toURL();
        for (int i = 0; i < libs.size(); i++) {
            File lib = new File(libsDir, libs.get(i));
            if (!lib.exists()) {
                Iris.warn("Missing library: " + lib.getAbsolutePath());
                continue;
            }

            urls[i + 1] = lib.toURI().toURL();
        }
        return new URLClassLoader(urls, Provider.class.getClassLoader());
    }

    private static KList<String> getLibraries(JarFile jar) throws IOException {
        return new KList<>(jar.getManifest()
                .getMainAttributes()
                .getValue("Libraries")
                .split(";"));
    }

    public interface Provider {
        @NonNull
        Engine createEngine(@NonNull com.volmit.iris.engine.framework.Engine engine);

        @NonNull
        Pack createPack(@NonNull IrisData data);

        @NonNull
        Simple createSimple();
    }


    public interface Simple {
        void execute(@NonNull String script);

        void execute(@NonNull String script, @NonNull Class<?> type, @Nullable Map<@NonNull String, Object> vars);

        @Nullable
        Object evaluate(@NonNull String script);

        @Nullable
        Object evaluate(@NonNull String script, @NonNull Class<?> type, @Nullable Map<@NonNull String, Object> vars);

        default void close() {

        }
    }

    public interface Pack extends Simple {
        @NonNull
        IrisData getData();

        void buildProject();
    }

    public interface Engine extends Pack {
        @NonNull
        com.volmit.iris.engine.framework.Engine getEngine();

        @Nullable
        Object spawnMob(@NonNull String script, @NonNull Location location);

        void postSpawnMob(@NonNull String script, @NonNull Location location, @NonNull Entity mob);

        void preprocessObject(@NonNull String script, @NonNull IrisRegistrant object);
    }
}
