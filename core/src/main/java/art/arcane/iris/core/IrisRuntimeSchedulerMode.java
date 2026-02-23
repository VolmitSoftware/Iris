package art.arcane.iris.core;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.util.Locale;

public enum IrisRuntimeSchedulerMode {
    AUTO,
    PAPER_LIKE,
    FOLIA;

    public static IrisRuntimeSchedulerMode resolve(IrisSettings.IrisSettingsPregen pregen) {
        Server server = Bukkit.getServer();
        boolean regionizedRuntime = FoliaScheduler.isRegionizedRuntime(server);
        if (regionizedRuntime) {
            return FOLIA;
        }

        IrisRuntimeSchedulerMode configuredMode = pregen == null ? null : pregen.getRuntimeSchedulerMode();
        if (configuredMode != null && configuredMode != AUTO) {
            if (configuredMode == FOLIA) {
                return PAPER_LIKE;
            }
            return configuredMode;
        }

        String bukkitName = Bukkit.getName();
        String bukkitVersion = Bukkit.getVersion();
        String serverClassName = server == null ? "" : server.getClass().getName();
        if (containsIgnoreCase(bukkitName, "folia")
                || containsIgnoreCase(bukkitVersion, "folia")
                || containsIgnoreCase(serverClassName, "folia")) {
            return FOLIA;
        }

        if (containsIgnoreCase(bukkitName, "purpur")
                || containsIgnoreCase(bukkitVersion, "purpur")
                || containsIgnoreCase(serverClassName, "purpur")
                || containsIgnoreCase(bukkitName, "paper")
                || containsIgnoreCase(bukkitVersion, "paper")
                || containsIgnoreCase(serverClassName, "paper")
                || containsIgnoreCase(bukkitName, "pufferfish")
                || containsIgnoreCase(bukkitVersion, "pufferfish")
                || containsIgnoreCase(serverClassName, "pufferfish")
                || containsIgnoreCase(bukkitName, "spigot")
                || containsIgnoreCase(bukkitVersion, "spigot")
                || containsIgnoreCase(serverClassName, "spigot")
                || containsIgnoreCase(bukkitName, "craftbukkit")
                || containsIgnoreCase(bukkitVersion, "craftbukkit")
                || containsIgnoreCase(serverClassName, "craftbukkit")) {
            return PAPER_LIKE;
        }

        if (regionizedRuntime) {
            return FOLIA;
        }

        return PAPER_LIKE;
    }

    private static boolean containsIgnoreCase(String value, String contains) {
        if (value == null || contains == null || contains.isEmpty()) {
            return false;
        }

        return value.toLowerCase(Locale.ROOT).contains(contains.toLowerCase(Locale.ROOT));
    }
}
