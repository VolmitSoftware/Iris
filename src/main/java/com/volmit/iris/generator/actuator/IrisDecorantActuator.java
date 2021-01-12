package com.volmit.iris.generator.actuator;

import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;
import com.volmit.iris.generator.decorator.IrisCeilingDecorator;
import com.volmit.iris.generator.decorator.IrisSeaSurfaceDecorator;
import com.volmit.iris.generator.decorator.IrisShoreLineDecorator;
import com.volmit.iris.generator.decorator.IrisSurfaceDecorator;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineAssignedActuator;
import com.volmit.iris.scaffold.engine.EngineDecorator;
import com.volmit.iris.scaffold.hunk.Hunk;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.function.Predicate;

public class IrisDecorantActuator extends EngineAssignedActuator<BlockData>
{
    private static final Predicate<BlockData> PREDICATE_SOLID = (b) -> b != null && !b.getMaterial().isAir() && !b.getMaterial().equals(Material.WATER) && !b.getMaterial().equals(Material.LAVA);
    private final RNG rng;
    @Getter
    private final EngineDecorator surfaceDecorator;
    @Getter
    private final EngineDecorator ceilingDecorator;
    @Getter
    private final EngineDecorator seaSurfaceDecorator;
    @Getter
    private final EngineDecorator shoreLineDecorator;
    private final boolean shouldRay;

    public IrisDecorantActuator(Engine engine) {
        super(engine, "Decorant");
        shouldRay = shouldRayDecorate();
        this.rng = new RNG(engine.getTarget().getWorld().getSeed());
        surfaceDecorator = new IrisSurfaceDecorator(getEngine());
        ceilingDecorator = new IrisCeilingDecorator(getEngine());
        seaSurfaceDecorator = new IrisSeaSurfaceDecorator(getEngine());
        shoreLineDecorator = new IrisShoreLineDecorator(getEngine());
    }

    @Override
    public void onActuate(int x, int z, Hunk<BlockData> output) {
        if(!getEngine().getDimension().isDecorate())
        {
            return;
        }

        PrecisionStopwatch p = PrecisionStopwatch.start();
        boolean solid;
        int emptyFor = 0;
        int lastSolid = 0;
        int j, realX, realZ, height;
        IrisBiome biome, cave;

        for(int i = 0; i < output.getWidth(); i++)
        {
            for(j = 0; j < output.getDepth(); j++)
            {
                realX = (int) Math.round(modX(x + i));
                realZ = (int) Math.round(modZ(z + j));
                height = (int) Math.round(getComplex().getHeightStream().get(realX, realZ));
                biome = getComplex().getTrueBiomeStream().get(realX, realZ);
                cave = shouldRay ? getComplex().getCaveBiomeStream().get(realX, realZ) : null;

                if(biome.getDecorators().isEmpty() && (cave == null || cave.getDecorators().isEmpty()))
                {
                    continue;
                }

                if(height == getDimension().getFluidHeight())
                {
                    getShoreLineDecorator().decorate(i, j,
                            realX, (int) Math.round(modX(x + i+1)), (int) Math.round(modX(x + i-1)),
                            realZ, (int) Math.round(modZ(z + j+1)), (int) Math.round(modZ(z + j-1)),
                            output, biome, height, getEngine().getHeight() - height);
                }

                else if(height < getDimension().getFluidHeight())
                {
                    getSeaSurfaceDecorator().decorate(i, j, realX, realZ, output, biome, getDimension().getFluidHeight(), getEngine().getHeight() - getDimension().getFluidHeight());
                }

                getSurfaceDecorator().decorate(i, j, realX, realZ, output, biome, height, getEngine().getHeight() - height);

                if(cave != null && cave.getDecorators().isNotEmpty())
                {
                    for(int k = height; k > 0; k--)
                    {
                        solid = PREDICATE_SOLID.test(output.get(i, k, j));

                        if(solid)
                        {
                            lastSolid = k;
                        }

                        else {
                            emptyFor++;
                        }

                        if(solid && emptyFor > 0)
                        {
                            getCeilingDecorator().decorate(i, j, realX, realZ, output, cave, lastSolid, emptyFor);
                            getSurfaceDecorator().decorate(i, j, realX, realZ, output, cave, k, emptyFor);
                            emptyFor = 0;
                        }
                    }
                }
            }
        }

        getEngine().getMetrics().getDecoration().put(p.getMilliseconds());
    }

    private boolean shouldRayDecorate()
    {
        return getEngine().getDimension().isCarving() || getEngine().getDimension().isCaves() || getEngine().getDimension().isRavines();
    }
}
