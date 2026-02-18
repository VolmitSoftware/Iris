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

package art.arcane.iris.util.project.hunk.view;

import art.arcane.iris.Iris;
import art.arcane.iris.core.service.EditSVC;
import art.arcane.iris.util.project.hunk.Hunk;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;

@SuppressWarnings("ClassCanBeRecord")
public class ChunkBiomeHunkView extends art.arcane.volmlib.util.hunk.view.ChunkWorldHunkView<Biome> implements Hunk<Biome> {
    public ChunkBiomeHunkView(Chunk chunk) {
        super(chunk,
                chunk.getWorld().getMaxHeight(),
                (wx, y, wz, t) -> Iris.service(EditSVC.class).setBiome(chunk.getWorld(), wx, y, wz, t),
                (wx, y, wz) -> Iris.service(EditSVC.class).getBiome(chunk.getWorld(), wx, y, wz));
    }
}
