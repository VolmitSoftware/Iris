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

package com.volmit.iris.util.particle;

import com.volmit.iris.Iris;
import org.bukkit.Bukkit;

import java.util.Optional;

/**
 * Small reflection class to use CraftBukkit and NMS
 *
 * @author MrMicky
 */
public final class FastReflection {

    public static final String OBC_PACKAGE = "org.bukkit.craftbukkit";
    public static final String NMS_PACKAGE = "net.minecraft.server";

    public static final String VERSION = Bukkit.getServer().getClass().getPackage().getName().substring(OBC_PACKAGE.length() + 1);

    private FastReflection() {
        throw new UnsupportedOperationException();
    }

    public static String nmsClassName(String className) {
        return NMS_PACKAGE + '.' + VERSION + '.' + className;
    }

    public static Class<?> nmsClass(String className) throws ClassNotFoundException {
        return Class.forName(nmsClassName(className));
    }

    public static Optional<Class<?>> nmsOptionalClass(String className) {
        return optionalClass(nmsClassName(className));
    }

    public static String obcClassName(String className) {
        return OBC_PACKAGE + '.' + VERSION + '.' + className;
    }

    public static Class<?> obcClass(String className) throws ClassNotFoundException {
        return Class.forName(obcClassName(className));
    }

    public static Optional<Class<?>> obcOptionalClass(String className) {
        return optionalClass(obcClassName(className));
    }

    public static Optional<Class<?>> optionalClass(String className) {
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException e) {
            Iris.reportError(e);
            return Optional.empty();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object enumValueOf(Class<?> enumClass, String enumName) {
        return Enum.valueOf((Class<Enum>) enumClass, enumName.toUpperCase());
    }
}