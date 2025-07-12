package com.volmit.iris.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.io.IO;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.stream.Stream;

public class IrisWorlds {
    private static final AtomicCache<IrisWorlds> cache = new AtomicCache<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = TypeToken.getParameterized(KMap.class, String.class, String.class).getType();
    private final KMap<String, String> worlds;
    private volatile boolean dirty = false;

    private IrisWorlds(KMap<String, String> worlds) {
        this.worlds = worlds;
        save();
    }

    public static IrisWorlds get() {
        return cache.aquire(() -> {
            File file = Iris.instance.getDataFile("worlds.json");
            if (!file.exists()) {
                return new IrisWorlds(new KMap<>());
            }

            try {
                String json = IO.readAll(file);
                KMap<String, String> worlds = GSON.fromJson(json, TYPE);
                return new IrisWorlds(Objects.requireNonNullElseGet(worlds, KMap::new));
            } catch (Throwable e) {
                Iris.error("Failed to load worlds.json!");
                e.printStackTrace();
                Iris.reportError(e);
            }

            return new IrisWorlds(new KMap<>());
        });
    }

    public void put(String name, String type) {
        String old = worlds.put(name, type);
        if (!type.equals(old))
            dirty = true;
        save();
    }

    public Stream<File> getFolders() {
        return worlds.keySet().stream().map(k -> new File(Bukkit.getWorldContainer(), k));
    }

    public void clean() {
        dirty = worlds.entrySet().removeIf(entry -> !new File(Bukkit.getWorldContainer(), entry.getKey() + "/iris/pack/dimensions/" + entry.getValue() + ".json").exists());
    }

    public synchronized void save() {
        clean();
        if (!dirty) return;
        try {
            IO.write(Iris.instance.getDataFile("worlds.json"), OutputStreamWriter::new, writer -> GSON.toJson(worlds, TYPE, writer));
            dirty = false;
        } catch (IOException e) {
            Iris.error("Failed to save worlds.json!");
            e.printStackTrace();
            Iris.reportError(e);
        }
    }
}
