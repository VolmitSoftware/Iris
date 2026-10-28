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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.modules.objects.ForwardingTerrain;
import com.volmit.ModuleLog;
import com.volmit.placement.PlacementTerrain;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * A terrain that logs the questions a placement asks of it, for the chunks the golden-debug switch names: the
 * {@code iris.goldendebug} system property, or else {@code plugins/Iris/goldendebug.txt}, holding
 * {@code chunkX,chunkZ[,radius]}.
 */
final class GoldenDebugTerrain extends ForwardingTerrain {
    private static final String SWITCH_PROPERTY = "iris.goldendebug";
    private static final File SWITCH_FILE = new File("plugins/Iris/goldendebug.txt");
    private static final int[] TARGET = parseTarget(readSwitch());

    private final String tag;
    private final ModuleLog log;

    GoldenDebugTerrain(PlacementTerrain delegate, String tag, ModuleLog log) {
        super(delegate);
        this.tag = tag;
        this.log = log;
    }

    /** Whether the switch names the chunk. */
    static boolean isGoldenDebugChunk(int chunkX, int chunkZ) {
        return TARGET != null
                && Math.abs(TARGET[0] - chunkX) <= TARGET[2]
                && Math.abs(TARGET[1] - chunkZ) <= TARGET[2];
    }

    @Override
    public int highest(int x, int z, boolean ignoreFluid) {
        int result = delegate().highest(x, z, ignoreFluid);
        log.debug("Goldendebug query: tag=" + tag + " getHighest(" + x + "," + z + ",ignoreFluid=" + ignoreFluid + ")=" + result);
        return result;
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        boolean result = delegate().isCarved(x, y, z);
        log.debug("Goldendebug query: tag=" + tag + " isCarved(" + x + "," + y + "," + z + ")=" + result);
        return result;
    }

    private static String readSwitch() {
        String property = System.getProperty(SWITCH_PROPERTY);
        if (property != null && !property.isBlank()) {
            return property;
        }
        if (!SWITCH_FILE.isFile()) {
            return null;
        }
        try {
            return Files.readString(SWITCH_FILE.toPath()).trim();
        } catch (IOException | SecurityException unreadable) {
            return null;
        }
    }

    private static int[] parseTarget(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 2 && parts.length != 3) {
            return null;
        }
        try {
            int radius = parts.length == 3 ? Integer.parseInt(parts[2].trim()) : 0;
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), radius};
        } catch (NumberFormatException malformed) {
            return null;
        }
    }
}
