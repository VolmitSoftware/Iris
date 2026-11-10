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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.modules.post.PostProcessingSlabsSettings;
import art.arcane.iris.modules.post.PostProcessingWallsSettings;
import art.arcane.iris.modules.terrain.LayerBlocks;
import art.arcane.iris.builtin.terrain.PaletteLayer;
import art.arcane.iris.modules.terrain.SlabSettings;
import art.arcane.iris.modules.terrain.WallSettings;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.IrisFeature;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.hunk.Hunk;
import com.volmit.config.FeatureSettings;
import com.volmit.resource.ResourceId;
import com.volmit.stage.ChunkStageContext;
import com.volmit.world.TerrainSampler;

import java.util.List;
import java.util.function.Function;

/**
 * Smooths the carved surface of one chunk for {@link PostModule}: floating nibs, single-block bumps and potholes,
 * walls under steep faces, slabs on steps, waterlogging and the foliage that lost its ground.
 */
final class SurfaceSmoothing {
    private static final BlockState AIR = new BlockState("minecraft:air");
    private static final ResourceId AIR_ID = AIR.material();
    private static final ResourceId CAVE_AIR_ID = ResourceId.parse("minecraft:cave_air");
    private static final ResourceId WATER_ID = ResourceId.parse("minecraft:water");
    private static final ResourceId SNOW_ID = ResourceId.parse("minecraft:snow");
    private static final String SLAB_SUFFIX = "_slab";
    private static final String WATERLOGGED = "waterlogged";
    private static final String TRUE = "true";
    /** A biome without a wall or slab picks no block. */
    private static final PaletteLayer NO_LAYER = PaletteLayer.withoutBlocks();
    /** Walls and slabs are picked once per column: the first signature of a layer's noise. */
    private static final int COLUMN_SIGNATURE = 0;
    /** The face properties of a connecting block, such as a vine's, each with the neighbour it faces. */
    private static final List<Face> FACES = List.of(new Face("north", 0, 0, -1), new Face("east", 1, 0, 0),
            new Face("south", 0, 0, 1), new Face("west", -1, 0, 0), new Face("up", 0, 1, 0), new Face("down", 0, -1, 0));

    private final Hunk<BlockState> blocks;
    private final BlockRegistry registry;
    private final TerrainSampler terrain;
    private final FeatureSettings settings;
    private final LayerBlocks layerBlocks;
    private final RNG postRandom;
    private final int originX;
    private final int originZ;
    private final int planeWidth;
    private final int[] heights;
    private final boolean walls;
    private final boolean slabs;
    private final int fluidHeight;

    private SurfaceSmoothing(ChunkStageContext context) {
        this.blocks = context.blocks();
        this.registry = context.world().resources().blocks();
        this.terrain = context.terrain();
        this.settings = context.settings();
        this.layerBlocks = LayerBlocks.of(context.world());
        this.postRandom = new RNG(context.world().seed(IrisSeedPurposes.POST));
        this.originX = context.originX();
        this.originZ = context.originZ();
        this.planeWidth = blocks.width() + 2;
        this.heights = heightPlane();
        this.walls = enabled(context, PostProcessingWallsSettings.class, PostProcessingWallsSettings::enabled, PostProcessingWallsSettings.DEFAULT_ENABLED);
        this.slabs = enabled(context, PostProcessingSlabsSettings.class, PostProcessingSlabsSettings::enabled, PostProcessingSlabsSettings.DEFAULT_ENABLED);
        this.fluidHeight = terrain.fluidHeight();
    }

    /** Smooths the chunk of {@code context}. */
    static void smooth(ChunkStageContext context) {
        new SurfaceSmoothing(context).smoothColumns();
    }

    private void smoothColumns() {
        for (int i = 0; i < blocks.width(); i++) {
            for (int j = 0; j < blocks.depth(); j++) {
                post(i, j);
            }
        }
    }

    /**
     * Every column reads its own height plus its four neighbours, so adjacent columns would otherwise
     * resolve the same height up to five times. Resolve the padded plane once instead. The four diagonal
     * corners are never read, so they are left unresolved.
     */
    /** A switch of the dimension, which every column of the chunk shares. */
    private static <S extends IrisFeature> boolean enabled(ChunkStageContext context, Class<S> type, Function<S, Boolean> enabled, boolean byDefault) {
        return context.settings().at(type, context.centreX(), context.centreZ()).dimension().map(enabled).orElse(byDefault);
    }

    /** The biome layer of the column's surface biome that {@code type} holds, or none. */
    private <S extends IrisFeature> PaletteLayer biomeLayer(Class<S> type, Function<S, PaletteLayer> layer, int x, int z) {
        return settings.at(type, x, z).mostSpecific().map(layer).orElse(NO_LAYER);
    }

    /** The layer's block at the position, picked with the world's post-processing random, or null when it has none. */
    private BlockState pick(PaletteLayer layer, double x, double y, double z) {
        int index = layerBlocks.pick(layer, postRandom, COLUMN_SIGNATURE, x, y, z);
        return index == LayerBlocks.NONE ? null : layerBlocks.blocks(layer).get(index);
    }

    private int[] heightPlane() {
        int width = blocks.width();
        int depth = blocks.depth();
        int[] plane = new int[planeWidth * (depth + 2)];
        for (int j = -1; j <= depth; j++) {
            boolean edge = j == -1 || j == depth;
            int from = edge ? 0 : -1;
            int to = edge ? width - 1 : width;
            int row = (j + 1) * planeWidth;
            for (int i = from; i <= to; i++) {
                plane[row + i + 1] = surfaceHeight(originX + i, originZ + j);
            }
        }
        return plane;
    }

    private int surfaceHeight(int x, int z) {
        return terrain.surfaceHeight(x, z);
    }

    private void post(int localX, int localZ) {
        int x = originX + localX;
        int z = originZ + localZ;
        int center = (localZ + 1) * planeWidth + localX + 1;
        int h = heights[center];
        int ha = heights[center + 1];
        int hb = heights[center + planeWidth];
        int hc = heights[center - 1];
        int hd = heights[center - planeWidth];

        if (h < 1) {
            return;
        }

        h = removeFloatingNib(x, z, h, ha, hb, hc, hd);
        h = levelBumpOrPothole(x, z, h, ha, hb, hc, hd);
        if (walls) {
            paintWall(x, z, h, ha, hb, hc, hd);
        }
        if (slabs) {
            h = placeSlab(x, z, h, ha, hb, hc, hd);
        }
        settleWaterlogging(x, z, h);
        settleVine(x, z, h);
    }

    private int removeFloatingNib(int x, int z, int h, int ha, int hb, int hc, int hd) {
        int g = lower(ha, h - 1) + lower(hb, h - 1) + lower(hc, h - 1) + lower(hd, h - 1);
        if (g != 4 || !isAir(x, h - 1, z)) {
            return h;
        }
        set(x, h, z, AIR);
        for (int i = h - 1; i > 0; i--) {
            if (!isAir(x, i, z)) {
                return i;
            }
        }
        return h;
    }

    private int levelBumpOrPothole(int x, int z, int h, int ha, int hb, int hc, int hd) {
        int g = same(ha, h - 1) + same(hb, h - 1) + same(hc, h - 1) + same(hd, h - 1);
        if (g >= 4) {
            BlockState bump = get(x, h, z);
            BlockState above = get(x, h + 1, z);
            if (has(above, BlockTrait.OCCLUDING) && has(above, BlockTrait.SOLID) && has(bump, BlockTrait.SOLID)) {
                set(x, h, z, above);
                return h - 1;
            }
            return h;
        }
        g = same(ha, h + 1) + same(hb, h + 1) + same(hc, h + 1) + same(hd, h + 1);
        if (g < 4) {
            return h;
        }
        int solidSides = solid(get(x, ha, z)) + solid(get(x, hb, z)) + solid(get(x, hc, z)) + solid(get(x, hd, z));
        if (solidSides < 3) {
            return h;
        }
        set(x, h + 1, z, get(x, h, z));
        return h + 1;
    }

    private void paintWall(int x, int z, int h, int ha, int hb, int hc, int hd) {
        if (ha >= h - 2 && hb >= h - 2 && hc >= h - 2 && hd >= h - 2) {
            return;
        }
        PaletteLayer wall = biomeLayer(WallSettings.class, WallSettings::layer, x, z);
        if (wall.palette().isEmpty()) {
            return;
        }
        boolean brokeGround = false;
        int max = Math.abs(Math.max(h - ha, Math.max(h - hb, Math.max(h - hc, h - hd))));
        for (int i = h; i > h - max; i--) {
            BlockState block = pick(wall, x + i, i + h, z + i);
            if (block == null) {
                continue;
            }
            if (isAirOrWater(x, i, z)) {
                if (brokeGround) {
                    break;
                }
                continue;
            }
            set(x, i, z, block);
            brokeGround = true;
        }
    }

    private int placeSlab(int x, int z, int h, int ha, int hb, int hc, int hd) {
        boolean step = (ha == h + 1 && isSolidNonSlab(x + 1, ha, z))
                || (hb == h + 1 && isSolidNonSlab(x, hb, z + 1))
                || (hc == h + 1 && isSolidNonSlab(x - 1, hc, z))
                || (hd == h + 1 && isSolidNonSlab(x, hd, z - 1));
        if (!step) {
            return h;
        }
        PaletteLayer slab = biomeLayer(SlabSettings.class, SlabSettings::layer, x, z);
        if (!slab.slopeCondition().isValid(terrain.column(x, z).slope())) {
            return h;
        }
        BlockState block = pick(slab, x, h, z);
        if (block == null) {
            return h;
        }
        boolean cancel = has(block, BlockTrait.AIR)
                || block.material().equals(SNOW_ID) && h + 1 <= fluidHeight
                || isSnowLayer(x, h, z);
        if (cancel || !isAirOrWater(x, h + 1, z)) {
            return h;
        }
        set(x, h + 1, z, block);
        return h + 1;
    }

    private void settleWaterlogging(int x, int z, int h) {
        BlockState block = get(x, h, z);
        if (!block.properties().containsKey(WATERLOGGED)) {
            return;
        }
        boolean waterlogged = h <= fluidHeight + 1 && (isWaterOrWaterlogged(x, h + 1, z)
                || isWaterOrWaterlogged(x + 1, h, z) || isWaterOrWaterlogged(x - 1, h, z)
                || isWaterOrWaterlogged(x, h, z + 1) || isWaterOrWaterlogged(x, h, z - 1));
        if (waterlogged != TRUE.equals(block.properties().get(WATERLOGGED))) {
            set(x, h, z, block.withProperty(WATERLOGGED, String.valueOf(waterlogged)));
        }
    }

    private void settleVine(int x, int z, int h) {
        BlockState block = get(x, h + 1, z);
        if (has(block, BlockTrait.VINE)) {
            connectVine(x, h + 1, z, block);
        }
    }

    /** A vine faces each neighbour it can cling to, a block that is neither air nor another vine. */
    private void connectVine(int x, int y, int z, BlockState vine) {
        BlockState connected = vine;
        for (Face face : FACES) {
            if (!vine.properties().containsKey(face.property())) {
                continue;
            }
            BlockState neighbour = get(x + face.dx(), y + face.dy(), z + face.dz());
            connected = connected.withProperty(face.property(), String.valueOf(!has(neighbour, BlockTrait.AIR) && !has(neighbour, BlockTrait.VINE)));
        }
        if (!connected.equals(vine)) {
            set(x, y, z, connected);
        }
    }

    private boolean isAir(int x, int y, int z) {
        ResourceId material = get(x, y, z).material();
        return material.equals(AIR_ID) || material.equals(CAVE_AIR_ID);
    }

    private boolean isSolidNonSlab(int x, int y, int z) {
        BlockState block = get(x, y, z);
        return has(block, BlockTrait.SOLID) && !block.material().path().endsWith(SLAB_SUFFIX);
    }

    private boolean isAirOrWater(int x, int y, int z) {
        ResourceId material = get(x, y, z).material();
        return material.equals(WATER_ID) || material.equals(AIR_ID) || material.equals(CAVE_AIR_ID);
    }

    private boolean isSnowLayer(int x, int y, int z) {
        return get(x, y, z).material().equals(SNOW_ID);
    }

    private boolean isWaterOrWaterlogged(int x, int y, int z) {
        BlockState block = get(x, y, z);
        return block.material().equals(WATER_ID) || TRUE.equals(block.properties().get(WATERLOGGED));
    }

    private boolean has(BlockState block, BlockTrait trait) {
        return registry.hasTrait(block, trait);
    }

    private int solid(BlockState block) {
        return has(block, BlockTrait.SOLID) ? 1 : 0;
    }

    private static int lower(int neighbour, int than) {
        return neighbour < than ? 1 : 0;
    }

    private static int same(int neighbour, int as) {
        return neighbour == as ? 1 : 0;
    }

    /** Writes inside the chunk only; a step on a neighbour column outside it is left alone. */
    private void set(int x, int y, int z, BlockState block) {
        int localX = x - originX;
        int localZ = z - originZ;
        if (localX < 0 || localZ < 0 || localX >= blocks.width() || localZ >= blocks.depth() || y < 0 || y >= blocks.height()) {
            return;
        }
        blocks.set(localX, y, localZ, block);
    }

    /**
     * Neighbour columns can sit one block outside this chunk, and the blocks of an adjacent chunk are not
     * available while generating this one. Clamp to the nearest block of the chunk instead of wrapping to the
     * opposite edge.
     */
    private BlockState get(int x, int y, int z) {
        return blocks.get(clamp(x - originX, blocks.width()), clamp(y, blocks.height()), clamp(z - originZ, blocks.depth()));
    }

    private static int clamp(int value, int size) {
        return value < 0 ? 0 : Math.min(value, size - 1);
    }

    private record Face(String property, int dx, int dy, int dz) {
    }
}
