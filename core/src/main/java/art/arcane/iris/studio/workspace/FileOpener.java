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

package art.arcane.iris.studio.workspace;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Opens a file or folder in the program the desktop has for it. */
public final class FileOpener {
    private FileOpener() {
    }

    /** False on a machine without a display, such as a Linux server with no X or Wayland session. */
    public static boolean isDesktopAvailable() {
        String system = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (system.contains("win") || system.contains("mac")) {
            return true;
        }
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;
    }

    /** @throws IOException when the desktop's opener could not be started */
    public static void open(File file) throws IOException {
        new ProcessBuilder(openerCommand(file.getAbsolutePath())).inheritIO().start();
    }

    private static List<String> openerCommand(String path) {
        String system = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (system.contains("win")) {
            return List.of("cmd", "/c", "start", "", path);
        }
        return List.of(system.contains("mac") ? "open" : "xdg-open", path);
    }
}
