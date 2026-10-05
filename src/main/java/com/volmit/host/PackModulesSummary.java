package com.volmit.host;

import java.util.List;

/** The one log message a pack load writes: which modules the pack uses and which jar each comes
 * from.
 */
final class PackModulesSummary {
    private static final String INDENT = "  ";

    private PackModulesSummary() {}

    static String describe(String packName, List<DiscoveredModule> modules) {
        if (modules.isEmpty()) {
            return "Pack " + packName + " uses no modules: no jars in its " + PackModules.MODULES_DIRECTORY + " directory";
        }
        var text = new StringBuilder("Pack ").append(packName).append(" uses ").append(modules.size())
                .append(modules.size() == 1 ? " module:" : " modules:");
        for (var module : modules) {
            text.append('\n').append(INDENT).append(describe(module));
        }
        return text.toString();
    }

    private static String describe(DiscoveredModule discovered) {
        var info = discovered.module().info();
        return info.name() + " (" + info.id() + " " + info.version() + ") from " + discovered.origin();
    }
}
