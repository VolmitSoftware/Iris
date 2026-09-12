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

package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.platform.bukkit.nms.v1X.NMSBinding1X;
import org.bukkit.Bukkit;

public class INMS {
    //@done
    private static final INMSBinding binding;
    private static final Throwable bindFailure;

    // A throwing field initializer would leave this class permanently erroneous — every
    // later touch raises NoClassDefFoundError and buries the real "unsupported Minecraft
    // version" message. Capture the failure so callers can report the actual cause.
    static {
        INMSBinding bound = null;
        Throwable failure = null;
        try {
            bound = bind();
        } catch (Throwable t) {
            failure = t;
        }
        binding = bound;
        bindFailure = failure;
    }

    public static boolean isBound() {
        return binding != null;
    }

    public static Throwable bindFailure() {
        return bindFailure;
    }

    public static INMSBinding get() {
        if (binding == null) {
            throw new IllegalStateException("Iris NMS binding is unavailable: "
                    + (bindFailure == null ? "unknown failure" : bindFailure.getMessage()), bindFailure);
        }
        return binding;
    }

    public static String getNMSTag() {
        if (IrisSettings.get().getGeneral().isDisableNMS()) {
            return "BUKKIT";
        }

        return NmsBindingSelector.select(requireMinecraftVersion());
    }

    private static INMSBinding bind() {
        boolean disableNms = IrisSettings.get().getGeneral().isDisableNMS();
        if (disableNms) {
            IrisLogging.debug("Craftbukkit BUKKIT <-> " + NMSBinding1X.class.getSimpleName() + " Successfully Bound");
            IrisLogging.warn("NMS support is disabled. Iris world creation is unavailable until general.disableNMS=false.");
            return new NMSBinding1X();
        }
        return bindExact(getNMSTag());
    }

    private static MinecraftVersion requireMinecraftVersion() {
        try {
            MinecraftVersion detected = MinecraftVersion.detect(Bukkit.getServer());
            if (detected == null) {
                throw new IllegalStateException("Iris could not determine the exact Minecraft server version");
            }
            return detected;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.error("Failed to determine server minecraft version!");
            if (e instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("Iris could not determine the exact Minecraft server version", e);
        }
    }

    private static INMSBinding bindExact(String code) {
        IrisLogging.debug("Locating exact NMS Binding for " + code);
        try {
            Class<?> clazz = Class.forName("art.arcane.iris.platform.bukkit.nms." + code + ".NMSBinding");
            Object candidate = clazz.getConstructor().newInstance();
            if (candidate instanceof INMSBinding binding) {
                IrisLogging.debug("Craftbukkit " + code + " <-> " + candidate.getClass().getSimpleName() + " Successfully Bound");
                return binding;
            }
            throw new IllegalStateException("Exact NMS binding class for " + code
                    + " does not implement " + INMSBinding.class.getName());
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            if (e instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("Failed to bind exact NMS revision " + code, e);
        }
    }
}
