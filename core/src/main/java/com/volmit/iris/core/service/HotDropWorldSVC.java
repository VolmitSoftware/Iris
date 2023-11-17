package com.volmit.iris.core.service;

import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.volmit.iris.Iris;
import com.volmit.iris.util.SFG.WorldHandlerSFG;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.Looper;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class HotDropWorldSVC implements IrisService {
    private WatchService watchService;
    private JavaPlugin plugin;
    public Looper ticker;

    @Override
    public void onEnable() {
        this.plugin = Iris.instance;
        initializeWatchService();
    }

    @Override
    public void onDisable() {
        ticker.interrupt();
    }

    private void initializeWatchService() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            Path path = Paths.get(Bukkit.getWorldContainer().getAbsolutePath());
            path.register(watchService, ENTRY_CREATE);
            this.startLoop();
            ticker.start();
        } catch (Exception e) {
            Iris.reportError(e);
            e.printStackTrace();
        }
    }

    public void startLoop() {
        final JavaPlugin finalPlugin = this.plugin;
        ticker = new Looper() {
            @Override
            protected long loop() {
                WatchKey key;
                try {
                    key = watchService.poll();
                    if (key != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();

                            if (kind == ENTRY_CREATE) {
                                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                                Path filename = ev.context();

                                File newDir = new File(Bukkit.getWorldContainer(), filename.toString());
                                File irisFolder = new File(newDir, "iris");
                                if (irisFolder.exists() && irisFolder.isDirectory()) {
                                    Iris.info("World HotDrop Detected!");
                                    String worldName = newDir.getName();
                                    String version = getVersionFromIrisFolder(irisFolder);

                                    if (Bukkit.getWorld(worldName) == null && isPackValid(worldName, version)) {
                                        Bukkit.getScheduler().runTask(finalPlugin, () -> WorldHandlerSFG.LoadWorld(worldName));
                                    }
                                }
                            }
                        }
                        key.reset();
                    }
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                    return -1;
                }

                return 1000;
            }
        };
    }

    private String getVersionFromIrisFolder(File irisFolder) {
        File versionFile = new File(irisFolder, "some_version_file.json");

        if (versionFile.exists() && versionFile.isFile()) {
            try (FileReader reader = new FileReader(versionFile)) {
                JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
                if (jsonObject.has("version")) {
                    return jsonObject.get("version").getAsString();
                }
            } catch (IOException | JsonParseException e) {
                Iris.reportError(e);
                e.printStackTrace();
            }
        }

        return "???";
    }

    private boolean isPackValid(String worldPackName, String version) {
        try {
            File packFolder = Iris.service(StudioSVC.class).getWorkspaceFolder();
            File[] serverPacks = packFolder.listFiles(File::isDirectory);
            if (serverPacks != null) {
                for (File serverPack : serverPacks) {
                    String serverPackName = serverPack.getName();
                    String serverPackVersion = getPackVersion(serverPack);

                    if (serverPackName.equals(worldPackName)) {
                        if (serverPackVersion.equals(version)) {
                            return true;
                        } else {
                            Iris.info("Version mismatch for pack '" + worldPackName + "'. Expected: " + serverPackVersion + ", Found: " + version);
                            Iris.info(C.GOLD + "Cant load the world!");
                            return false;
                        }
                    }
                }
                Iris.info("Pack '" + worldPackName + "' not found on the server.");
                Iris.info(C.GOLD + "Cant load the world!");
            } else {
                Iris.info("No packs found in the server's workspace folder.");
            }
        } catch (Exception e) {
            Iris.reportError(e);
            e.printStackTrace();
            Iris.info("Error checking if pack is valid: " + e.getMessage());
        }
        return false;
    }

    private String getPackVersion(File pack) {
        String version = "???";
        File dimensionFile = new File(pack, "dimensions/" + pack.getName() + ".json");
        if (dimensionFile.isFile()) {
            try (FileReader reader = new FileReader(dimensionFile)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("version")) {
                    version = json.get("version").getAsString();
                }
            } catch (IOException | JsonParseException e) {
                Iris.reportError(e);
                e.printStackTrace();
            }
        }
        return version;
    }
}

