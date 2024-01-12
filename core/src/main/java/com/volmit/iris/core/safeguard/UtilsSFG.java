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
            if (IrisSafeguard.unstablemode) {
                Iris.safeguard(C.DARK_RED + "" + ServerBootSFG.count + " Conflicts found");
            }
            if (IrisSafeguard.warningmode) {
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
                Iris.safeguard(C.RED + "- Iris only supports 1.19.2 > 1.20.2");
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
                Iris.safeguard(C.YELLOW + "- Please consider using JDK 17 Instead of JDK " + Iris.getJavaVersion());
            }
            if (ServerBootSFG.isJRE) {
                Iris.safeguard(C.YELLOW + "Unsupported Server JDK");
                Iris.safeguard(C.YELLOW + "- Please consider using JDK 17 Instead of JRE " + Iris.getJavaVersion());
            }
        }
    }

    public static String MSGIncompatibleWarnings() {
        return ServerBootSFG.allIncompatibilities;
    }
}
