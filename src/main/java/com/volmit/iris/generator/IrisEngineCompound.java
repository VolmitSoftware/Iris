package com.volmit.iris.generator;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.nms.INMS;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisDimensionIndex;
import com.volmit.iris.object.IrisPosition;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineCompound;
import com.volmit.iris.scaffold.engine.EngineData;
import com.volmit.iris.scaffold.engine.EngineTarget;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.*;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.generator.BlockPopulator;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class IrisEngineCompound implements EngineCompound {
    @Getter
    private final World world;

    private final AtomicRollingSequence wallClock;

    private Engine defaultEngine;

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
    private final int threadCount = -1;

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



        if(engineMetadata.getStrongholdPositions() == null || engineMetadata.getStrongholdPositions().size() == 0)
        {
            if(!(world instanceof FakeWorld || world instanceof HeightedFakeWorld))
            {
                List<IrisPosition> strongholds = new ArrayList<>();
                Object nmsWorld = new V(world).invoke("getHandle");
                Object chunkProvider = new V(nmsWorld).invoke("getChunkProvider");
                Object chunkGenerator = new V(chunkProvider).invoke("getChunkGenerator");
                try {
                    Class<?> clazz = Class.forName("net.minecraft.server." + INMS.getNMSTag() + ".ChunkGenerator");
                    Class<?> clazzSG = Class.forName("net.minecraft.server." + INMS.getNMSTag() + ".StructureGenerator");
                    Class<?> clazzBP = Class.forName("net.minecraft.server." + INMS.getNMSTag() + ".BlockPosition");
                    getBPSafe(clazz, clazzSG, clazzBP, nmsWorld, chunkGenerator).thenAccept(bp -> {
                        if (bp == null){
                            throw new NullPointerException();
                        }
                        strongholds.add(new IrisPosition((int) new V(bp, false).invoke("getX"), (int) new V(bp, false).invoke("getY"), (int) new V(bp, false).invoke("getZ")));
                        StringBuilder positions = new StringBuilder();
                        for (IrisPosition pos : strongholds){
                            positions.append("(").append(pos.getX()).append(",").append(pos.getY()).append(",").append(pos.getZ()).append(") ");
                        }
                        Iris.info("Strongholds (" + engineMetadata.getStrongholdPositions().size() + ") found at [" + positions + "]");
                    });

                    engineMetadata.setStrongholdPositions(strongholds);
                } catch (Throwable e) {
                    strongholds.add( new IrisPosition(1337, 32, -1337) );
                    engineMetadata.setStrongholdPositions(strongholds);
                    Iris.warn("Couldn't properly find the stronghold position for this world. Is this headless mode? Are you not using 1.16 or higher?");
                    Iris.warn("  -> Setting default stronghold position");
                    e.printStackTrace();
                    StringBuilder positions = new StringBuilder();
                    for (IrisPosition pos : strongholds){
                        positions.append("(").append(pos.getX()).append(",").append(pos.getY()).append(",").append(pos.getZ()).append(") ");
                    }
                    Iris.info("Strongholds (" + engineMetadata.getStrongholdPositions().size() + ") found at [" + positions + "]");
                }
            }
        }

        saveEngineMetadata();
        populators = new KList<>();

        if(rootDimension.getDimensionalComposite().isEmpty())
        {
            burster = null;
            // TODO: WARNING HEIGHT
            engines = new Engine[]{new IrisEngine(new EngineTarget(world, rootDimension, data, 256, maximumThreads), this, 0)};
            defaultEngine = engines[0];
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
                // TODO: WARNING HEIGHT
                engines[i] = new IrisEngine(new EngineTarget(world, dimension, data.copy(), (int)Math.floor(256D * (index.getWeight() / totalWeight)), index.isInverted(), threadDist), this, i);
                engines[i].setMinHeight(buf);
                buf += engines[i].getHeight();

                if(index.isPrimary())
                {
                    defaultEngine = engines[i];
                }
            }

            if(defaultEngine == null)
            {
                defaultEngine = engines[0];
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

    private Object getBP(Class clazz, Class clazzSG, Class clazzBP, Object nmsWorld, Object chunkGenerator) throws NoSuchFieldException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        return clazz.getDeclaredMethod("findNearestMapFeature",
            nmsWorld.getClass(),
            clazzSG,
            clazzBP,
            int.class,
            boolean.class
        ).invoke(chunkGenerator,
            nmsWorld,
            clazzSG.getDeclaredField("STRONGHOLD").get(null),
            clazzBP.getDeclaredField("ZERO").get(null),
            100,
            false
        );
    }

    public CompletableFuture<Object> getBPSafe(Class clazz, Class clazzSG, Class clazzBP, Object nmsWorld, Object chunkGenerator) {
        CompletableFuture<Object> cf = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(Iris.instance, () -> {
            try {
                cf.complete(getBP(clazz, clazzSG, clazzBP, nmsWorld, chunkGenerator));
            } catch (Throwable e){
                cf.complete(null);
            }
        });
        return cf;
    }

    public List<IrisPosition> getStrongholdPositions()
    {
        return engineMetadata.getStrongholdPositions();
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
    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<BlockData> postblocks, Hunk<Biome> biomes)
    {
        recycle();
        PrecisionStopwatch p = PrecisionStopwatch.start();
        if(engines.length == 1 && !getEngine(0).getTarget().isInverted())
        {
            engines[0].generate(x, z, blocks, biomes);
        }

        else
        {
            int i;
            int offset = 0;

            for(i = 0; i < engines.length; i++)
            {
                Engine engine = engines[i];
                int doffset = offset;
                int height = engine.getTarget().getHeight();
                Hunk<BlockData> cblock = Hunk.newArrayHunk(16, height, 16);
                Hunk<Biome> cbiome = Hunk.newArrayHunk(16, height, 16);

                if(engine.getTarget().isInverted())
                {
                    cblock = cblock.invertY();
                    cbiome = cbiome.invertY();
                }

                engine.generate(x, z, cblock, cbiome);
                blocks.insert(0, doffset, 0, cblock);
                biomes.insert(0, doffset, 0, cbiome);
                offset += height;
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
    public Engine getDefaultEngine() {
        return defaultEngine;
    }

    @Override
    public void hotload() {
        for(int i = 0; i < getSize(); i++)
        {
            getEngine(i).hotload();
        }
    }
}
