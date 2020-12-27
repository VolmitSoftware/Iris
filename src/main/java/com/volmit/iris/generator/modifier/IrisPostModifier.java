package com.volmit.iris.generator.modifier;

import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineAssignedModifier;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.parallel.BurstExecutor;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.B;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Slab;

public class IrisPostModifier extends EngineAssignedModifier<BlockData> {
    private static final BlockData AIR = B.get("CAVE_AIR");
    private static final BlockData WATER = B.get("WATER");
    private final RNG rng;

    public IrisPostModifier(Engine engine) {
        super(engine, "Post");
        rng = new RNG(getEngine().getWorld().getSeed()+12938).nextParallelRNG(28348777);
    }

    @Override
    public void onModify(int x, int z, Hunk<BlockData> output) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        BurstExecutor b = MultiBurst.burst.burst(output.getWidth() * output.getDepth());
        int i, j;
        for(i = 0; i < output.getWidth(); i++)
        {
            int ii = i;
            for(j = 0; j < output.getDepth(); j++)
            {
                int jj = j;
                b.queue(() -> post(ii, jj, output, ii+x, jj+z));
            }
        }
        b.complete();
        getEngine().getMetrics().getPost().put(p.getMilliseconds());
    }

    private void post(int currentPostX, int currentPostZ, Hunk<BlockData> currentData, int x, int z) {

        int h = getFramework().getEngineParallax().trueHeight(x, z);
        int ha = getFramework().getEngineParallax().trueHeight(x + 1, z);
        int hb = getFramework().getEngineParallax().trueHeight(x, z + 1);
        int hc = getFramework().getEngineParallax().trueHeight(x - 1, z);
        int hd = getFramework().getEngineParallax().trueHeight(x, z - 1);

        // Floating Nibs
        int g = 0;

        if(h < 1)
        {
            return;
        }

        g += ha < h - 1 ? 1 : 0;
        g += hb < h - 1 ? 1 : 0;
        g += hc < h - 1 ? 1 : 0;
        g += hd < h - 1 ? 1 : 0;

        if(g == 4 && isAir(x, h - 1, z, currentPostX, currentPostZ, currentData))
        {
            setPostBlock(x, h, z, AIR, currentPostX, currentPostZ, currentData);

            for(int i = h - 1; i > 0; i--)
            {
                if(!isAir(x, i, z, currentPostX, currentPostZ, currentData))
                {
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

        if(g >= 4)
        {
            BlockData bc = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData);
            BlockData b = getPostBlock(x, h + 1, z, currentPostX, currentPostZ, currentData);
            Material m = bc.getMaterial();

            if((b.getMaterial().isOccluding() && b.getMaterial().isSolid()))
            {
                if(m.isSolid())
                {
                    setPostBlock(x, h, z, b, currentPostX, currentPostZ, currentData);
                    h--;
                }
            }
        }

        else
        {
            // Potholes
            g = 0;
            g += ha == h + 1 ? 1 : 0;
            g += hb == h + 1 ? 1 : 0;
            g += hc == h + 1 ? 1 : 0;
            g += hd == h + 1 ? 1 : 0;

            if(g >= 4)
            {
                BlockData ba = getPostBlock(x, ha, z, currentPostX, currentPostZ, currentData);
                BlockData bb = getPostBlock(x, hb, z, currentPostX, currentPostZ, currentData);
                BlockData bc = getPostBlock(x, hc, z, currentPostX, currentPostZ, currentData);
                BlockData bd = getPostBlock(x, hd, z, currentPostX, currentPostZ, currentData);
                g = 0;
                g = B.isSolid(ba) ? g + 1 : g;
                g = B.isSolid(bb) ? g + 1 : g;
                g = B.isSolid(bc) ? g + 1 : g;
                g = B.isSolid(bd) ? g + 1 : g;

                if(g >= 3)
                {
                    setPostBlock(x, h + 1, z, getPostBlock(x, h, z, currentPostX, currentPostZ, currentData), currentPostX, currentPostZ, currentData);
                    h++;
                }
            }
        }

        // Wall Patcher
        IrisBiome biome = getComplex().getTrueBiomeStream().get(x,z);

        if(getDimension().isPostProcessingWalls())
        {
            if(!biome.getWall().getPalette().isEmpty())
            {
                if(ha < h - 2 || hb < h - 2 || hc < h - 2 || hd < h - 2)
                {
                    boolean brokeGround = false;
                    int max = Math.abs(Math.max(h - ha, Math.max(h - hb, Math.max(h - hc, h - hd))));

                    for(int i = h; i > h - max; i--)
                    {
                        BlockData d = biome.getWall().get(rng, x + i, i + h, z + i, getData());

                        if(d != null)
                        {
                            if(isAirOrWater(x, i, z, currentPostX, currentPostZ, currentData))
                            {
                                if(brokeGround)
                                {
                                    break;
                                }

                                continue;
                            }

                            setPostBlock(x, i, z, d, currentPostX, currentPostZ, currentData);
                            brokeGround = true;
                        }
                    }
                }
            }
        }

        // Slab
        if(getDimension().isPostProcessingSlabs())
        {
            //@builder
            if((ha == h + 1 && isSolidNonSlab(x + 1, ha, z, currentPostX, currentPostZ, currentData))
                    || (hb == h + 1 && isSolidNonSlab(x, hb, z + 1, currentPostX, currentPostZ, currentData))
                    || (hc == h + 1 && isSolidNonSlab(x - 1, hc, z, currentPostX, currentPostZ, currentData))
                    || (hd == h + 1 && isSolidNonSlab(x, hd, z - 1, currentPostX, currentPostZ, currentData)))
            //@done
            {
                BlockData d = biome.getSlab().get(rng, x, h, z, getData());

                if(d != null)
                {
                    boolean cancel = false;

                    if(B.isAir(d))
                    {
                        cancel = true;
                    }

                    if(d.getMaterial().equals(Material.SNOW) && h + 1 <= getDimension().getFluidHeight())
                    {
                        cancel = true;
                    }

                    if(isSnowLayer(x, h, z, currentPostX, currentPostZ, currentData))
                    {
                        cancel = true;
                    }

                    if(!cancel && isAirOrWater(x, h + 1, z, currentPostX, currentPostZ, currentData))
                    {
                        setPostBlock(x, h + 1, z, d, currentPostX, currentPostZ, currentData);
                        h++;
                    }
                }
            }
        }

        // Waterlogging
        BlockData b = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData);

        if(b instanceof Waterlogged)
        {
            Waterlogged ww = (Waterlogged) b.clone();
            boolean w = false;

            if (h <= getDimension().getFluidHeight()+1) {
                if(isWaterOrWaterlogged(x, h + 1, z, currentPostX, currentPostZ, currentData))
                {
                    w = true;
                }

                else if((isWaterOrWaterlogged(x + 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x - 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z + 1, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z - 1, currentPostX, currentPostZ, currentData)))
                {
                    w = true;
                }
            }

            if(w != ww.isWaterlogged())
            {
                ww.setWaterlogged(w);
                setPostBlock(x, h, z, ww, currentPostX, currentPostZ, currentData);
            }
        }

        else if(b.getMaterial().equals(Material.AIR) && h <= getDimension().getFluidHeight())
        {
            if((isWaterOrWaterlogged(x + 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x - 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z + 1, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z - 1, currentPostX, currentPostZ, currentData)))
            {
                setPostBlock(x, h, z, WATER, currentPostX, currentPostZ, currentData);
            }
        }

        // Foliage
        b = getPostBlock(x, h + 1, z, currentPostX, currentPostZ, currentData);

        if(B.isFoliage(b) || b.getMaterial().equals(Material.DEAD_BUSH))
        {
            Material onto = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData).getMaterial();

            if(!B.canPlaceOnto(b.getMaterial(), onto))
            {
                setPostBlock(x, h + 1, z, AIR, currentPostX, currentPostZ, currentData);
            }
        }

        if(getDimension().isPostProcessCaves())
        {
            IrisBiome cave = getComplex().getCaveBiomeStream().get(x, z);

            if(cave != null)
            {
                for(CaveResult i : ((IrisCaveModifier)getFramework().getCaveModifier()).genCaves(x, z, 0, 0, null))
                {
                    if(i.getCeiling() >= currentData.getMax2DParallelism() || i.getFloor() < 0)
                    {
                        continue;
                    }

                    int f = i.getFloor();
                    int fa = nearestCaveFloor(f, x + 1, z, currentPostX, currentPostZ, currentData);
                    int fb = nearestCaveFloor(f, x, z + 1, currentPostX, currentPostZ, currentData);
                    int fc = nearestCaveFloor(f, x - 1, z, currentPostX, currentPostZ, currentData);
                    int fd = nearestCaveFloor(f, x, z - 1, currentPostX, currentPostZ, currentData);
                    int c = i.getCeiling();
                    int ca = nearestCaveCeiling(c, x + 1, z, currentPostX, currentPostZ, currentData);
                    int cb = nearestCaveCeiling(c, x, z + 1, currentPostX, currentPostZ, currentData);
                    int cc = nearestCaveCeiling(c, x - 1, z, currentPostX, currentPostZ, currentData);
                    int cd = nearestCaveCeiling(c, x, z - 1, currentPostX, currentPostZ, currentData);

                    // Cave Nibs
                    g = 0;
                    g += fa == f - 1 ? 1 : 0;
                    g += fb == f - 1 ? 1 : 0;
                    g += fc == f - 1 ? 1 : 0;
                    g += fd == f - 1 ? 1 : 0;

                    if(g >= 4)
                    {
                        BlockData bc = getPostBlock(x, f, z, currentPostX, currentPostZ, currentData);
                        b = getPostBlock(x, f + 1, z, currentPostX, currentPostZ, currentData);
                        Material m = bc.getMaterial();

                        if(m.isSolid())
                        {
                            setPostBlock(x, f, z, b, currentPostX, currentPostZ, currentData);
                            h--;
                        }
                    }

                    else
                    {
                        // Cave Potholes
                        g = 0;
                        g += fa == f + 1 ? 1 : 0;
                        g += fb == f + 1 ? 1 : 0;
                        g += fc == f + 1 ? 1 : 0;
                        g += fd == f + 1 ? 1 : 0;

                        if(g >= 4)
                        {
                            BlockData ba = getPostBlock(x, fa, z, currentPostX, currentPostZ, currentData);
                            BlockData bb = getPostBlock(x, fb, z, currentPostX, currentPostZ, currentData);
                            BlockData bc = getPostBlock(x, fc, z, currentPostX, currentPostZ, currentData);
                            BlockData bd = getPostBlock(x, fd, z, currentPostX, currentPostZ, currentData);
                            g = 0;
                            g = B.isSolid(ba) ? g + 1 : g;
                            g = B.isSolid(bb) ? g + 1 : g;
                            g = B.isSolid(bc) ? g + 1 : g;
                            g = B.isSolid(bd) ? g + 1 : g;

                            if(g >= 4)
                            {
                                setPostBlock(x, f + 1, z, getPostBlock(x, f, z, currentPostX, currentPostZ, currentData), currentPostX, currentPostZ, currentData);
                                h++;
                            }
                        }
                    }

                    if(getDimension().isPostProcessingSlabs())
                    {
                        //@builder
                        if((fa == f + 1 && isSolidNonSlab(x + 1, fa, z, currentPostX, currentPostZ, currentData))
                                || (fb == f + 1 && isSolidNonSlab(x, fb, z + 1, currentPostX, currentPostZ, currentData))
                                || (fc == f + 1 && isSolidNonSlab(x - 1, fc, z, currentPostX, currentPostZ, currentData))
                                || (fd == f + 1 && isSolidNonSlab(x, fd, z - 1, currentPostX, currentPostZ, currentData)))
                        //@done
                        {
                            BlockData d = cave.getSlab().get(rng, x, f, z, getData());

                            if(d != null)
                            {
                                boolean cancel = false;

                                if(B.isAir(d))
                                {
                                    cancel = true;
                                }

                                if(d.getMaterial().equals(Material.SNOW) && f + 1 <= getDimension().getFluidHeight())
                                {
                                    cancel = true;
                                }

                                if(isSnowLayer(x, f, z, currentPostX, currentPostZ, currentData))
                                {
                                    cancel = true;
                                }

                                if(!cancel && isAirOrWater(x, f + 1, z, currentPostX, currentPostZ, currentData))
                                {
                                    setPostBlock(x, f + 1, z, d, currentPostX, currentPostZ, currentData);
                                }
                            }
                        }

                        //@builder
                        if((ca == c - 1 && isSolidNonSlab(x + 1, ca, z, currentPostX, currentPostZ, currentData))
                                || (cb == c - 1 && isSolidNonSlab(x, cb, z + 1, currentPostX, currentPostZ, currentData))
                                || (cc == c - 1 && isSolidNonSlab(x - 1, cc, z, currentPostX, currentPostZ, currentData))
                                || (cd == c - 1 && isSolidNonSlab(x, cd, z - 1, currentPostX, currentPostZ, currentData)))
                        //@done
                        {
                            BlockData d = cave.getSlab().get(rng, x, c, z, getData());

                            if(d != null)
                            {
                                boolean cancel = false;

                                if(B.isAir(d))
                                {
                                    cancel = true;
                                }

                                if(!(d instanceof Slab))
                                {
                                    cancel = true;
                                }

                                if(isSnowLayer(x, c, z, currentPostX, currentPostZ, currentData))
                                {
                                    cancel = true;
                                }

                                if(!cancel && isAirOrWater(x, c, z, currentPostX, currentPostZ, currentData))
                                {
                                    Slab slab = (Slab) d.clone();
                                    slab.setType(Slab.Type.TOP);
                                    setPostBlock(x, c, z, slab, currentPostX, currentPostZ, currentData);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private int nearestCaveFloor(int floor, int x, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData)
    {
        if(floor >= currentData.getHeight())
        {
            return currentData.getHeight()-1;
        }

        if(B.isAir(getPostBlock(x, floor, z, currentPostX, currentPostZ, currentData)))
        {
            if(B.isAir(getPostBlock(x, floor - 1, z, currentPostX, currentPostZ, currentData)))
            {
                return floor - 2;
            }

            return floor - 1;
        }

        else
        {
            if(!B.isAir(getPostBlock(x, floor + 1, z, currentPostX, currentPostZ, currentData)))
            {
                if(!B.isAir(getPostBlock(x, floor + 2, z, currentPostX, currentPostZ, currentData)))
                {
                    return floor + 2;
                }

                return floor + 1;
            }

            return floor;
        }
    }

    private int nearestCaveCeiling(int ceiling, int x, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData)
    {
        if(ceiling >= currentData.getHeight())
        {
            return currentData.getHeight()-1;
        }

        if(B.isAir(getPostBlock(x, ceiling, z, currentPostX, currentPostZ, currentData)))
        {
            if(B.isAir(getPostBlock(x, ceiling + 1, z, currentPostX, currentPostZ, currentData)))
            {
                return ceiling + 2;
            }

            return ceiling + 1;
        }

        else
        {
            if(!B.isAir(getPostBlock(x, ceiling - 1, z, currentPostX, currentPostZ, currentData)))
            {
                if(!B.isAir(getPostBlock(x, ceiling - 2, z, currentPostX, currentPostZ, currentData)))
                {
                    return ceiling - 2;
                }

                return ceiling - 1;
            }

            return ceiling;
        }
    }

    public boolean isAir(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData)
    {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().equals(Material.AIR) || d.getMaterial().equals(Material.CAVE_AIR);
    }

    public boolean hasGravity(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData)
    {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().equals(Material.SAND) || d.getMaterial().equals(Material.RED_SAND) || d.getMaterial().equals(Material.BLACK_CONCRETE_POWDER) || d.getMaterial().equals(Material.BLUE_CONCRETE_POWDER) || d.getMaterial().equals(Material.BROWN_CONCRETE_POWDER) || d.getMaterial().equals(Material.CYAN_CONCRETE_POWDER) || d.getMaterial().equals(Material.GRAY_CONCRETE_POWDER) || d.getMaterial().equals(Material.GREEN_CONCRETE_POWDER) || d.getMaterial().equals(Material.LIGHT_BLUE_CONCRETE_POWDER) || d.getMaterial().equals(Material.LIGHT_GRAY_CONCRETE_POWDER) || d.getMaterial().equals(Material.LIME_CONCRETE_POWDER) || d.getMaterial().equals(Material.MAGENTA_CONCRETE_POWDER) || d.getMaterial().equals(Material.ORANGE_CONCRETE_POWDER) || d.getMaterial().equals(Material.PINK_CONCRETE_POWDER) || d.getMaterial().equals(Material.PURPLE_CONCRETE_POWDER) || d.getMaterial().equals(Material.RED_CONCRETE_POWDER) || d.getMaterial().equals(Material.WHITE_CONCRETE_POWDER) || d.getMaterial().equals(Material.YELLOW_CONCRETE_POWDER);
    }

    public boolean isSolid(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData)
    {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().isSolid();
    }

    public boolean isSolidNonSlab(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData)
    {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().isSolid() && !(d instanceof Slab);
    }

    public boolean isAirOrWater(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData)
    {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().equals(Material.WATER) || d.getMaterial().equals(Material.AIR) || d.getMaterial().equals(Material.CAVE_AIR);
    }

    public boolean isSlab(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData)
    {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d instanceof Slab;
    }

    public boolean isSnowLayer(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData)
    {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().equals(Material.SNOW);
    }

    public boolean isWater(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData)
    {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().equals(Material.WATER);
    }

    public boolean isWaterOrWaterlogged(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData)
    {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().equals(Material.WATER) || (d instanceof Waterlogged && ((Waterlogged) d).isWaterlogged());
    }

    public boolean isLiquid(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData)
    {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d instanceof Levelled;
    }


    public void setPostBlock(int x, int y, int z, BlockData d, int currentPostX, int currentPostZ, Hunk<BlockData> currentData)
    {
        if(y < currentData.getHeight())
        {
            currentData.set(x & 15, y, z & 15, d);
        }
    }

    public BlockData getPostBlock(int x, int y, int z, int cpx, int cpz, Hunk<BlockData> h)
    {
        BlockData b = h.getClosest(x & 15, y, z & 15);

        return b == null ? AIR : b;
    }
}
