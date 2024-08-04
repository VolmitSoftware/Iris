package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.v1X.NMSBinding1X;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static com.volmit.iris.Iris.getJavaVersion;
import static com.volmit.iris.Iris.instance;
import static com.volmit.iris.core.safeguard.IrisSafeguard.*;

public class ServerBootSFG {
    public static final Map<String, Boolean> incompatibilities = new HashMap<>();
    public static boolean isJDK17 = true;
    public static boolean hasEnoughDiskSpace = true;
    public static boolean isJRE = false;
    public static boolean hasPrivileges = true;
    public static boolean unsuportedversion = false;
    protected static boolean safeguardPassed;
    public static boolean passedserversoftware = true;
    protected static int count;
    protected static byte severityLow;
    protected static byte severityMedium;
    protected static byte severityHigh;
    public static String allIncompatibilities;

    public static void BootCheck() {
        Iris.info("Checking for possible conflicts..");
        PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin[] plugins = pluginManager.getPlugins();

        incompatibilities.clear();
        incompatibilities.put("Multiverse-Core", false);
        incompatibilities.put("dynmap", false);
        incompatibilities.put("TerraformGenerator", false);
        incompatibilities.put("Stratos", false);

        String pluginName;
        for (Plugin plugin : plugins) {
            pluginName = plugin.getName();
            Boolean flag = incompatibilities.get(pluginName);
            if (flag != null && !flag) {
                severityHigh++;
                incompatibilities.put(pluginName, true);
            }
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (Map.Entry<String, Boolean> entry : incompatibilities.entrySet()) {
            if (entry.getValue()) {
                joiner.add(entry.getKey());
            }
        }
        // Legacy ServerInfo
        String distro = Bukkit.getName().toLowerCase();
        if (!distro.contains("purpur") &&
                !distro.contains("paper") &&
                !distro.contains("spigot") &&
                !distro.contains("pufferfish") &&
                !distro.contains("bukkit")) {


            passedserversoftware = false;
            joiner.add("Server Software");
            severityMedium++;
        }


        if (INMS.get() instanceof NMSBinding1X) {
            unsuportedversion = true;
            joiner.add("Unsupported Minecraft Version");
            severityHigh++;
        }

        if (!List.of(17, 21).contains(getJavaVersion())) {
            isJDK17 = false;
            joiner.add("Unsupported Java version");
            severityMedium++;
        }

        if (!isJDK()) {
            isJRE = true;
            joiner.add("Unsupported JDK");
            severityMedium++;
        }

//        if (!hasPrivileges()){
//            hasPrivileges = false;
//            joiner.add("Insufficient Privileges");
//            severityMedium++;
//        } Some servers dont like this

        if (!enoughDiskSpace()){
            hasEnoughDiskSpace = false;
            joiner.add("Insufficient Disk Space");
            severityMedium++;
        }

        allIncompatibilities = joiner.toString();

        safeguardPassed = (severityHigh == 0 && severityMedium == 0 && severityLow == 0);
        count = severityHigh + severityMedium + severityLow;
        if (safeguardPassed) {
            IrisSafeguard.instance.stablemode = true;
            Iris.safeguard("Stable mode has been activated.");
        }
        if (!safeguardPassed) {
            if (severityMedium >= 1 && severityHigh == 0) {
                IrisSafeguard.instance.warningmode = true;
                Iris.safeguard("Warning mode has been activated.");
            }
            if (severityHigh >= 1) {
                IrisSafeguard.instance.unstablemode = true;
                Iris.safeguard("Unstable mode has been activated.");
            }
        }
    }


    public static boolean isJDK() {
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            // If the compiler is null, it means this is a JRE environment, not a JDK.
            return compiler != null;
        } catch (Exception ignored) {}
        return false;
    }
    public static boolean hasPrivileges() {
        Path pv = Paths.get(Bukkit.getWorldContainer() + "iristest.json");
        try (FileChannel fc = FileChannel.open(pv, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            if (Files.isReadable(pv) && Files.isWritable(pv)) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static boolean enoughDiskSpace() {
        File freeSpace = new File(Bukkit.getWorldContainer() + ".");
        double gigabytes = freeSpace.getFreeSpace() / (1024.0 * 1024.0 * 1024.0);
        return gigabytes > 3;
    }

    private static boolean checkJavac(String path) {
        return !path.isEmpty() && (new File(path, "javac").exists() || new File(path, "javac.exe").exists());
    }

}
