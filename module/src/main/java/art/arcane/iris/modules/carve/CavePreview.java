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

package art.arcane.iris.modules.carve;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.modules.carve.field.CaveCarver;
import art.arcane.iris.modules.carve.field.CavePart;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.BlockParts;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One cave carved over a square of the world under a flat surface, with the seed the world carves it with: the carved
 * space drawn as blocks, a colour per part, so the studio shows the shape of the cave instead of the rock around it.
 * A variant is the same cave somewhere else in the world.
 */
final class CavePreview {
    static final int VARIANTS = 4;
    private static final int AREA_CHUNKS = 8;
    private static final int AREA_BLOCKS = AREA_CHUNKS * 16;
    private static final int CHUNK_COLUMNS = 256;
    /** The surface the preview carves under, in world Y. */
    static final int SURFACE_WORLD_Y = 96;
    /** A cave deeper than this under the surface is cut off at the bottom of the preview. */
    private static final int MAX_DEPTH_BLOCKS = 256;
    private static final int VARIANT_SPACING_BLOCKS = 4096;
    private static final BlockState TUNNEL_BLOCK = new BlockState("minecraft:light_blue_concrete");
    private static final BlockState CAVERN_BLOCK = new BlockState("minecraft:orange_concrete");
    private static final int[] FLAT_SURFACE = new int[CHUNK_COLUMNS];
    private static final double[] LISTED_EVERYWHERE = new double[CHUNK_COLUMNS];
    private static final boolean[] NOTHING_SEALED = new boolean[CHUNK_COLUMNS];

    static {
        Arrays.fill(LISTED_EVERYWHERE, 1D);
    }

    private final GeneratedObject scene;
    private final BlockParts parts;

    private CavePreview(GeneratedObject scene, BlockParts parts) {
        this.scene = scene;
        this.parts = parts;
    }

    static CavePreview of(PackWorld world, ResourceId id, Cave cave, int variant) {
        int bottomWorldY = Math.max(world.minWorldY(), SURFACE_WORLD_Y - MAX_DEPTH_BLOCKS);
        int surfaceHeight = SURFACE_WORLD_Y - world.minWorldY();
        int lowestHeight = bottomWorldY - world.minWorldY();
        long seed = CaveCarver.seedOf(world.seed(IrisSeedPurposes.CARVE), id);
        CaveCarver carver = new CaveCarver(cave, world.resources().noise(), seed, world.minWorldY(), surfaceHeight + 1);
        int[] surface = FLAT_SURFACE.clone();
        Arrays.fill(surface, surfaceHeight);
        int originX = variant * VARIANT_SPACING_BLOCKS;
        Map<BlockPosition, CavePart> carved = new HashMap<>();
        for (int chunkX = 0; chunkX < AREA_CHUNKS; chunkX++) {
            for (int chunkZ = 0; chunkZ < AREA_CHUNKS; chunkZ++) {
                int sceneX = chunkX * 16;
                int sceneZ = chunkZ * 16;
                carver.carveByPart(originX + sceneX, sceneZ, surface, LISTED_EVERYWHERE, NOTHING_SEALED, (part, localX, y, localZ) -> {
                    if (y >= lowestHeight) {
                        carved.putIfAbsent(new BlockPosition(sceneX + localX, y - lowestHeight, sceneZ + localZ), part);
                    }
                });
            }
        }
        return new CavePreview(sceneOf(carved, surfaceHeight - lowestHeight + 1), partsOf(carved));
    }

    private static GeneratedObject sceneOf(Map<BlockPosition, CavePart> carved, int height) {
        List<ObjectBlock> blocks = new ArrayList<>(carved.size());
        carved.forEach((position, part) -> blocks.add(new ObjectBlock(position, part == CavePart.TUNNEL ? TUNNEL_BLOCK : CAVERN_BLOCK)));
        BlockBox bounds = new BlockBox(0, 0, 0, AREA_BLOCKS, height, AREA_BLOCKS);
        return new GeneratedObject(bounds, new BlockPosition(AREA_BLOCKS / 2, 0, AREA_BLOCKS / 2), blocks, List.of());
    }

    private static BlockParts partsOf(Map<BlockPosition, CavePart> carved) {
        Map<BlockPosition, Integer> parts = new HashMap<>(carved.size());
        carved.forEach((position, part) -> parts.put(position, part.ordinal()));
        return new BlockParts(parts);
    }

    GeneratedObject scene() {
        return scene;
    }

    BlockParts parts() {
        return parts;
    }
}
