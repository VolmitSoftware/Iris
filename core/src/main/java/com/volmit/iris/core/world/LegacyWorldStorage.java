package com.volmit.iris.core.world;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.misc.ServerProperties;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class LegacyWorldStorage implements WorldStorage {
    public static final LegacyWorldStorage INSTANCE = new LegacyWorldStorage();
    private static final List<String> SERVER_WIDE_MAIN_WORLD_FOLDERS = List.of("datapacks", "playerdata", "advancements", "stats");

    @Override
    public File worldFolder(String worldName) {
        return new File(Bukkit.getWorldContainer(), worldName);
    }

    @Override
    public KList<File> datapackFolders() {
        KList<File> folders = new KList<>();
        for (World world : Bukkit.getWorlds()) {
            folders.add(new File(world.getWorldFolder(), "datapacks"));
        }
        if (folders.isEmpty()) {
            folders.add(new File(worldFolder(ServerProperties.LEVEL_NAME), "datapacks"));
        }
        return folders;
    }

    @Override
    public void makeMainWorld(String worldName) throws IOException {
        var data = ServerProperties.DATA;
        try (var in = new FileInputStream(ServerProperties.SERVER_PROPERTIES)) {
            data.load(in);
        }
        for (String sub : SERVER_WIDE_MAIN_WORLD_FOLDERS) {
            IO.copyDirectory(new File(worldFolder(ServerProperties.LEVEL_NAME), sub).toPath(), new File(worldFolder(worldName), sub).toPath());
        }

        data.setProperty("level-name", worldName);
        try (var out = new FileOutputStream(ServerProperties.SERVER_PROPERTIES)) {
            data.store(out, null);
        }
    }
}
