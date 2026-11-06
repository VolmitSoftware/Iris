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

package art.arcane.iris.core.tooling;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisValidationContext;
import art.arcane.iris.spi.IrisPlatforms;
import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import com.volmit.tooling.ObjectCodec;
import com.volmit.tooling.PackEdit;
import com.volmit.tooling.ToolPack;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * One pack as a command of one module sees it. An edit holds the pack's {@link IrisData}, which a hotload also takes, so
 * a hotload waits for the edit and a second edit waits for the first. Built-in modules that still read Iris's own pack
 * data reach it through {@link #dataOf(ToolPack)}.
 */
public final class IrisToolPack implements ToolPack {
    static final String EXPORTS_FOLDER = "exports";

    private final IrisData data;
    private final ModuleLog log;
    private final ObjectCodec objects = new IrisObjectCodec();

    public IrisToolPack(IrisData data, ModuleLog log) {
        this.data = Objects.requireNonNull(data, "data");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** The pack data behind {@code pack}, for built-in modules compiled into Iris. */
    public static IrisData dataOf(ToolPack pack) {
        if (!(pack instanceof IrisToolPack irisPack)) {
            throw new IllegalArgumentException("Built-in tools run on packs Iris serves, not " + pack);
        }
        return irisPack.data;
    }

    @Override
    public PackResources resources() {
        return data.getPackResources();
    }

    @Override
    public PackSettings settings() {
        return IrisValidationContext.forPack(data).settings();
    }

    @Override
    public <T> T edit(PackEdit<T> edit) throws IOException {
        synchronized (data) {
            T result = edit.apply(data.getDataFolder().toPath());
            data.invalidateStructureResources();
            return result;
        }
    }

    /** The workspace's {@code exports} folder, one folder for each pack, beside the packs themselves. */
    @Override
    public Path outputFolder() {
        File packsFolder = IrisPlatforms.get().packsFolder();
        Path output = packsFolder.toPath().resolve(EXPORTS_FOLDER).resolve(data.getDataFolder().getName());
        try {
            return Files.createDirectories(output).toAbsolutePath().normalize();
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot create the export folder " + output, failure);
        }
    }

    @Override
    public ObjectCodec objects() {
        return objects;
    }

    @Override
    public ModuleLog log() {
        return log;
    }

    @Override
    public String toString() {
        return "pack " + data.getDataFolder().getName();
    }
}
