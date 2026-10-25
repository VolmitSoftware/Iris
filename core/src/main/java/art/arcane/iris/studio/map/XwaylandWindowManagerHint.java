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

package art.arcane.iris.studio.map;

import com.sun.jna.platform.linux.LibC;
import art.arcane.iris.spi.IrisLogging;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java does not recognise the non-reparenting Xwayland window managers of compositors such as niri, sway and Hyprland.
 * It waits for a reparent that never comes and paints only a corner of each window. Java reads this hint from the
 * native environment once, at the first decorated window, so it is set through libc before Iris opens any window.
 */
final class XwaylandWindowManagerHint {
    private static final String NON_REPARENTING_VARIABLE = "_JAVA_AWT_WM_NONREPARENTING";
    private static final int KEEP_EXISTING_VALUE = 0;
    private static final List<String> REPARENTING_DESKTOPS = List.of("GNOME", "KDE");
    private static final AtomicBoolean APPLIED = new AtomicBoolean();

    private XwaylandWindowManagerHint() {
    }

    static void apply() {
        if (!APPLIED.compareAndSet(false, true) || !isNeeded(System.getenv(), System.getProperty("os.name", ""))) {
            return;
        }

        try {
            if (LibC.INSTANCE.setenv(NON_REPARENTING_VARIABLE, "1", KEEP_EXISTING_VALUE) != 0) {
                warnUnapplied();
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            warnUnapplied();
        }
    }

    static boolean isNeeded(Map<String, String> environment, String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("linux")
                && isSet(environment, "WAYLAND_DISPLAY")
                && isSet(environment, "DISPLAY")
                && !isSet(environment, NON_REPARENTING_VARIABLE)
                && !isReparentingDesktop(environment.get("XDG_CURRENT_DESKTOP"));
    }

    private static boolean isReparentingDesktop(String currentDesktop) {
        if (currentDesktop == null) {
            return false;
        }

        return Arrays.stream(currentDesktop.split(":"))
                .map(desktop -> desktop.trim().toUpperCase(Locale.ROOT))
                .anyMatch(REPARENTING_DESKTOPS::contains);
    }

    private static boolean isSet(Map<String, String> environment, String variable) {
        String value = environment.get(variable);
        return value != null && !value.isBlank();
    }

    private static void warnUnapplied() {
        IrisLogging.warn("Iris could not adjust Java for this Wayland desktop, so its windows may stay blank. "
                + "Start the server with %s=1 set to fix that.", NON_REPARENTING_VARIABLE);
    }
}
