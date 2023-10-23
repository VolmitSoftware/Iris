package com.volmit.iris.engine.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.v20.NMSBinding1_20_1;
import com.volmit.iris.util.format.C;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import static com.volmit.iris.Iris.instance;
import static com.volmit.iris.engine.safeguard.IrisSafeguard.unstablemode;

public class ServerBoot {
    public static boolean multiverse = false;
    public static boolean dynmap = false;
    public static boolean terraform = false;
    public static boolean stratos = false;
    public static boolean correctversion = true;

    protected static boolean safeguardPassed;
    public static boolean passedserversoftware = true;
    protected static byte count;

    public static void BootCheck() {
        Iris.info("Checking for possible conflicts..");
        org.bukkit.plugin.PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin[] plugins = pluginManager.getPlugins();
        if (!instance.getServer().getBukkitVersion().contains(NMSBinding1_20_1.NMS_VERSION)) {
            unstablemode = true;
            correctversion = false;
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
        }

        if (
        !instance.getServer().getVersion().contains("Purpur") &&
        !instance.getServer().getVersion().contains("Paper") &&
        !instance.getServer().getVersion().contains("Spigot") &&
        !instance.getServer().getVersion().contains("Pufferfish") &&
         !instance.getServer().getVersion().contains("Bukkit"))
        {
            unstablemode = true;
            passedserversoftware = false;
        }

        safeguardPassed = (count == 0);
        if(!safeguardPassed){
            unstablemode = true;
        }
        if (unstablemode){
            Iris.safeguard("Unstable mode has been activated.");
        }
        Iris.safeguard(pluginList.toString());

    }
    public static void UnstableMode(){
        if (unstablemode) {
            Iris.safeguard(C.DARK_RED + "Iris is running in Unstable Mode");
        } else {
            Iris.safeguard(C.BLUE + "Iris is running Stable");
        }
    }
    public static void SupportedServerSoftware(){
        if (!passedserversoftware) {
            Iris.safeguard(C.DARK_RED + "Server is running unsupported server software");
            Iris.safeguard(C.RED + "Supported: Purpur, Pufferfish, Paper, Spigot, Bukkit");
        }
    }
    public static void printincompatiblepluginWarnings(){

        if (safeguardPassed) {
            Iris.safeguard(C.BLUE + "0 Conflicts found");
        } else {
            Iris.safeguard(C.DARK_RED + "" + count + " Conflicts found");
            unstablemode = true;

            if (multiverse) {
                Iris.safeguard(C.RED + "Multiverse");
                Iris.safeguard(C.RED + "- The plugin Multiverse is not compatible with the server.");
                Iris.safeguard(C.RED + "- If you want to have a world manager, consider using PhantomWorlds or MyWorlds instead.");
            }
            if (dynmap) {
                Iris.safeguard(C.RED + "Dynmap");
                Iris.safeguard(C.RED + "- The plugin Dynmap is not compatible with the server.");
                Iris.safeguard(C.RED + "- If you want to have a map plugin like Dynmap, consider Bluemap or LiveAtlas.");
            }
            if (terraform || stratos) {
                Iris.safeguard(C.YELLOW + "Terraform Generator / Stratos");
                Iris.safeguard(C.YELLOW + "- Iris is not compatible with other worldgen plugins.");
            }
        }
    }
}
