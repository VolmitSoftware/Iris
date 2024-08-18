/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

import java.util.Map;

public class INMS {
    private static final Map<String, String> REVISION = Map.of(
            "1.20.5", "v1_20_R4",
            "1.20.6", "v1_20_R4",
            "1.21", "v1_21_R1",
            "1.21.1", "v1_21_R1"
    );
    //@done
    private static final INMSBinding binding = bind();

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
                return REVISION.getOrDefault(Bukkit.getServer().getBukkitVersion().split("-")[0], "BUKKIT");
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
            Class<?> clazz = Class.forName("com.volmit.iris.core.nms." + code + ".NMSBinding");
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
        } catch (ClassNotFoundException |
                 NoClassDefFoundError classNotFoundException) {
        }

        Iris.info("Craftbukkit " + code + " <-> " + NMSBinding1X.class.getSimpleName() + " Successfully Bound");
        Iris.warn("Note: Some features of Iris may not work the same since you are on an unsupported version of Minecraft.");
        Iris.warn("Note: If this is a new version, expect an update soon.");

        return new NMSBinding1X();
    }
}
