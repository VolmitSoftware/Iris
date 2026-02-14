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

import art.arcane.iris.util.data.B;
import art.arcane.iris.util.data.IrisCustomData;
import art.arcane.iris.util.hunk.Hunk;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

@SuppressWarnings("ClassCanBeRecord")
public class ChunkDataHunkView extends art.arcane.volmlib.util.hunk.view.ChunkDataHunkView implements Hunk<BlockData> {
    private static final BlockData AIR = B.getAir();

    public ChunkDataHunkView(ChunkData chunk) {
        super(chunk, AIR, (data) -> data instanceof IrisCustomData d ? d.getBase() : data);
    }

    @Override
    public void set(int x1, int y1, int z1, int x2, int y2, int z2, BlockData t) {
        setRegion(x1, y1, z1, x2, y2, z2, t);
    }

    public BlockData get(int x, int y, int z) {
        return super.get(x, y, z);
    }

    public void set(int x, int y, int z, BlockData t) {
        super.set(x, y, z, t);
    }
}
