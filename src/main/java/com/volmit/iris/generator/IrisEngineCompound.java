package com.volmit.iris.generator;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisDimensionIndex;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineCompound;
import com.volmit.iris.scaffold.engine.EngineData;
import com.volmit.iris.scaffold.engine.EngineTarget;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.parallel.BurstExecutor;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.*;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.generator.BlockPopulator;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class IrisEngineCompound implements EngineCompound {
    @Getter
    private final World world;

    private final AtomicRollingSequence wallClock;

    @Getter
    private final EngineData engineMetadata;

    private final Engine[] engines;

    @Getter
    private final MultiBurst burster;

    @Getter
    private final KList<BlockPopulator> populators;

    @Getter
    private final IrisDimension rootDimension;

    @Getter
    private int threadCount;

    @Getter
    @Setter
    private boolean studio;

    public IrisEngineCompound(World world, IrisDimension rootDimension, IrisDataManager data, int maximumThreads)
    {
        wallClock = new AtomicRollingSequence(32);
        this.rootDimension = rootDimension;
        Iris.info("Initializing Engine Composite for " + world.getName());
        this.world = world;
        engineMetadata = EngineData.load(getEngineMetadataFile());
        engineMetadata.setDimension(rootDimension.getLoadKey());
        engineMetadata.setLastVersion(Iris.instance.getDescription().getVersion());
        saveEngineMetadata();
        populators = new KList<>();

        if(rootDimension.getDimensionalComposite().isEmpty())
        {
            burster = null;
            engines = new Engine[]{new IrisEngine(new EngineTarget(world, rootDimension, data, 256, maximumThreads), this, 0)};
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

            int buf = 0;

            for(int i = 0; i < engines.length; i++)
            {
                IrisDimensionIndex index = rootDimension.getDimensionalComposite().get(i);
                IrisDimension dimension = data.getDimensionLoader().load(index.getDimension());
                engines[i] = new IrisEngine(new EngineTarget(world, dimension, data.copy(), (int)Math.floor(256D * (index.getWeight() / totalWeight)), index.isInverted(), threadDist), this, i);
                engines[i].setMinHeight(buf);
                buf += engines[i].getHeight();
            }
        }

        for(Engine i : engines)
        {
            if(i instanceof BlockPopulator)
            {
                populators.add((BlockPopulator) i);
            }
        }

        Iris.instance.registerListener(this);
    }

    @EventHandler
    public void on(WorldSaveEvent e)
    {
        if(world != null &&e.getWorld().equals(world))
        {
            save();
        }
    }

    public void printMetrics(CommandSender sender)
    {
        KMap<String, Double> totals = new KMap<>();
        KMap<String, Double> weights = new KMap<>();
        double masterWallClock = wallClock.getAverage();

        for(int i = 0; i < getSize(); i++)
        {
            Engine e = getEngine(i);
            KMap<String, Double> timings = e.getMetrics().pull();
            double totalWeight = 0;
            double wallClock = e.getMetrics().getTotal().getAverage();

            for(double j : timings.values())
            {
                totalWeight += j;
            }

            for(String j : timings.k())
            {
                weights.put(e.getName() + "[" + e.getIndex() + "]." + j, (wallClock / totalWeight) * timings.get(j));
            }

            totals.put(e.getName() + "[" + e.getIndex() + "]", wallClock);
        }

        double mtotals = 0;

        for(double i : totals.values())
        {
            mtotals+=i;
        }

        for(String i : totals.k())
        {
            totals.put(i, (masterWallClock / mtotals) * totals.get(i));
        }

        double v = 0;

        for(double i : weights.values())
        {
            v+=i;
        }

        for(String i : weights.k())
        {
            weights.put(i, weights.get(i) / v);
        }

        sender.sendMessage("Total: " + C.BOLD + C.WHITE + Form.duration(masterWallClock, 0));

        for(String i : totals.k())
        {
            sender.sendMessage("  Engine " + C.UNDERLINE + C.GREEN + i + C.RESET + ": " + C.BOLD + C.WHITE +  Form.duration(totals.get(i), 0));
        }

        sender.sendMessage("Details: ");

        for(String i : weights.sortKNumber().reverse())
        {
            String befb = C.UNDERLINE +""+ C.GREEN + "" + i.split("\\Q[\\E")[0] + C.RESET + C.GRAY + "[";
            String num = C.GOLD + i.split("\\Q[\\E")[1].split("]")[0] + C.RESET + C.GRAY +  "].";
            String afb = C.ITALIC +""+ C.AQUA + i.split("\\Q]\\E")[1].substring(1) + C.RESET + C.GRAY;

            sender.sendMessage("  " + befb + num + afb + ": " + C.BOLD + C.WHITE +  Form.pc(weights.get(i), 0));
        }
    }

    private File getEngineMetadataFile() {
        return new File(world.getWorldFolder(), "iris/engine-metadata.json");
    }

    @Override
    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes)
    {
        PrecisionStopwatch p = PrecisionStopwatch.start();
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
                int doffset = offset;
                int height = engine.getTarget().getHeight();
                AtomicReference<Hunk<BlockData>> cblock = new AtomicReference<>(Hunk.newArrayHunk(16, height, 16));
                AtomicReference<Hunk<Biome>> cbiome = new AtomicReference<>(Hunk.newArrayHunk(16, height, 16));
                cblock.set(engine.getTarget().isInverted() ? cblock.get().invertY() : cblock.get());
                cbiome.set(engine.getTarget().isInverted() ? cbiome.get().invertY() : cbiome.get());
                e.queue(() -> {
                    engine.generate(x, z, cblock.get(), cbiome.get());
                    synchronized (insert)
                    {
                        insert[index.get()] = () -> {
                            blocks.insert(0, doffset, 0, cblock.get());
                            biomes.insert(0, doffset, 0, cbiome.get());
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

        wallClock.put(p.getMilliseconds());
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

    @Override
    public IrisDataManager getData(int height) {
        return getEngineForHeight(height).getData();
    }

    //TODO: FAIL
    @Override
    public boolean isFailing() {
        return false;
    }

    @Override
    public void hotload() {
        for(int i = 0; i < getSize(); i++)
        {
            getEngine(i).hotload();
        }
    }
}
