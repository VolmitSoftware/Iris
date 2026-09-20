/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.modded;

import art.arcane.iris.generation.decoration.DecoratorPlatformHooks;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockFaces;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;

public final class ModdedDecoratorHooks implements DecoratorPlatformHooks.FaceFixer, DecoratorPlatformHooks.SurfaceSturdiness {
    @Override
    public NativeBlockState fixFaces(NativeBlockState state, Hunk<NativeBlockState> hunk, int rX, int rZ,
                                     int x, int y, int z, EngineMantle mantle) {
        return NativeBlockFaces.fixFaces(state, new Neighbors(hunk, rX, rZ, x, y, z, mantle));
    }

    @Override
    public boolean canGoOn(NativeBlockState surface, boolean upward) {
        return NativeBlockFaces.canGoOn(surface, upward);
    }

    private record Neighbors(Hunk<NativeBlockState> hunk, int rX, int rZ, int x, int y, int z,
                             EngineMantle mantle) implements NativeBlockFaces.Neighbors {
        @Override
        public NativeBlockState primary(int dx, int dy, int dz) {
            NativeBlockState state = mantle == null ? null
                    : mantle.getMantle().get(x + dx, y + dy, z + dz, NativeBlockState.class);
            return state == null ? EngineMantle.AIR.get() : state;
        }

        @Override
        public NativeBlockState secondary(int dx, int dy, int dz) {
            int xx = rX + dx;
            int yy = y + dy;
            int zz = rZ + dz;
            return xx < 0 || xx > 15 || zz < 0 || zz > 15 || yy < 0 || yy > hunk.getHeight()
                    ? null : hunk.get(xx, yy, zz);
        }
    }
}
