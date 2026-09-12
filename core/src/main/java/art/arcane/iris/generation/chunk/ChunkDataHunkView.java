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

package art.arcane.iris.generation.chunk;

import art.arcane.iris.platform.bukkit.BukkitBlockResolution;

import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.volmlib.util.hunk.Hunk;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

@SuppressWarnings("ClassCanBeRecord")
public class ChunkDataHunkView implements Hunk<PlatformBlockState> {
    private final art.arcane.volmlib.util.hunk.view.ChunkDataHunkView view;

    public ChunkDataHunkView(ChunkData chunk) {
        this.view = new art.arcane.volmlib.util.hunk.view.ChunkDataHunkView(chunk, BukkitBlockResolution.getAir(), (data) -> data instanceof IrisCustomData d ? d.getBase() : data);
    }

    @Override
    public int getWidth() {
        return view.getWidth();
    }

    @Override
    public int getDepth() {
        return view.getDepth();
    }

    @Override
    public int getHeight() {
        return view.getHeight();
    }

    @Override
    public void set(int x1, int y1, int z1, int x2, int y2, int z2, PlatformBlockState t) {
        if (t == null) {
            return;
        }

        view.setRegion(x1, y1, z1, x2, y2, z2, (BlockData) t.nativeHandle());
    }

    @Override
    public PlatformBlockState get(int x, int y, int z) {
        return BukkitBlockState.of(view.get(x, y, z));
    }

    @Override
    public void set(int x, int y, int z, PlatformBlockState t) {
        if (t == null) {
            return;
        }

        view.set(x, y, z, (BlockData) t.nativeHandle());
    }

    @Override
    public void setRaw(int x, int y, int z, PlatformBlockState t) {
        if (t == null) {
            return;
        }

        view.setRaw(x, y, z, (BlockData) t.nativeHandle());
    }

    @Override
    public PlatformBlockState getRaw(int x, int y, int z) {
        return BukkitBlockState.of(view.getRaw(x, y, z));
    }
}
