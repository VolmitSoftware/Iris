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
import com.volmit.host.LoadedPack;

import java.io.File;

/**
 * The modules of one pack folder, loaded with {@link IrisModuleHost} the first time something needs
 * them and closed with the pack data that owns them. A failed load is reported once and remembered, so
 * every later use fails with the same error instead of loading the jars again.
 */
public final class PackModuleSet implements AutoCloseable {
    private enum State { NOT_LOADED, LOADED, FAILED, CLOSED }

    private final File packFolder;
    private State state = State.NOT_LOADED;
    private LoadedPack pack;
    private ModuleSettingsDecoder settings;
    private PackModulesException failure;

    public PackModuleSet(File packFolder) {
        this.packFolder = packFolder;
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

    @Override
    public synchronized void close() {
        if (state == State.LOADED) {
            pack.close();
        }
        pack = null;
        settings = null;
        state = State.CLOSED;
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
            LoadedPack loaded = LoadedPack.load(packFolder.toPath(), IrisModuleHost.get());
            pack = loaded;
            settings = new ModuleSettingsDecoder(loaded.catalog(), packFolder.toPath());
            state = State.LOADED;
        } catch (RuntimeException loadFailure) {
            failure = new PackModulesException(packFolder, loadFailure);
            state = State.FAILED;
            IrisLogging.error(failure.getMessage());
            throw failure;
        }
    }
}
