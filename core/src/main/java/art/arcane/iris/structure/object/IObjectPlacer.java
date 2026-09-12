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

package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.TerrainBoundarySignature;

import java.util.Optional;
import art.arcane.iris.spi.PlatformBlockState;
import org.jetbrains.annotations.Nullable;

public interface IObjectPlacer {
    int getHighest(int x, int z, IrisData data);

    int getHighest(int x, int z, IrisData data, boolean ignoreFluid);

    void set(int x, int y, int z, PlatformBlockState d);

    PlatformBlockState get(int x, int y, int z);

    boolean isPreventingDecay();

    boolean isCarved(int x, int y, int z);

    /**
     * Whether the block at this position is real, solid ground. Only live-world placers can answer this;
     * at mantle time terrain has not been written yet, so the height stream is the authority.
     */
    default boolean isSurfaceSolid(int x, int y, int z) {
        return true;
    }

    boolean isSolid(int x, int y, int z);

    boolean isUnderwater(int x, int z);

    int getFluidHeight();

    default int getFluidHeight(int x, int z) {
        Engine engine = getEngine();
        if (engine == null || engine.getComplex() == null) {
            return getFluidHeight();
        }
        int coordinateShift = getFluidHeight() - engine.getDimension().getFluidHeight();
        Optional<TerrainBoundarySignature> resolved = engine.getComplex().resolvedTerrainColumn(x, z);
        if (resolved.isPresent()) {
            return resolved.get().fluidHeight().isPresent()
                    ? coordinateShift + resolved.get().fluidHeight().getAsInt() : -1;
        }
        return coordinateShift + (int) Math.round(
                engine.getComplex().getRiverWaterSurfaceStream().get(x, z)
        );
    }

    boolean isDebugSmartBore();

    void setTile(int xx, int yy, int zz, TileData tile);

    <T> void setData(int xx, int yy, int zz, T data);

    <T> @Nullable T getData(int xx, int yy, int zz, Class<T> t);

    Engine getEngine();
}
