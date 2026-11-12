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

package art.arcane.iris.core.studio.saved;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A square of a saved world, one column per pixel: the column at each pixel's north west block, or no ground where no
 * finished chunk holds it. Blocks and biomes are named once each and indexed per pixel.
 *
 * @param groundY       per pixel, row by row from the north west; {@link #NO_GROUND} where nothing is saved
 * @param waterSurfaceY per pixel, the top water block over the ground, or {@link #NO_WATER}
 */
public record SavedTile(int size, int blocksPerPixel, short[] groundY, short[] waterSurfaceY, short[] blocks, short[] biomes,
                        List<SavedBlock> savedBlocks, List<String> biomeNames) {
    public static final short NO_GROUND = Short.MIN_VALUE;
    public static final short NO_WATER = (short) SavedRegion.NO_WATER;

    /**
     * @param tileX          tile index west to east; the tile starts at block {@code tileX * size * blocksPerPixel}
     * @param blocksPerPixel whole blocks, as a saved world holds one column per block
     */
    public static SavedTile sample(SavedWorld world, int tileX, int tileZ, int blocksPerPixel, int size) {
        int pixels = size * size;
        SavedTile tile = new SavedTile(size, blocksPerPixel, new short[pixels], new short[pixels], new short[pixels], new short[pixels],
                new ArrayList<>(), new ArrayList<>());
        Map<SavedBlock, Short> blockIds = new HashMap<>();
        Map<String, Short> biomeIds = new HashMap<>();
        long originX = (long) tileX * size * blocksPerPixel;
        long originZ = (long) tileZ * size * blocksPerPixel;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int pixel = row * size + column;
                SavedRegion.SavedColumn saved = world.columnAt((int) (originX + (long) column * blocksPerPixel),
                        (int) (originZ + (long) row * blocksPerPixel));
                if (saved == null) {
                    tile.groundY[pixel] = NO_GROUND;
                    tile.waterSurfaceY[pixel] = NO_WATER;
                    continue;
                }
                tile.groundY[pixel] = (short) saved.groundY();
                tile.waterSurfaceY[pixel] = (short) saved.waterSurfaceY();
                tile.blocks[pixel] = idOf(saved.block(), blockIds, tile.savedBlocks);
                tile.biomes[pixel] = idOf(saved.biome(), biomeIds, tile.biomeNames);
            }
        }
        return tile;
    }

    private static <Named> short idOf(Named name, Map<Named, Short> ids, List<Named> names) {
        return ids.computeIfAbsent(name, added -> {
            names.add(added);
            return (short) (names.size() - 1);
        });
    }
}
