package com.volmit.iris.v2.generator;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisDimensionIndex;
import com.volmit.iris.util.KList;
import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.engine.EngineCompound;
import com.volmit.iris.v2.scaffold.engine.EngineData;
import com.volmit.iris.v2.scaffold.engine.EngineTarget;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.v2.scaffold.parallel.BurstExecutor;
import com.volmit.iris.v2.scaffold.parallel.MultiBurst;
import lombok.Getter;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class IrisEngineCompound implements EngineCompound {
    @Getter
    private final World world;

    @Getter
    private final EngineData engineMetadata;

    private final Engine[] engines;

    @Getter
    private final MultiBurst burster;

    public IrisEngineCompound(World world, IrisDimension rootDimension, IrisDataManager data, int maximumThreads)
    {
        this.world = world;
        engineMetadata = EngineData.load(getEngineMetadataFile());
        saveEngineMetadata();

        if(rootDimension.getDimensionalComposite().isEmpty())
        {
            burster = null;
            engines = new Engine[]{new IrisEngine(new EngineTarget(world, rootDimension, data, 256, maximumThreads))};
        }

        else
        {
            double totalWeight = 0D;
            engines = new Engine[rootDimension.getDimensionalComposite().size()];
            burster = engines.length > 1 ? new MultiBurst(engines.length) : null;
            int threadDist = (Math.max(2, maximumThreads - engines.length)) / engines.length;

            if((threadDist * engines.length) + engines.length > maximumThreads)
            {
                Iris.warn("Using " + ((threadDist * engines.length) + engines.length) + " threads instead of the configured " + maximumThreads + " maximum thread count due to the requirements of this dimension!");
            }

            for(IrisDimensionIndex i : rootDimension.getDimensionalComposite())
            {
                totalWeight += i.getWeight();
            }

            for(int i = 0; i < engines.length; i++)
            {
                IrisDimensionIndex index = rootDimension.getDimensionalComposite().get(i);
                IrisDimension dimension = data.getDimensionLoader().load(index.getDimension());
                engines[i] = new IrisEngine(new EngineTarget(world, dimension, data.copy(), (int)Math.floor(256D * (index.getWeight() / totalWeight)), index.isInverted(), threadDist));
            }
        }
    }

    private File getEngineMetadataFile() {
        return new File(world.getWorldFolder(), "iris/engine-metadata.json");
    }

    @Override
    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes)
    {
        if(engines.length == 1 && !getEngine(0).getTarget().isInverted())
        {
            engines[0].generate(x, z, blocks, biomes);
        }

        else
        {
            int i;
            int offset = 0;
            BurstExecutor e = burster.burst();
            Runnable[] insert = new Runnable[engines.length];

            for(i = 0; i < engines.length; i++)
            {
                AtomicInteger index = new AtomicInteger(i);
                Engine engine = engines[i];
                int height = engine.getTarget().getHeight();
                AtomicReference<Hunk<BlockData>> cblock = new AtomicReference<>(blocks.croppedView(0, offset, 0, 16, offset+height, 16));
                AtomicReference<Hunk<Biome>> cbiome = new AtomicReference<>(biomes.croppedView(0, offset, 0, 16, offset+height, 16));
                cblock.set(engine.getTarget().isInverted() ? cblock.get().invertY() : cblock.get());
                cbiome.set(engine.getTarget().isInverted() ? cbiome.get().invertY() : cbiome.get());
                e.queue(() -> {
                    generate(x, z, cblock.get(), cbiome.get());
                    synchronized (insert)
                    {
                        insert[index.get()] = () -> {
                            blocks.insert(cblock.get());
                            biomes.insert(cbiome.get());
                        };
                    }
                });

                offset += height;
            }

            e.complete();

            for(i = 0; i < insert.length; i++)
            {
                insert[i].run();
            }
        }
    }


    @Override
    public int getSize() {
        return engines.length;
    }

    @Override
    public Engine getEngine(int index) {
        return engines[index];
    }

    @Override
    public void saveEngineMetadata() {
        engineMetadata.save(getEngineMetadataFile());
    }
}
