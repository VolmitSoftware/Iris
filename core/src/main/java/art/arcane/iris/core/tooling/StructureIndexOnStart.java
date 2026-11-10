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
import art.arcane.iris.core.modules.IrisModuleHost;
import art.arcane.iris.core.structure.authoring.StructureIndexService;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import com.volmit.tooling.PlatformStructures;

import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Writes a pack's structure index once, when the first engine of the pack starts and the platform can list its structures. */
public final class StructureIndexOnStart {
    private static final Set<String> GENERATED = ConcurrentHashMap.newKeySet();

    private StructureIndexOnStart() {
    }

    /** The index file; null when it was written before, or the platform does not list its structures yet, or writing failed. */
    public static File writeOnce(IrisData data) {
        PlatformStructures platform = IrisServices.getOrNull(PlatformStructures.class);
        if (data == null || platform == null) {
            return null;
        }

        File dataFolder = data.getDataFolder();
        String key = dataFolder == null ? null : dataFolder.getAbsolutePath();
        if (key == null || !GENERATED.add(key)) {
            return null;
        }

        try {
            return StructureIndexService.write(new IrisToolPack(data, IrisModuleHost.hostLog()), platform);
        } catch (Throwable e) {
            GENERATED.remove(key);
            IrisLogging.reportError(e);
            return null;
        }
    }
}
