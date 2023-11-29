package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.v1X.NMSBinding1X;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.*;

import static com.volmit.iris.Iris.instance;
import static com.volmit.iris.core.safeguard.IrisSafeguard.stablemode;
import static com.volmit.iris.core.safeguard.IrisSafeguard.unstablemode;

public class ServerBootSFG {
    public static final Map<String, Boolean> incompatiblePlugins = new HashMap<>();
    public static boolean unsuportedversion = false;
    protected static boolean safeguardPassed;
    public static boolean passedserversoftware = true;
    protected static int count;
    protected static byte severityLow;
    protected static byte severityMedium;
    protected static byte severityHigh;
    public static String allIncompatiblePlugins;

    public static void BootCheck() {
        Iris.info("Checking for possible conflicts..");
        org.bukkit.plugin.PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin[] plugins = pluginManager.getPlugins();

        incompatiblePlugins.clear();
        incompatiblePlugins.put("Multiverse-Core", false);
        incompatiblePlugins.put("Dynmap", false);
        incompatiblePlugins.put("TerraformGenerator", false);
        incompatiblePlugins.put("Stratos", false);

        String pluginName;
        for (Plugin plugin : plugins) {
            pluginName = plugin.getName();
            Boolean flag = incompatiblePlugins.get(pluginName);
            if (flag != null && !flag) {
                severityHigh++;
                incompatiblePlugins.put(pluginName, true);
            }
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (Map.Entry<String, Boolean> entry : incompatiblePlugins.entrySet()) {
            if (entry.getValue()) {
                joiner.add(entry.getKey());
            }
        }
        if (
        !instance.getServer().getVersion().contains("Purpur") &&
        !instance.getServer().getVersion().contains("Paper") &&
        !instance.getServer().getVersion().contains("Spigot") &&
        !instance.getServer().getVersion().contains("Pufferfish") &&
         !instance.getServer().getVersion().contains("Bukkit"))
        {
            passedserversoftware = false;
            joiner.add("Server Software");
            severityHigh++;
        }
        if (INMS.get() instanceof NMSBinding1X) {
            unsuportedversion = true;
            joiner.add("Unsupported Minecraft Version");
            severityHigh++;
        }

        allIncompatiblePlugins = joiner.toString();

        safeguardPassed = (severityHigh == 0 && severityMedium == 0 && severityLow == 0);
        count = severityHigh + severityMedium + severityLow;
        if(safeguardPassed){
            stablemode = true;
            Iris.safeguard("Stable mode has been activated.");
        }
        if(!safeguardPassed){
            unstablemode = true;
            Iris.safeguard("Unstable mode has been activated.");
        }
    }
}
