/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.lifecycle.VanishedWorldStorage;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.io.IO;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * On-disk persistence for a single engine's {@link IrisEngineData}. Loads are double-checked against
 * the engine's volatile field and serialized on a dedicated lock, and every write goes through an
 * atomic temp-file move so a crash mid-save can never truncate the live engine data.
 */
final class EngineDataStore {
    private static final String ENGINE_DATA_DIRECTORY = "iris/engine-data";
    private final IrisEngine engine;
    private final Object engineDataLock = new Object();
    /**
     * Set once this store has written {@code iris/engine-data} into the world folder. From then on that
     * directory going missing is a delete, whether or not the server has already put the world folder back.
     */
    private volatile boolean engineDataEstablished;

    EngineDataStore(IrisEngine engine) {
        this.engine = engine;
    }

    IrisEngineData getEngineData() {
        IrisEngineData loaded = engine.engineData;
        if (loaded != null) {
            return loaded;
        }
        synchronized (engineDataLock) {
            loaded = engine.engineData;
            if (loaded != null) {
                return loaded;
            }
            File f = engineDataFile();
            if (f.exists()) {
                engineDataEstablished = true;
                try {
                    loaded = new Gson().fromJson(IO.readAll(f), IrisEngineData.class);
                    if (loaded == null) {
                        throw new IllegalStateException("Engine data file contains no JSON object: " + f.getAbsolutePath());
                    }
                } catch (IOException | JsonParseException e) {
                    IrisLogging.reportError(e);
                    throw new IllegalStateException("Failed to read Iris engine data without modifying it: " + f.getAbsolutePath(), e);
                }
            }

            if (loaded == null) {
                loaded = new IrisEngineData();
                loaded.getStatistics().setVersion(IrisPlatforms.get().irisVersionNumber());
                loaded.getStatistics().setMCVersion(IrisPlatforms.get().minecraftVersionNumber());
                loaded.getStatistics().setUpgradedVersion(IrisPlatforms.get().irisVersionNumber());
                if (loaded.getStatistics().getVersion() == -1 || loaded.getStatistics().getMCVersion() == -1) {
                    IrisLogging.error("Failed to setup Engine Data!");
                }
                if (!storageVanished()) {
                    try {
                        writeEngineDataAtomically(f, loaded);
                        engineDataEstablished = true;
                    } catch (IOException e) {
                        IrisLogging.reportError(e);
                        throw new IllegalStateException("Failed to create Iris engine data: " + f.getAbsolutePath(), e);
                    }
                }
            }
            engine.engineData = loaded;
            return loaded;
        }
    }

    /**
     * True when this engine's world storage is gone, which stops persistence rather than letting the write
     * rebuild the tree it is supposed to be writing into.
     * <p>
     * The world folder existing is not enough. A {@code save-all} after a hot delete writes the level's own
     * {@code data/*.dat} files back and recreates the folder, and Iris' {@code WorldSaveEvent} handler runs
     * after that, so a folder check alone lets the save rebuild {@code iris/engine-data} - which is exactly
     * the directory the next boot's storage audit reads as "this is an Iris world whose pack snapshot broke".
     * Once this store has written that directory, its absence is the delete.
     */
    private boolean storageVanished() {
        File worldFolder = engine.getWorld().worldFolder();
        if (!engineDataEstablished) {
            return VanishedWorldStorage.vanished(worldFolder);
        }
        return VanishedWorldStorage.vanished(worldFolder, new File(worldFolder, ENGINE_DATA_DIRECTORY));
    }

    private File engineDataFile() {
        return new File(
                engine.getWorld().worldFolder(),
                ENGINE_DATA_DIRECTORY + "/" + engine.getDimension().getLoadKey() + ".json");
    }

    void saveEngineData() {
        synchronized (engineDataLock) {
            if (storageVanished()) {
                return;
            }
            File f = engineDataFile();
            try {
                writeEngineDataAtomically(f, engine.getEngineData());
                engineDataEstablished = true;
                IrisLogging.debug("Saved Engine Data");
            } catch (IOException e) {
                IrisLogging.error("Failed to save Engine Data");
                IrisLogging.reportError(e);
                throw new IllegalStateException("Failed to save Iris engine data: " + f.getAbsolutePath(), e);
            }
        }
    }

    void releaseEngineData() {
        IrisData data = engine.getData();
        data.unregisterEngine(engine);
        if (data.getEngines().isEmpty()) {
            data.close();
            data.clearLists();
        }
    }

    static void writeEngineDataAtomically(File file, IrisEngineData data) throws IOException {
        Path output = file.toPath();
        Path parent = output.getParent();
        if (parent == null) {
            throw new IOException("Engine data path has no parent: " + output);
        }
        // <worldFolder>/iris/engine-data/<key>.json: the world folder itself is never created here, so a
        // deleted world cannot be rebuilt by a save that races the guard in saveEngineData.
        Path irisRoot = parent.getParent();
        Path worldFolder = irisRoot == null ? null : irisRoot.getParent();
        if (worldFolder == null || !Files.isDirectory(worldFolder, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Iris world storage is missing: " + worldFolder);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, output.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, new Gson().toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
