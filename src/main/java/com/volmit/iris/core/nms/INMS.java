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
import com.volmit.iris.core.nms.v19_4.NMSBinding19_4;
import com.volmit.iris.core.nms.v1X.NMSBinding1X;
import com.volmit.iris.util.collection.KMap;
import org.bukkit.Bukkit;

public class INMS {
    //@builder
    private static final KMap<String, Class<? extends INMSBinding>> bindings = new KMap<String, Class<? extends INMSBinding>>()
            .qput("v1_19_R3", NMSBinding19_4.class);
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
            return Bukkit.getServer().getClass().getCanonicalName().split("\\Q.\\E")[3];
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

        if (bindings.containsKey(code)) {
            try {
                INMSBinding b = bindings.get(code).getConstructor().newInstance();
                Iris.info("Craftbukkit " + code + " <-> " + b.getClass().getSimpleName() + " Successfully Bound");

                return b;
            } catch (Throwable e) {
                Iris.reportError(e);
                e.printStackTrace();
            }
        }

        Iris.info("Craftbukkit " + code + " <-> " + NMSBinding1X.class.getSimpleName() + " Successfully Bound");
        Iris.warn("Note: Some features of Iris may not work the same since you are on an unsupported version of Minecraft.");
        Iris.warn("Note: If this is a new version, expect an update soon.");

        return new NMSBinding1X();
    }
}
