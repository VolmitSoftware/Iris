package com.volmit.host;

import java.util.ArrayList;
import java.util.List;

/** The one log message a pack load writes: which modules the pack uses, grouped by whether the host
 * or the pack brings them. The host's modules are named only; the pack's own also carry their
 * version and jar.
 */
final class PackModulesSummary {
    private static final String INDENT = "  ";
    private static final String HOST_LABEL = "Host: ";
    private static final String PACK_LABEL = "Pack: ";
    private static final String ENTRY_SEPARATOR = ",";
    private static final int MAX_LINE_WIDTH = 100;

    private PackModulesSummary() {}

    static String describe(String packName, List<DiscoveredModule> modules) {
        if (modules.isEmpty()) {
            return "Pack " + packName + " uses no modules: no jars in its " + PackModules.MODULES_DIRECTORY + " directory";
        }
        List<String> hostModules = new ArrayList<>();
        List<String> ownModules = new ArrayList<>();
        for (var module : modules) {
            if (isFromHost(module)) {
                hostModules.add(module.info().name());
            } else {
                ownModules.add(describeOwn(module));
            }
        }
        var text = new StringBuilder("Pack ").append(packName).append(" uses ").append(modules.size())
                .append(modules.size() == 1 ? " module:" : " modules:");
        appendGroup(text, HOST_LABEL, hostModules);
        appendGroup(text, PACK_LABEL, ownModules);
        return text.toString();
    }

    private static boolean isFromHost(DiscoveredModule module) {
        return module.origin().equals(DiscoveredModule.HOST_CLASSPATH)
                || module.origin().endsWith(PackModules.BUNDLED_ORIGIN_SUFFIX);
    }

    private static String describeOwn(DiscoveredModule discovered) {
        var info = discovered.info();
        return info.name() + " " + info.version() + " (" + discovered.origin() + ")";
    }

    private static void appendGroup(StringBuilder text, String label, List<String> entries) {
        if (entries.isEmpty()) {
            return;
        }
        String continuation = INDENT + " ".repeat(label.length());
        var line = new StringBuilder(INDENT).append(label);
        int entriesOnLine = 0;
        for (int index = 0; index < entries.size(); index++) {
            String entry = entries.get(index) + (index < entries.size() - 1 ? ENTRY_SEPARATOR : "");
            if (entriesOnLine > 0 && line.length() + 1 + entry.length() > MAX_LINE_WIDTH) {
                text.append('\n').append(line);
                line = new StringBuilder(continuation);
                entriesOnLine = 0;
            }
            if (entriesOnLine > 0) {
                line.append(' ');
            }
            line.append(entry);
            entriesOnLine++;
        }
        text.append('\n').append(line);
    }
}
