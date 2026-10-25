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

package art.arcane.iris.studio.map;

import art.arcane.iris.spi.IrisLogging;

import java.util.List;

/**
 * Whether the graphics card library the 3D views draw with and its native bindings can load. Mod loaders get it from the game,
 * which ships only part of it, so the views check here before touching any class that links against it.
 */
final class GpuLibrary {
    private static final List<String> REQUIRED_CLASSES = List.of(
            "org.lwjgl.system.MemoryStack",
            "org.lwjgl.opengl.GL",
            "org.lwjgl.egl.EGL",
            "org.lwjgl.system.jawt.JAWT"
    );
    private static final boolean LOADED = findRequiredClasses();

    private GpuLibrary() {
    }

    static boolean isLoaded() {
        return LOADED;
    }

    private static boolean findRequiredClasses() {
        ClassLoader loader = GpuLibrary.class.getClassLoader();
        for (String className : REQUIRED_CLASSES) {
            try {
                Class.forName(className, false, loader);
            } catch (ClassNotFoundException | LinkageError missing) {
                IrisLogging.info("3D views draw without a graphics card: %s isn't on the classpath", className);
                return false;
            }
        }
        try {
            Class.forName("org.lwjgl.system.jawt.JAWTFunctions", true, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError | SecurityException unavailable) {
            IrisLogging.info("3D views draw without a graphics card: native bindings are unavailable (%s)", unavailable);
            return false;
        }
    }
}
