package com.volmit.iris.engine.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.v1X.NMSBinding1X;
import com.volmit.iris.util.format.C;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

import static com.volmit.iris.Iris.dump;
import static com.volmit.iris.Iris.instance;
import static com.volmit.iris.engine.safeguard.IrisSafeguard.unstablemode;

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

        if (unsuportedversion) count++;

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
    public static void printIncompatiblePluginWarnings(){
        // String SupportedIrisVersion = getDescription().getVersion(); //todo Automatic version

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
            if (unsuportedversion) {
                Iris.safeguard(C.RED + "Server Version");
                Iris.safeguard(C.RED + "- Iris only supports 1.19.2 > 1.20.2");
            }
            if (!passedserversoftware) {
                Iris.safeguard(C.RED + "Unsupported Server Software");
                Iris.safeguard(C.RED + "- Please consider using Paper or Purpur instead.");

                // todo Add a cmd to show all issues?
            }
        }
    }

    public static String MSGIncompatiblePluginWarnings(){
        StringBuilder stringBuilder = new StringBuilder();

        List<String> incompatibleList = new ArrayList<>();

        if (multiverse) {
            String incompatibility1 = "Multiverse";
            stringBuilder.append(incompatibility1).append(", ");
            incompatibleList.add(incompatibility1);
        }
        if(dynmap) {
            String incompatibility2 = "Dynmap";
            stringBuilder.append(incompatibility2).append(", ");
            incompatibleList.add(incompatibility2);
        }
        if (terraform) {
            String incompatibility3 = "Terraform";
            stringBuilder.append(incompatibility3).append(", ");
            incompatibleList.add(incompatibility3);
        }
        if(stratos){
            String incompatibility4 = "Stratos";
            stringBuilder.append(incompatibility4).append(", ");
            incompatibleList.add(incompatibility4);

        }

        String MSGIncompatiblePlugins = stringBuilder.toString();
        return MSGIncompatiblePlugins;

    }
}
