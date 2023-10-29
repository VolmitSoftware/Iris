package com.volmit.iris.engine.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.v1X.NMSBinding1X;
import com.volmit.iris.util.format.C;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

import static com.volmit.iris.Iris.dump;
import static com.volmit.iris.Iris.instance;
import static com.volmit.iris.engine.safeguard.IrisSafeguard.unstablemode;
@Getter
public class ServerBootSFG {
    public static boolean multiverse = false;
    public static boolean dynmap = false;
    public static boolean terraform = false;
    public static boolean stratos = false;
    public static boolean unsuportedversion = false;
    protected static boolean safeguardPassed;
    public static boolean passedserversoftware = true;
    protected static byte count;

    public static void BootCheck() {
        Iris.info("Checking for possible conflicts..");
        org.bukkit.plugin.PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin[] plugins = pluginManager.getPlugins();
        if (INMS.get() instanceof NMSBinding1X) {
            unsuportedversion = true;
            count++;
        }

        StringBuilder pluginList = new StringBuilder("Plugin list: ");
        count = 0;

        for (Plugin plugin : plugins) {
            String pluginName = plugin.getName();
            if (pluginName.equalsIgnoreCase("Multiverse-Core")) {
                multiverse = true;
                count++;
            }
            if (pluginName.equalsIgnoreCase("Dynmap")) {
                dynmap = true;
                count++;
            }
            if (pluginName.equalsIgnoreCase("TerraformGenerator")) {
                terraform = true;
                count++;
            }
            if (pluginName.equalsIgnoreCase("Stratos")) {
                stratos = true;
                count++;
            }
            pluginList.append(pluginName).append(", ");
            Iris.safeguard(pluginList.toString());
        }

        if (
        !instance.getServer().getVersion().contains("Purpur") &&
        !instance.getServer().getVersion().contains("Paper") &&
        !instance.getServer().getVersion().contains("Spigot") &&
        !instance.getServer().getVersion().contains("Pufferfish") &&
         !instance.getServer().getVersion().contains("Bukkit"))
        {
            passedserversoftware = false;
            count++;
        }

        safeguardPassed = (count == 0);
        if(!safeguardPassed){
            unstablemode = true;
            Iris.safeguard("Unstable mode has been activated.");
        }

    }
}
