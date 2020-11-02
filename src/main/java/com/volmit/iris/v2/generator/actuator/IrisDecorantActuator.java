package com.volmit.iris.v2.generator.actuator;

import com.volmit.iris.util.B;
import com.volmit.iris.util.KList;
import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.engine.EngineAssignedActuator;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDecorator;
import com.volmit.iris.util.RNG;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.util.function.Predicate;

public class IrisDecorantActuator extends EngineAssignedActuator<BlockData>
{
    private static final Predicate<BlockData> PREDICATE_SOLID = (b) -> b != null && !b.getMaterial().isAir() && !b.getMaterial().equals(Material.WATER) && !b.getMaterial().equals(Material.LAVA);
    private final RNG rng;

    public IrisDecorantActuator(Engine engine) {
        super(engine, "Decorant");
        this.rng = new RNG(engine.getTarget().getWorld().getSeed());
    }

    private boolean shouldDecorate()
    {
        return getEngine().getDimension().isDecorate();
    }

    private boolean shouldRayDecorate()
    {
        return getEngine().getDimension().isCarving() || getEngine().getDimension().isCaves() || getEngine().getDimension().isVanillaCaves() || getEngine().getDimension().isRavines();
    }

    @Override
    public void onActuate(int x, int z, Hunk<BlockData> output) {
        if(!shouldDecorate())
        {
            return;
        }

        if(shouldRayDecorate())
        {
            output.iterateSurfaces2D(0, PREDICATE_SOLID, (hunkRelativeX, hunkRelativeZ, hunkOffsetX, hunkOffsetZ, top, bottom, lastBottom, h) ->  decorateLayer(x, z, hunkRelativeX, hunkRelativeZ, hunkOffsetX, hunkOffsetZ,top,bottom,lastBottom,h));
        }

        else
        {
            int he;
            for(int i = 0; i < output.getWidth(); i++)
            {
                for(int j = 0; j < output.getDepth(); j++)
                {
                    he = getComplex().getHeightFluidStream().get(x + i, z + j).intValue();
                    decorateLayer(x, z, i, j, 0, 0, he, 0, getEngine().getHeight(), output);
                }
            }
        }
    }

    private void decorateLayer(int x, int z, int hunkRelativeX, int hunkRelativeZ, int hunkOffsetX, int hunkOffsetZ, int top, int bottom,int  lastBottom, Hunk<BlockData> h)
    {
        int realX = x + hunkOffsetX + hunkRelativeX;
        int realZ = z + hunkOffsetZ + hunkRelativeZ;
        IrisBiome b = getComplex().getTrueBiomeStream().get(realX, realZ);

        if(b.getDecorators().isEmpty())
        {
            return;
        }

        RNG g = rng.nextParallelRNG(realX >> 4).nextParallelRNG(realZ >> 4);
        boolean surface = lastBottom == -1;
        int floor = top + 1;
        int ceiling = lastBottom == -1 ? floor < getDimension().getFluidHeight() ? getDimension().getFluidHeight() : getEngine().getHeight() : lastBottom - 1;
        int height = ceiling - floor;

        if(height < 2)
        {
            return;
        }

        if(b.isShore() && floor <= getDimension().getFluidHeight())
        {
            return;
        }

        if(surface)
        {
            IrisDecorator deco = getComplex().getTerrainSurfaceDecoration().get(realX, realZ);

            if(deco != null)
            {
                if(deco.isStacking())
                {
                    int stack = Math.min(g.i(deco.getStackMin(), deco.getStackMax()), height);
                    boolean fail = false;

                    for(int i = 0; i < stack; i++)
                    {
                        BlockData v = deco.getBlockData100(b, rng, realX - i, realZ + i, getData());
                        if(i == 0 && (deco.isForcePlace() || canGoOn(h.get(hunkRelativeX, i+floor-1, hunkRelativeZ), v)))
                        {
                            h.set(hunkRelativeX, i+floor, hunkRelativeZ, v);
                            continue;
                        }

                        else if(i == 0)
                        {
                            fail = true;
                            break;
                        }

                        h.set(hunkRelativeX, i + floor, hunkRelativeZ, v);
                    }

                    if(!fail && deco.getTopPalette().isNotEmpty())
                    {
                        h.set(hunkRelativeX, stack + floor - 1, hunkRelativeZ, deco.getBlockDataForTop(b, rng, realX - stack, realZ + stack, getData()));
                    }
                }

                else
                {
                    BlockData v = deco.getBlockData100(b, rng, realX, realZ, getData());
                    if(deco.isForcePlace() || canGoOn(h.get(hunkRelativeX, floor-1, hunkRelativeZ), v))
                    {
                        h.set(hunkRelativeX, floor, hunkRelativeZ, v);
                    }
                }
            }
        }

        else
        {
            IrisBiome cave = getComplex().getCaveBiomeStream().get(realX, realZ);
            IrisDecorator deco = getComplex().getTerrainCaveSurfaceDecoration().get(realX, realZ);

            if(deco != null)
            {
                if(deco.isStacking())
                {
                    int stack = Math.min(g.i(deco.getStackMin(), deco.getStackMax()), height);
                    boolean fail = false;

                    for(int i = 0; i < stack; i++)
                    {
                        BlockData v = deco.getBlockData100(b, rng, realX - i, realZ + i, getData());
                        if(i == 0 && (deco.isForcePlace() || canGoOn(h.get(hunkRelativeX, i+floor-1, hunkRelativeZ), v)))
                        {
                            h.set(hunkRelativeX, i+floor, hunkRelativeZ, v);
                            continue;
                        }

                        else if(i == 0)
                        {
                            fail = true;
                            break;
                        }

                        h.set(hunkRelativeX, i + floor, hunkRelativeZ, v);
                    }

                    if(deco.getTopPalette().isNotEmpty())
                    {
                        h.set(hunkRelativeX, stack + floor - 1, hunkRelativeZ, deco.getBlockDataForTop(b, rng, realX - stack, realZ + stack, getData()));
                    }
                }

                else
                {
                    BlockData v = deco.getBlockData100(b, rng, realX, realZ, getData());
                    if(deco.isForcePlace() || canGoOn(h.get(hunkRelativeX, floor-1, hunkRelativeZ), v))
                    {
                        h.set(hunkRelativeX, floor, hunkRelativeZ, v);
                    }
                }
            }

            int maxCeiling = lastBottom - top - 1;

            if(maxCeiling > 0)
            {
                KList<BlockData> v = cave.generateLayers(realX, realZ, rng, maxCeiling, top, getData());

                if(!v.isEmpty())
                {
                    for(int i = 0; i < v.size(); i++)
                    {
                        h.set(hunkRelativeX, top+i, hunkRelativeZ, v.get(i));
                    }
                }
            }

            IrisDecorator cdeco = getComplex().getTerrainCaveCeilingDecoration().get(realX, realZ);

            if(cdeco != null)
            {
                if(cdeco.isStacking())
                {
                    int stack = Math.min(g.i(cdeco.getStackMin(), cdeco.getStackMax()), height);

                    for(int i = 0; i < stack; i++)
                    {
                        h.set(hunkRelativeX, -i + ceiling, hunkRelativeZ, cdeco.getBlockData100(b, rng, realX - i, realZ + i, getData()));
                    }

                    if(cdeco.getTopPalette().isNotEmpty())
                    {
                        h.set(hunkRelativeX, -stack + ceiling - 1, hunkRelativeZ, cdeco.getBlockDataForTop(b, rng, realX - stack, realZ + stack, getData()));
                    }
                }

                else
                {
                    h.set(hunkRelativeX, ceiling, hunkRelativeZ, cdeco.getBlockData100(b, rng, realX, realZ, getData()));
                }
            }

            int maxFloor = Math.min(8, bottom-1);

            KList<BlockData> v = cave.generateLayers(realX, realZ, rng, maxFloor, bottom, getData());

            if(!v.isEmpty())
            {
                for(int i = 0; i < v.size(); i++)
                {
                    if(bottom-i < 2)
                    {
                        break;
                    }

                    BlockData bk = h.get(hunkRelativeX, bottom-i, hunkRelativeZ);

                    if(PREDICATE_SOLID.test(bk))
                    {
                        h.set(hunkRelativeX, bottom-i, hunkRelativeZ, v.get(i));
                    }

                    else
                    {
                        break;
                    }
                }
            }
        }
    }

    private boolean canGoOn(BlockData decorant, BlockData atop)
    {
        return B.canPlaceOnto(decorant.getMaterial(), atop.getMaterial());
    }
}
