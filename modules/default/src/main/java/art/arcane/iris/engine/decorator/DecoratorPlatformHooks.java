/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.decorator;

import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;

public final class DecoratorPlatformHooks {
    private static volatile FaceFixer FACE_FIXER = null;
    private static volatile SurfaceSturdiness SURFACE_STURDINESS = null;

    private DecoratorPlatformHooks() {
    }

    public interface FaceFixer {
        PlatformBlockState fixFaces(PlatformBlockState state, Hunk<PlatformBlockState> hunk, int rX, int rZ, int x, int y, int z, EngineMantle mantle);
    }

    public interface SurfaceSturdiness {
        boolean canGoOn(PlatformBlockState surface);
    }

    public static synchronized Bindings bind(FaceFixer faceFixer, SurfaceSturdiness surfaceSturdiness) {
        Bindings previous = new Bindings(FACE_FIXER, SURFACE_STURDINESS);
        FACE_FIXER = faceFixer;
        SURFACE_STURDINESS = surfaceSturdiness;
        return previous;
    }

    public static synchronized void restore(Bindings bindings) {
        FACE_FIXER = bindings.faceFixer();
        SURFACE_STURDINESS = bindings.surfaceSturdiness();
    }

    static FaceFixer faceFixer() {
        return FACE_FIXER;
    }

    static SurfaceSturdiness surfaceSturdiness() {
        return SURFACE_STURDINESS;
    }

    public record Bindings(FaceFixer faceFixer, SurfaceSturdiness surfaceSturdiness) {
    }
}
