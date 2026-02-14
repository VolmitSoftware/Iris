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

package art.arcane.iris.util.hunk.view;

import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.engine.data.chunk.LinkedTerrainChunk;
import art.arcane.iris.util.hunk.Hunk;
import art.arcane.volmlib.util.hunk.view.BiomeGridForceSupport;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;

@SuppressWarnings("ClassCanBeRecord")
public class BiomeGridHunkHolder extends art.arcane.volmlib.util.hunk.view.BiomeGridHunkHolder implements Hunk<Biome> {
    public BiomeGridHunkHolder(BiomeGrid chunk, int minHeight, int maxHeight) {
        super(chunk, minHeight, maxHeight);
    }

    public void forceBiomeBaseInto(int x, int y, int z, Object somethingVeryDirty) {
        BiomeGridForceSupport.forceBiomeBaseInto(
                getChunk(),
                getMinHeight(),
                x,
                y,
                z,
                somethingVeryDirty,
                chunk -> chunk instanceof LinkedTerrainChunk ? ((LinkedTerrainChunk) chunk).getRawBiome() : chunk,
                (wx, wy, wz, dirty, target) -> INMS.get().forceBiomeInto(wx, wy, wz, dirty, target));
    }
}
