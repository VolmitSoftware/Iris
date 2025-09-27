package com.volmit.iris.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.misc.ServerProperties;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

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
        readBukkitWorlds().forEach(this::put0);
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
        put0(name, type);
        save();
    }

    private void put0(String name, String type) {
        String old = worlds.put(name, type);
        if (!type.equals(old))
            dirty = true;
    }

    public KMap<String, String> getWorlds() {
        clean();
        return readBukkitWorlds().put(worlds);
    }

    public Stream<IrisData> getPacks() {
        return getDimensions()
                .map(IrisDimension::getLoader)
                .filter(Objects::nonNull);
    }

    public Stream<IrisDimension> getDimensions() {
        return getWorlds()
                .entrySet()
                .stream()
                .map(entry -> Iris.loadDimension(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull);
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

    public static KMap<String, String> readBukkitWorlds() {
        var bukkit = YamlConfiguration.loadConfiguration(ServerProperties.BUKKIT_YML);
        var worlds = bukkit.getConfigurationSection("worlds");
        if (worlds == null) return new KMap<>();

        var result = new KMap<String, String>();
        for (String world : worlds.getKeys(false)) {
            var gen = worlds.getString(world + ".generator");
            if (gen == null) continue;

            String loadKey;
            if (gen.equalsIgnoreCase("iris")) {
                loadKey = IrisSettings.get().getGenerator().getDefaultWorldType();
            } else if (gen.startsWith("Iris:")) {
                loadKey = gen.substring(5);
            } else continue;

            result.put(world, loadKey);
        }

        return result;
    }
}
