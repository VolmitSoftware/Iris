/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.core.nms;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.nms.v1X.NMSBinding1X;
import org.bukkit.Bukkit;

import java.util.List;

public class INMS {
    private static final Version CURRENT = Boolean.getBoolean("iris.no-version-limit") ?
            new Version(Integer.MAX_VALUE, Integer.MAX_VALUE, null) :
            new Version(26, 1, null);

    private static final List<Version> REVISION = List.of(
            new Version(26, 1, "v26_1_R1"),
            new Version(21, 11, "v1_21_R7"),
            new Version(21, 9, "v1_21_R6"),
            new Version(21, 6, "v1_21_R5"),
            new Version(21, 5, "v1_21_R4"),
            new Version(21, 4, "v1_21_R3"),
            new Version(21, 2, "v1_21_R2"),
            new Version(21, 0, "v1_21_R1"),
            new Version(20, 5, "v1_20_R4")
    );

    private static final List<Version> PACKS = List.of(
            new Version(26, 1, "101"),
            new Version(21, 5, "31100"),
            new Version(21, 4, "31020"),
            new Version(21, 2, "31000"),
            new Version(20, 1, "3910")
    );

    private static final List<Version> OVERWORLD_RELEASES = List.of(
            new Version(26, 1, "31100"),
            new Version(21, 5, "31100"),
            new Version(21, 4, "31020"),
            new Version(21, 2, "31000"),
            new Version(20, 1, "3910")
    );

    //@done
    private static final INMSBinding binding = bind();
    public static final String OVERWORLD_TAG = getTag(PACKS, "3910");
    public static final String OVERWORLD_RELEASE_TAG = getTag(OVERWORLD_RELEASES, "3910");

    public static INMSBinding get() {
        return binding;
    }

    public static String getNMSTag() {
        if (IrisSettings.get().getGeneral().isDisableNMS()) {
            return "BUKKIT";
        }

        try {
            String name = Bukkit.getServer().getClass().getCanonicalName();
            if (name.equals("org.bukkit.craftbukkit.CraftServer")) {
                return getTag(REVISION, "BUKKIT");
            } else {
                return name.split("\\Q.\\E")[3];
            }
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.error("Failed to determine server nms version!");
            e.printStackTrace();
        }

        return "BUKKIT";
    }

    private static INMSBinding bind() {
        String code = getNMSTag();
        Iris.info("Locating NMS Binding for " + code);

        try {
            Class<?> clazz = Class.forName("com.volmit.iris.core.nms."+code+".NMSBinding");
            try {
                Object b = clazz.getConstructor().newInstance();
                if (b instanceof INMSBinding binding) {
                    Iris.info("Craftbukkit " + code + " <-> " + b.getClass().getSimpleName() + " Successfully Bound");
                    return binding;
                }
            } catch (Throwable e) {
                Iris.reportError(e);
                e.printStackTrace();
            }
        } catch (ClassNotFoundException|NoClassDefFoundError classNotFoundException) {}

        Iris.info("Craftbukkit " + code + " <-> " + NMSBinding1X.class.getSimpleName() + " Successfully Bound");
        Iris.warn("Note: Some features of Iris may not work the same since you are on an unsupported version of Minecraft.");
        Iris.warn("Note: If this is a new version, expect an update soon.");

        return new NMSBinding1X();
    }

    private static String getTag(List<Version> versions, String def) {
        var version = parseVersion(Bukkit.getServer().getBukkitVersion());
        int major = version.major;
        int minor = version.minor;

        if (isAfter(version, CURRENT)) {
            return versions.getFirst().tag;
        }

        for (var p : versions) {
            if (p.major > major || (p.major == major && p.minor > minor))
                continue;
            return p.tag;
        }
        return def;
    }

    private static Version parseVersion(String bukkitVersion) {
        var version = bukkitVersion.split("-")[0].split("\\.", 3);
        int major = 0;
        int minor = 0;

        try {
            if (version.length > 0) {
                if ("1".equals(version[0]) && version.length > 1) {
                    major = Integer.parseInt(version[1]);
                    minor = version.length > 2 ? Integer.parseInt(version[2]) : 0;
                } else {
                    major = Integer.parseInt(version[0]);
                    minor = version.length > 1 ? Integer.parseInt(version[1]) : 0;
                }
            }
        } catch (NumberFormatException e) {
            Iris.reportError(e);
            Iris.warn("Failed to parse server version " + bukkitVersion + ", using Bukkit fallback.");
        }

        return new Version(major, minor, null);
    }

    private static boolean isAfter(Version version, Version current) {
        return version.major > current.major || (version.major == current.major && version.minor > current.minor);
    }

    private record Version(int major, int minor, String tag) {}
}
