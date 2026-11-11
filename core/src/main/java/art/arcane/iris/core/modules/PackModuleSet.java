/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.modules;

import art.arcane.iris.core.modules.manifest.ModuleUpdateReport;
import art.arcane.iris.core.modules.manifest.PackModuleManifest;
import art.arcane.iris.core.modules.manifest.ShippedModules;
import art.arcane.iris.spi.IrisLogging;
import com.google.gson.Gson;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleLoadException;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * The modules of one pack folder, loaded with {@link IrisModuleHost} the first time something needs
 * them and closed with the pack data that owns them. Before a load, the jars are brought up to what the
 * pack's {@code modules.json} asks for and Iris ships ({@link ManagedPackModules}). A failed load is reported
 * once and remembered, so every later use fails with the same error instead of loading the jars again.
 * Studio can load them again after the jars in {@code <pack>/modules} change.
 */
public final class PackModuleSet implements AutoCloseable {
    private enum State { NOT_LOADED, LOADED, FAILED, CLOSED }

    static final String MODULES_FOLDER = "modules";
    private static final String JAR_EXTENSION = ".jar";

    private final File packFolder;
    private final Supplier<Gson> irisGson;
    private final ManagedPackModules managed;
    private State state = State.NOT_LOADED;
    /** Set only while loaded; volatile so {@link #pack()}, asked on the generation hot path, needs no lock then. */
    private volatile LoadedPack pack;
    private ModuleSettingsDecoder settings;
    private PackModulesException failure;
    private List<String> loadedJars = List.of();
    private ModuleUpdateReport lastUpdates = new ModuleUpdateReport(false, List.of(), List.of());

    /** @param irisGson the pack's own Gson, which reads the Iris classes built-in settings hold */
    public PackModuleSet(File packFolder, Supplier<Gson> irisGson) {
        this(packFolder, irisGson, ShippedModules.bundled());
    }

    /** @param shipped the module jars to install and update the pack's from */
    PackModuleSet(File packFolder, Supplier<Gson> irisGson, ShippedModules shipped) {
        this.packFolder = packFolder;
        this.irisGson = irisGson;
        this.managed = new ManagedPackModules(packFolder, shipped);
    }

    /** The loaded pack, its built-in modules included. */
    public LoadedPack pack() {
        LoadedPack loaded = pack;
        if (loaded != null) {
            return loaded;
        }
        return loadedPack();
    }

    private synchronized LoadedPack loadedPack() {
        requireLoaded();
        return pack;
    }

    public synchronized ModuleSettingsDecoder settings() {
        requireLoaded();
        return settings;
    }

    /**
     * Forgets the loaded modules, or the failure to load them, when the pack's module jars changed since,
     * so the next use loads the jars as they are now. Only for studio: anything built from the old modules,
     * such as resources read with their settings, must be reloaded too.
     *
     * @return whether the jars changed
     */
    public synchronized boolean reloadIfJarsChanged() {
        if (state == State.NOT_LOADED || state == State.CLOSED || jarFingerprint().equals(loadedJars)) {
            return false;
        }
        release(State.NOT_LOADED);
        return true;
    }

    /**
     * Updates the pack's jars to what Iris ships, new minor and major versions included. Whatever loaded the pack keeps
     * the modules it loaded until the server restarts.
     */
    public synchronized ModuleUpdateReport takeEveryUpdate() {
        lastUpdates = managed.takeEveryUpdate();
        return lastUpdates;
    }

    /** What the last update of the pack's jars did and what it kept: at the last load, or at {@link #takeEveryUpdate()} since. */
    public synchronized ModuleUpdateReport lastUpdates() {
        return lastUpdates;
    }

    /** "World survival" for a world's copy of a pack, "Pack overworld" for a pack folder. */
    public String label() {
        return managed.label();
    }

    @Override
    public synchronized void close() {
        release(State.CLOSED);
    }

    private void release(State next) {
        if (state == State.LOADED) {
            pack.close();
        }
        pack = null;
        settings = null;
        failure = null;
        loadedJars = List.of();
        state = next;
    }

    private void requireLoaded() {
        switch (state) {
            case NOT_LOADED -> load();
            case FAILED -> throw failure;
            case CLOSED -> throw new IllegalStateException("The modules of pack " + packFolder.getName() + " are closed");
            case LOADED -> {
            }
        }
    }

    private void load() {
        try {
            lastUpdates = managed.bringUpToDate();
            loadedJars = jarFingerprint();
            LoadedPack loaded = LoadedPack.load(packFolder.toPath(), IrisModuleHost.get());
            settings = new ModuleSettingsDecoder(loaded.catalog(), packFolder.toPath(), irisGson);
            pack = loaded;
            state = State.LOADED;
            managed.recordLoaded(loaded.packModules());
        } catch (ModuleLoadException loadFailure) {
            fail(new PackModulesException(packFolder, loadFailure));
        } catch (PackModuleManifest.ManifestFormatException brokenManifest) {
            fail(new PackModulesException(packFolder, brokenManifest));
        }
    }

    private void fail(PackModulesException loadFailure) {
        loadedJars = jarFingerprint();
        failure = loadFailure;
        state = State.FAILED;
        IrisLogging.error(failure.getMessage());
        throw failure;
    }

    /** Each module jar's name, size and modification time, in name order. */
    private List<String> jarFingerprint() {
        File[] jars = new File(packFolder, MODULES_FOLDER).listFiles(file -> file.isFile() && file.getName().endsWith(JAR_EXTENSION));
        if (jars == null) {
            return List.of();
        }
        return Arrays.stream(jars)
                .sorted(Comparator.comparing(File::getName))
                .map(jar -> jar.getName() + ":" + jar.length() + ":" + jar.lastModified())
                .toList();
    }
}
