package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.util.agent.Agent;
import com.volmit.iris.util.format.C;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UtilsSFG {
    public static void splash() {
        ModesSFG.selectMode();
    }

    public static void printIncompatibleWarnings() {
        String[] parts = Iris.instance.getDescription().getVersion().split("-");
        String minVersion = parts[1];
        String maxVersion = parts[2];

        if (ServerBootSFG.safeguardPassed) {
            Iris.safeguard(C.BLUE + "0 Conflicts found");
        } else {
            if (IrisSafeguard.unstablemode) {
                Iris.safeguard(C.DARK_RED + "" + ServerBootSFG.count + " Conflicts found");
            }
            if (IrisSafeguard.warningmode) {
                Iris.safeguard(C.YELLOW + "" + ServerBootSFG.count + " Conflicts found");
            }

            if (ServerBootSFG.incompatibilities.get("dynmap")) {
                Iris.safeguard(C.RED + "Dynmap");
                Iris.safeguard(C.RED + "- The plugin Dynmap is not compatible with the server.");
                Iris.safeguard(C.RED + "- If you want to have a map plugin like Dynmap, consider Bluemap.");
            }
            if (ServerBootSFG.incompatibilities.get("Stratos")) {
                Iris.safeguard(C.YELLOW + "Stratos");
                Iris.safeguard(C.YELLOW + "- Iris is not compatible with other worldgen plugins.");
            }
            if (ServerBootSFG.unsuportedversion) {
                Iris.safeguard(C.RED + "Server Version");
                Iris.safeguard(C.RED + "- Iris only supports " + minVersion + " > " + maxVersion);
            }
            if (ServerBootSFG.unsafeUpdate) {
                Iris.safeguard(C.RED + "Unsafe Update");
                Function<int[], String> regex = ii -> Arrays.stream(ii)
                        .mapToObj(String::valueOf)
                        .collect(Collectors.joining("."));

                for (var world : ServerBootSFG.unsafeWorldUpdate.entrySet()) {
                    String version = regex.apply(world.getValue());
                    String worldName = world.getKey().getName();
                    Iris.safeguard(C.RED + "- World: " + C.WHITE + worldName + C.GRAY + " (v" + version + ")");
                }
            }
            if (ServerBootSFG.missingDimensionTypes) {
                Iris.safeguard(C.RED + "Dimension Types");
                Iris.safeguard(C.RED + "- Required Iris dimension types were not loaded.");
                Iris.safeguard(C.RED + "- If this still happens after a restart please contact support.");
            }
            if (ServerBootSFG.missingAgent) {
                Iris.safeguard(C.RED + "Java Agent");
                Iris.safeguard(C.RED + "- Please enable dynamic agent loading by adding -XX:+EnableDynamicAgentLoading to your jvm arguments.");
                Iris.safeguard(C.RED + "- or add the jvm argument -javaagent:" + Agent.AGENT_JAR.getPath());
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
            if (!ServerBootSFG.isCorrectJDK) {
                Iris.safeguard(C.YELLOW + "Unsupported java version");
                Iris.safeguard(C.YELLOW + "- Please consider using JDK 21 Instead of JDK " + Iris.getJavaVersion());
            }
            if (ServerBootSFG.isJRE) {
                Iris.safeguard(C.YELLOW + "Unsupported Server JDK");
                Iris.safeguard(C.YELLOW + "- Please consider using JDK 21 Instead of JRE " + Iris.getJavaVersion());
            }
        }
    }

    public static String MSGIncompatibleWarnings() {
        return ServerBootSFG.allIncompatibilities;
    }
}
