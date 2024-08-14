/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.format.C;
import org.bukkit.Bukkit;

public class ModesSFG {
    public static void selectMode() {
        if (IrisSafeguard.instance.unstablemode) {
            Iris.safeguard(C.DARK_RED + "Iris is running in Unstable Mode");
            unstable();
        }
        if (IrisSafeguard.instance.warningmode) {
            Iris.safeguard(C.GOLD + "Iris is running in Warning Mode");
            warning();
        }
        if (IrisSafeguard.instance.stablemode) {
            stable();
        }
    }

    public static void stable() {
        Iris.safeguard(C.BLUE + "Iris is running Stable");
    }

    public static void unstable() {

        UtilsSFG.printIncompatibleWarnings();

        if (IrisSafeguard.instance.unstablemode) {
            Iris.info("");
            Iris.info(C.DARK_GRAY + "--==<" + C.RED + " IMPORTANT " + C.DARK_GRAY + ">==--");
            Iris.info(C.RED + "Iris is running in unstable mode which may cause the following issues:");
            Iris.info(C.DARK_RED + "Server Issues");
            Iris.info(C.RED + "- Server won't boot");
            Iris.info(C.RED + "- Data Loss");
            Iris.info(C.RED + "- Unexpected behavior.");
            Iris.info(C.RED + "- And More...");
            Iris.info(C.DARK_RED + "World Issues");
            Iris.info(C.RED + "- Worlds can't load due to corruption.");
            Iris.info(C.RED + "- Worlds may slowly corrupt until they can't load.");
            Iris.info(C.RED + "- World data loss.");
            Iris.info(C.RED + "- And More...");
            Iris.info(C.DARK_RED + "ATTENTION: " + C.RED + "While running Iris in unstable mode, you won't be eligible for support.");
            Iris.info(C.DARK_RED + "CAUSE: " + C.RED + UtilsSFG.MSGIncompatibleWarnings());

            if (IrisSettings.get().getSafeguard().ignoreBootMode) {
                Iris.info(C.DARK_RED + "Boot Unstable is set to true, continuing with the startup process.");
            } else {
                Iris.info(C.DARK_RED + "Go to plugins/iris/settings.json and set DoomsdayAnnihilationSelfDestructMode to true if you wish to proceed.");
                while (true) {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (InterruptedException e) {
                        // no
                    }
                }
            }
            Iris.info("");
        }
    }

    public static void warning() {

        UtilsSFG.printIncompatibleWarnings();

        if (IrisSafeguard.instance.warningmode) {
            Iris.info("");
            Iris.info(C.DARK_GRAY + "--==<" + C.GOLD + " IMPORTANT " + C.DARK_GRAY + ">==--");
            Iris.info(C.GOLD + "Iris is running in warning mode which may cause the following issues:");
            Iris.info(C.YELLOW + "- Data Loss");
            Iris.info(C.YELLOW + "- Errors");
            Iris.info(C.YELLOW + "- Broken worlds");
            Iris.info(C.YELLOW + "- Unexpected behavior.");
            Iris.info(C.YELLOW + "- And perhaps further complications.");
            Iris.info(C.GOLD + "CAUSE: " + C.YELLOW + UtilsSFG.MSGIncompatibleWarnings());
            Iris.info("");
        }
    }
}
