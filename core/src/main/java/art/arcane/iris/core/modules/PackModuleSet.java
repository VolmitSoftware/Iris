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

import art.arcane.iris.spi.IrisLogging;
import com.google.gson.Gson;
import com.volmit.host.LoadedPack;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * The modules of one pack folder, loaded with {@link IrisModuleHost} the first time something needs
 * them and closed with the pack data that owns them. A failed load is reported once and remembered, so
 * every later use fails with the same error instead of loading the jars again. Studio can load them
 * again after the jars in {@code <pack>/modules} change.
 */
public final class PackModuleSet implements AutoCloseable {
    private enum State { NOT_LOADED, LOADED, FAILED, CLOSED }

    static final String MODULES_FOLDER = "modules";
    private static final String JAR_EXTENSION = ".jar";
    private static final String MISSING_BUILT_INS_KEY = "missing-built-in-modules:";

    private final File packFolder;
    private final Supplier<Gson> irisGson;
    private State state = State.NOT_LOADED;
    private LoadedPack pack;
    private ModuleSettingsDecoder settings;
    private PackModulesException failure;
    private List<String> loadedJars = List.of();

    /** @param irisGson the pack's own Gson, which reads the Iris classes built-in settings hold */
    public PackModuleSet(File packFolder, Supplier<Gson> irisGson) {
        this.packFolder = packFolder;
        this.irisGson = irisGson;
    }

    /** The loaded pack, its built-in modules included. */
    public synchronized LoadedPack pack() {
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
        loadedJars = jarFingerprint();
        try {
            LoadedPack loaded = LoadedPack.load(packFolder.toPath(), IrisModuleHost.get());
            pack = loaded;
            settings = new ModuleSettingsDecoder(loaded.catalog(), packFolder.toPath(), irisGson);
            state = State.LOADED;
            warnWhenBuiltInModulesAreMissing(loaded);
        } catch (RuntimeException loadFailure) {
            failure = new PackModulesException(packFolder, loadFailure);
            state = State.FAILED;
            IrisLogging.error(failure.getMessage());
            throw failure;
        }
    }

    private void warnWhenBuiltInModulesAreMissing(LoadedPack loaded) {
        MissingBuiltInModules.warning(packFolder, loaded.packModules())
                .ifPresent(warning -> IrisLogging.warnOnce(MISSING_BUILT_INS_KEY + packFolder.getAbsolutePath(), "%s", warning));
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
