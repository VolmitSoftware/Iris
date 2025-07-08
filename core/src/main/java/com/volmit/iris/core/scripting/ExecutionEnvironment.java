package com.volmit.iris.core.scripting;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.util.io.IO;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.*;

@UtilityClass
public class ExecutionEnvironment {
    private static final String VERSION = System.getProperty("iris.scriptVersion", "master-ffbf167eba-1");
    private static final String BASE_URL = "https://jitpack.io/com/github/VolmitSoftware/Iris-Scripts/" + VERSION + "/Iris-Scripts-" + VERSION + "-all.jar";
    private static final Provider PROVIDER = ServiceLoader.load(Provider.class, buildLoader())
            .findFirst()
            .orElseThrow()
            .init(Iris.instance.getDataFolder("cache", "libraries").toPath());

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
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
            var resolved = client.send(HttpRequest.newBuilder(URI.create(BASE_URL)).build(), HttpResponse.BodyHandlers.discarding())
                    .request();
            String hash = IO.hash(resolved.uri().getPath());
            File file = Iris.instance.getDataFile("cache", hash.substring(0, 2), hash.substring(3, 5), hash + ".jar");
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                Iris.info("Downloading Script Engine...");
                client.send(resolved, HttpResponse.BodyHandlers.ofFile(file.toPath()));
                Iris.info("Downloaded Script Engine!");
            }
            return new URLClassLoader(new URL[]{file.toURI().toURL()}, Provider.class.getClassLoader());
        }
    }

    public interface Provider {
        Provider init(Path localRepository);

        @NonNull
        Engine createEngine(@NonNull com.volmit.iris.engine.framework.Engine engine);

        @NonNull
        Pack createPack(@NonNull IrisData data);

        @NonNull
        Simple createSimple();
    }


    public interface Simple {
        void configureProject(@NonNull File projectDir);

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
