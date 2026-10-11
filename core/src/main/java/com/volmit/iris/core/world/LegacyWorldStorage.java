package com.volmit.iris.core.world;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.misc.ServerProperties;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;

public class LegacyWorldStorage implements WorldStorage {
    public static final LegacyWorldStorage INSTANCE = new LegacyWorldStorage();

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
}
