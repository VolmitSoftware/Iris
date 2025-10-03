package com.volmit.iris.util.format;

import com.volmit.iris.Iris;
import com.volmit.iris.core.safeguard.ServerBootSFG;
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Versions {
    private static final int[] CACHED_IRIS_VERSION = getIrisVersion();

    private static final int[] CACHED_MC_VERSIONS = getMCVersion();

    public static int[] getIrisVersion() {
        if (CACHED_IRIS_VERSION != null) return CACHED_IRIS_VERSION;
        try {
            return Arrays.stream(Iris.instance.getDescription().getVersion().split("-")[0].split("\\."))
                    .mapToInt(Integer::parseInt)
                    .toArray();
        } catch (Exception e) {
            Iris.error("Failed to parse Iris version format", e);
            return new int[2];
        }
    }

    public static int[] getMCVersion() {
        if (CACHED_MC_VERSIONS != null) return CACHED_MC_VERSIONS;
        try {
            Matcher matcher = Pattern.compile("\\(MC: ([\\d.]+)\\)").matcher(Bukkit.getVersion());
            if(!matcher.find()) throw new Exception("Failed to parse " + Bukkit.getVersion());
            return Arrays.stream(matcher.group(1).split("\\.")).mapToInt(Integer::parseInt).toArray();
        } catch (Exception e) {
            Iris.error("Failed to parse Minecraft version format");
            return new int[2];
        }
    }

}
