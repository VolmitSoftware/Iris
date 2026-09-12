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

package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineAssignedModifier;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.terrain.IrisSlopeClip;
import art.arcane.iris.generation.block.BoundBlockState;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.spi.PlatformBlockState;

public class IrisPostModifier extends EngineAssignedModifier<PlatformBlockState> {
    private static final BoundBlockState AIR = BoundBlockState.of("AIR");

    private final RNG rng;

    public IrisPostModifier(Engine engine) {
        super(engine, "Post");
        rng = new RNG(getEngine().getSeedManager().getPost());
    }

    @Override
    public void onModify(int x, int z, Hunk<PlatformBlockState> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        // The post stage runs sequentially on production (multicore false); an uncontended
        // monitor per probe is still a monitor times ~10k probes per chunk.
        Hunk<PlatformBlockState> sync = multicore ? output.synchronize() : output;
        int width = output.getWidth();
        int depth = output.getDepth();
        int planeWidth = width + 2;
        int[] heights = heightPlane(x, z, width, depth, planeWidth);
        IrisDimension dimension = getDimension();
        boolean walls = dimension.isPostProcessingWalls();
        boolean slabs = dimension.isPostProcessingSlabs();
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < depth; j++) {
                post(i, j, sync, i + x, j + z, context, heights, planeWidth, walls, slabs);
            }
        }

        getEngine().getMetrics().getPost().put(p.getMilliseconds());
    }

    /**
     * Every column reads its own height plus its four neighbours, so adjacent columns would otherwise
     * resolve the same height stream entry up to five times. Resolve the padded plane once instead. The
     * four diagonal corners are never read, so they are left unresolved.
     */
    private boolean riverColumn(int x, int z) {
        ProceduralStream<Double> carveWeights = getComplex().getRiverCarveWeightStream();
        return skipsHydrologyColumn(new double[] {
                carveWeights.get(x, z),
                carveWeights.get(x + 1, z),
                carveWeights.get(x - 1, z),
                carveWeights.get(x, z + 1),
                carveWeights.get(x, z - 1)
        });
    }

    static boolean skipsHydrologyColumn(double[] carveWeights) {
        for (double weight : carveWeights) {
            if (weight > 0D) {
                return true;
            }
        }
        return false;
    }

    private int[] heightPlane(int x, int z, int width, int depth, int planeWidth) {
        int[] heights = new int[planeWidth * (depth + 2)];
        EngineMantle mantle = getEngine().getMantle();

        for (int j = -1; j <= depth; j++) {
            boolean edge = j == -1 || j == depth;
            int from = edge ? 0 : -1;
            int to = edge ? width - 1 : width;
            int row = (j + 1) * planeWidth;

            for (int i = from; i <= to; i++) {
                heights[row + i + 1] = mantle.trueHeight(x + i, z + j);
            }
        }

        return heights;
    }

    private void post(int currentPostX, int currentPostZ, Hunk<PlatformBlockState> currentData, int x, int z, ChunkContext context, int[] heights, int planeWidth, boolean walls, boolean slabs) {
        // x/z are world coordinates, the hunk is indexed relative to this chunk origin.
        int originX = x - currentPostX;
        int originZ = z - currentPostZ;
        int center = (currentPostZ + 1) * planeWidth + currentPostX + 1;
        int h = heights[center];
        int ha = heights[center + 1];
        int hb = heights[center + planeWidth];
        int hc = heights[center - 1];
        int hd = heights[center - planeWidth];
        int fluidHeight = (int) Math.round(getComplex().getRiverWaterSurfaceStream().get(x, z));
        boolean river = riverColumn(x, z);

        // Floating Nibs
        int g = 0;

        if (h < 1) {
            return;
        }

        g += ha < h - 1 ? 1 : 0;
        g += hb < h - 1 ? 1 : 0;
        g += hc < h - 1 ? 1 : 0;
        g += hd < h - 1 ? 1 : 0;

        if (!river && g == 4 && isAir(x, h - 1, z, originX, originZ, currentData)) {
            setPostBlock(x, h, z, AIR.get(), originX, originZ, currentData);

            for (int i = h - 1; i > 0; i--) {
                if (!isAir(x, i, z, originX, originZ, currentData)) {
                    h = i;
                    break;
                }
            }
        }

        // Nibs
        g = 0;
        g += ha == h - 1 ? 1 : 0;
        g += hb == h - 1 ? 1 : 0;
        g += hc == h - 1 ? 1 : 0;
        g += hd == h - 1 ? 1 : 0;

        if (river) {
            g = -1;
        }

        if (g >= 4) {
            PlatformBlockState bcState = getPostBlock(x, h, z, originX, originZ, currentData);
            PlatformBlockState bState = getPostBlock(x, h + 1, z, originX, originZ, currentData);

            if (bState.isOccluding() && bState.isSolid()) {
                if (bcState.isSolid()) {
                    setPostBlock(x, h, z, bState, originX, originZ, currentData);
                    h--;
                }
            }
        } else if (!river) {
            // Potholes
            g = 0;
            g += ha == h + 1 ? 1 : 0;
            g += hb == h + 1 ? 1 : 0;
            g += hc == h + 1 ? 1 : 0;
            g += hd == h + 1 ? 1 : 0;

            if (g >= 4) {
                PlatformBlockState ba = getPostBlock(x, ha, z, originX, originZ, currentData);
                PlatformBlockState bb = getPostBlock(x, hb, z, originX, originZ, currentData);
                PlatformBlockState bc = getPostBlock(x, hc, z, originX, originZ, currentData);
                PlatformBlockState bd = getPostBlock(x, hd, z, originX, originZ, currentData);
                g = 0;
                g = B.isSolid(ba) ? g + 1 : g;
                g = B.isSolid(bb) ? g + 1 : g;
                g = B.isSolid(bc) ? g + 1 : g;
                g = B.isSolid(bd) ? g + 1 : g;

                if (g >= 3) {
                    setPostBlock(x, h + 1, z, getPostBlock(x, h, z, originX, originZ, currentData), originX, originZ, currentData);
                    h++;
                }
            }
        }

        // Wall Patcher
        IrisBiome biome = context.getBiome().get(currentPostX, currentPostZ);

        if (walls && !river) {
            if (!biome.getWall().getPalette().isEmpty()) {
                if (ha < h - 2 || hb < h - 2 || hc < h - 2 || hd < h - 2) {
                    boolean brokeGround = false;
                    int max = Math.abs(Math.max(h - ha, Math.max(h - hb, Math.max(h - hc, h - hd))));

                    for (int i = h; i > h - max; i--) {
                        PlatformBlockState d = biome.getWall().get(rng, x + i, i + h, z + i, getData());

                        if (d != null) {
                            if (isAirOrWater(x, i, z, originX, originZ, currentData)) {
                                if (brokeGround) {
                                    break;
                                }

                                continue;
                            }

                            setPostBlock(x, i, z, d, originX, originZ, currentData);
                            brokeGround = true;
                        }
                    }
                }
            }
        }

        // Slab
        if (slabs && !river) {
            //@builder
            if ((ha == h + 1 && isSolidNonSlab(x + 1, ha, z, originX, originZ, currentData))
                    || (hb == h + 1 && isSolidNonSlab(x, hb, z + 1, originX, originZ, currentData))
                    || (hc == h + 1 && isSolidNonSlab(x - 1, hc, z, originX, originZ, currentData))
                    || (hd == h + 1 && isSolidNonSlab(x, hd, z - 1, originX, originZ, currentData)))
            //@done
            {
                IrisSlopeClip sc = biome.getSlab().getSlopeCondition();
                PlatformBlockState d = sc.isValid(getComplex().getSlopeStream().get(x, z)) ? biome.getSlab().get(rng, x, h, z, getData()) : null;

                if (d != null) {
                    boolean cancel = B.isAir(d);

                    if (IrisProceduralBlocks.materialKey(d).equals("minecraft:snow") && h + 1 <= fluidHeight) {
                        cancel = true;
                    }

                    if (isSnowLayer(x, h, z, originX, originZ, currentData)) {
                        cancel = true;
                    }

                    if (!cancel && isAirOrWater(x, h + 1, z, originX, originZ, currentData)) {
                        setPostBlock(x, h + 1, z, d, originX, originZ, currentData);
                        h++;
                    }
                }
            }
        }

        // Waterlogging
        PlatformBlockState b = getPostBlock(x, h, z, originX, originZ, currentData);

        if (IrisProceduralBlocks.hasProperty(b, "waterlogged")) {
            boolean w = false;

            if (h <= fluidHeight + 1) {
                if (isWaterOrWaterlogged(x, h + 1, z, originX, originZ, currentData)) {
                    w = true;
                } else if ((isWaterOrWaterlogged(x + 1, h, z, originX, originZ, currentData) || isWaterOrWaterlogged(x - 1, h, z, originX, originZ, currentData) || isWaterOrWaterlogged(x, h, z + 1, originX, originZ, currentData) || isWaterOrWaterlogged(x, h, z - 1, originX, originZ, currentData))) {
                    w = true;
                }
            }

            if (w != "true".equals(IrisProceduralBlocks.propertyValue(b, "waterlogged"))) {
                setPostBlock(x, h, z, b.withProperty("waterlogged", String.valueOf(w)), originX, originZ, currentData);
            }
        }

        // Foliage
        b = getPostBlock(x, h + 1, z, originX, originZ, currentData);

        if (B.isVineBlock(b)) {
            PlatformBlockState result = b;
            int finalH = h + 1;

            for (String face : IrisProceduralBlocks.FACE_PROPERTIES) {
                if (!IrisProceduralBlocks.hasProperty(b, face)) {
                    continue;
                }
                int[] mod = IrisProceduralBlocks.faceOffset(face);
                PlatformBlockState d = getPostBlock(x + mod[0], finalH + mod[1], z + mod[2], originX, originZ, currentData);
                result = result.withProperty(face, String.valueOf(!B.isAir(d) && !B.isVineBlock(d)));
            }
            if (!result.equals(b)) {
                setPostBlock(x, h + 1, z, result, originX, originZ, currentData);
            }
        }

        if (B.isFoliage(b) || IrisProceduralBlocks.materialKey(b).equals("minecraft:dead_bush")) {
            PlatformBlockState onto = getPostBlock(x, h, z, originX, originZ, currentData);

            if (!B.canPlaceOnto(b, onto) && !B.isDecorant(b)) {
                setPostBlock(x, h + 1, z, AIR.get(), originX, originZ, currentData);
            }
        }
    }

    public boolean isAir(int x, int y, int z, int originX, int originZ, Hunk<PlatformBlockState> currentData) {
        String material = IrisProceduralBlocks.materialKey(getPostBlock(x, y, z, originX, originZ, currentData));
        return material.equals("minecraft:air") || material.equals("minecraft:cave_air");
    }

    public boolean hasGravity(int x, int y, int z, int originX, int originZ, Hunk<PlatformBlockState> currentData) {
        String material = IrisProceduralBlocks.materialKey(getPostBlock(x, y, z, originX, originZ, currentData));
        return material.equals("minecraft:sand") || material.equals("minecraft:red_sand") || material.endsWith("_concrete_powder");
    }

    public boolean isSolid(int x, int y, int z, int originX, int originZ, Hunk<PlatformBlockState> currentData) {
        PlatformBlockState d = getPostBlock(x, y, z, originX, originZ, currentData);
        return B.isSolid(d) && !B.isVineBlock(d);
    }

    public boolean isSolidNonSlab(int x, int y, int z, int originX, int originZ, Hunk<PlatformBlockState> currentData) {
        PlatformBlockState d = getPostBlock(x, y, z, originX, originZ, currentData);
        return B.isSolid(d) && !IrisProceduralBlocks.materialKey(d).endsWith("_slab");
    }

    public boolean isAirOrWater(int x, int y, int z, int originX, int originZ, Hunk<PlatformBlockState> currentData) {
        String material = IrisProceduralBlocks.materialKey(getPostBlock(x, y, z, originX, originZ, currentData));
        return material.equals("minecraft:water") || material.equals("minecraft:air") || material.equals("minecraft:cave_air");
    }

    public boolean isSlab(int x, int y, int z, int originX, int originZ, Hunk<PlatformBlockState> currentData) {
        return IrisProceduralBlocks.materialKey(getPostBlock(x, y, z, originX, originZ, currentData)).endsWith("_slab");
    }

    public boolean isSnowLayer(int x, int y, int z, int originX, int originZ, Hunk<PlatformBlockState> currentData) {
        return IrisProceduralBlocks.materialKey(getPostBlock(x, y, z, originX, originZ, currentData)).equals("minecraft:snow");
    }

    public boolean isWater(int x, int y, int z, int originX, int originZ, Hunk<PlatformBlockState> currentData) {
        return IrisProceduralBlocks.materialKey(getPostBlock(x, y, z, originX, originZ, currentData)).equals("minecraft:water");
    }

    public boolean isWaterOrWaterlogged(int x, int y, int z, int originX, int originZ, Hunk<PlatformBlockState> currentData) {
        PlatformBlockState d = getPostBlock(x, y, z, originX, originZ, currentData);
        return IrisProceduralBlocks.materialKey(d).equals("minecraft:water") || "true".equals(IrisProceduralBlocks.propertyValue(d, "waterlogged"));
    }

    public boolean isLiquid(int x, int y, int z, int originX, int originZ, Hunk<PlatformBlockState> currentData) {
        return IrisProceduralBlocks.hasProperty(getPostBlock(x, y, z, originX, originZ, currentData), "level");
    }

    public void setPostBlock(int x, int y, int z, PlatformBlockState d, int originX, int originZ, Hunk<PlatformBlockState> currentData) {
        int lx = x - originX;
        int lz = z - originZ;

        if (lx < 0 || lz < 0 || lx >= currentData.getWidth() || lz >= currentData.getDepth() || y < 0 || y >= currentData.getHeight()) {
            return;
        }

        currentData.set(lx, y, lz, d);
    }

    /**
     * Neighbour columns can sit one block outside this chunk, and the blocks of an adjacent chunk are not
     * available while generating this one. Resolve the hunk index relative to the chunk origin and let
     * getClosest clamp to the nearest in-chunk column instead of wrapping to the opposite chunk edge.
     */
    public PlatformBlockState getPostBlock(int x, int y, int z, int originX, int originZ, Hunk<PlatformBlockState> h) {
        PlatformBlockState b = h.getClosest(x - originX, y, z - originZ);

        return b == null ? AIR.get() : b;
    }
}
