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
import com.volmit.iris.util.format.C;

public class UtilsSFG {
    public static void splash() {
        ModesSFG.selectMode();
    }

    public static void printIncompatibleWarnings() {
        // String SupportedIrisVersion = getDescription().getVersion(); //todo Automatic version

        if (ServerBootSFG.safeguardPassed) {
            Iris.safeguard(C.BLUE + "0 Conflicts found");
        } else {
            if (IrisSafeguard.instance.unstablemode) {
                Iris.safeguard(C.DARK_RED + "" + ServerBootSFG.count + " Conflicts found");
            }
            if (IrisSafeguard.instance.warningmode) {
                Iris.safeguard(C.YELLOW + "" + ServerBootSFG.count + " Conflicts found");
            }

            if (ServerBootSFG.incompatibilities.get("Multiverse-Core")) {
                Iris.safeguard(C.RED + "Multiverse");
                Iris.safeguard(C.RED + "- The plugin Multiverse is not compatible with the server.");
                Iris.safeguard(C.RED + "- If you want to have a world manager, consider using PhantomWorlds or MyWorlds instead.");
            }
            if (ServerBootSFG.incompatibilities.get("dynmap")) {
                Iris.safeguard(C.RED + "Dynmap");
                Iris.safeguard(C.RED + "- The plugin Dynmap is not compatible with the server.");
                Iris.safeguard(C.RED + "- If you want to have a map plugin like Dynmap, consider Bluemap.");
            }
            if (ServerBootSFG.incompatibilities.get("TerraformGenerator") || ServerBootSFG.incompatibilities.get("Stratos")) {
                Iris.safeguard(C.YELLOW + "Terraform Generator / Stratos");
                Iris.safeguard(C.YELLOW + "- Iris is not compatible with other worldgen plugins.");
            }
            if (ServerBootSFG.unsuportedversion) {
                Iris.safeguard(C.RED + "Server Version");
                Iris.safeguard(C.RED + "- Iris only supports 1.19.2 > 1.21.1");
            }
            if (!ServerBootSFG.passedserversoftware) {
                Iris.safeguard(C.YELLOW + "Unsupported Server Software");
                Iris.safeguard(C.YELLOW + "- Please consider using Paper or Purpur instead.");
            }
            if (!ServerBootSFG.hasPrivileges) {
                Iris.safeguard(C.YELLOW + "Insufficient Privileges");
                Iris.safeguard(C.YELLOW + "- The server has insufficient Privileges to run iris. Please contact support.");
            }
            if (!ServerBootSFG.hasEnoughDiskSpace) {
                Iris.safeguard(C.YELLOW + "Insufficient Disk Space");
                Iris.safeguard(C.YELLOW + "- The server has insufficient Free DiskSpace to run iris required 3GB+.");
            }
            if (!ServerBootSFG.isJDK17) {
                Iris.safeguard(C.YELLOW + "Unsupported java version");
                Iris.safeguard(C.YELLOW + "- Please consider using JDK 17 (or 21 for 1.20.6) Instead of JDK " + Iris.getJavaVersion());
            }
            if (ServerBootSFG.isJRE) {
                Iris.safeguard(C.YELLOW + "Unsupported Server JDK");
                Iris.safeguard(C.YELLOW + "- Please consider using JDK 17 (or 21 for 1.20.6) Instead of JRE " + Iris.getJavaVersion());
            }
        }
    }

    public static String MSGIncompatibleWarnings() {
        return ServerBootSFG.allIncompatibilities;
    }
}
