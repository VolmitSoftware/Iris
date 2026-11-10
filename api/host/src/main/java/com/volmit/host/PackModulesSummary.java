package com.volmit.host;

import java.util.List;

/** The one log message a pack load writes: how many modules the pack uses, and each module the pack
 * brings itself with the jar it comes from. The host's modules are the same for every pack, so they
 * are only counted.
 */
final class PackModulesSummary {
    private static final String INDENT = "  ";

    private PackModulesSummary() {}

    static String describe(String packName, List<DiscoveredModule> modules) {
        if (modules.isEmpty()) {
            return "Pack " + packName + " uses no modules: no jars in its " + PackModules.MODULES_DIRECTORY + " directory";
        }
        List<DiscoveredModule> ownModules = modules.stream().filter(module -> !isFromHost(module)).toList();
        int hostModuleCount = modules.size() - ownModules.size();
        var text = new StringBuilder("Pack ").append(packName).append(" uses ").append(countOf(modules.size()))
                .append(": ").append(sourcesOf(hostModuleCount, ownModules.size()));
        for (var module : ownModules) {
            text.append('\n').append(INDENT).append(describe(module));
        }
        return text.toString();
    }

    private static boolean isFromHost(DiscoveredModule module) {
        return module.origin().equals(DiscoveredModule.HOST_CLASSPATH)
                || module.origin().endsWith(PackModules.BUNDLED_ORIGIN_SUFFIX);
    }

    private static String sourcesOf(int hostModuleCount, int ownModuleCount) {
        if (ownModuleCount == 0) {
            return "all from the host";
        }
        if (hostModuleCount == 0) {
            return "all from the pack";
        }
        return hostModuleCount + " from the host, " + ownModuleCount + " from the pack";
    }

    private static String countOf(int moduleCount) {
        return moduleCount + (moduleCount == 1 ? " module" : " modules");
    }

    private static String describe(DiscoveredModule discovered) {
        var info = discovered.info();
        return info.name() + " (" + info.id() + " " + info.version() + ") from " + discovered.origin();
    }
}
