package com.volmit.iris.scaffold.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.generator.IrisComplex;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.*;
import com.volmit.iris.scaffold.cache.Cache;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.parallax.ParallaxAccess;
import com.volmit.iris.scaffold.parallax.ParallaxChunkMeta;
import com.volmit.iris.scaffold.parallel.BurstExecutor;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public interface EngineParallaxManager extends DataProvider, IObjectPlacer
{
    public static final BlockData AIR = B.get("AIR");

    public Engine getEngine();

    public int getParallaxSize();

    public EngineStructureManager getStructureManager();

    default EngineFramework getFramework()
    {
        return getEngine().getFramework();
    }

    default ParallaxAccess getParallaxAccess()
    {
        return getEngine().getParallax();
    }

    default IrisDataManager getData()
    {
        return getEngine().getData();
    }

    default IrisComplex getComplex()
    {
        return getEngine().getFramework().getComplex();
    }

    default KList<IrisRegion> getAllRegions()
    {
        KList<IrisRegion> r = new KList<>();

        for(String i : getEngine().getDimension().getRegions())
        {
            r.add(getEngine().getData().getRegionLoader().load(i));
        }

        return r;
    }

    default KList<IrisBiome> getAllBiomes()
    {
        KList<IrisBiome> r = new KList<>();

        for(IrisRegion i : getAllRegions())
        {
            r.addAll(i.getAllBiomes(this));
        }

        return r;
    }

    default void insertParallax(int x, int z, Hunk<BlockData> data)
    {
        try
        {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            ParallaxChunkMeta meta = getParallaxAccess().getMetaR(x>>4, z>>4);

            if(!meta.isParallaxGenerated())
            {
                Iris.warn("Chunk " + (x >> 4) + " " + (z >> 4) + " has no parallax data!");
                generateParallaxLayer(x, z, true);
                meta = getParallaxAccess().getMetaR(x>>4, z>>4);

                if(meta.isParallaxGenerated())
                {
                    Iris.info("Fixed!");
                }

                else
                {
                    Iris.error("Not Fixed!");
                }
            }

            if(!meta.isObjects()) {
                getEngine().getMetrics().getParallaxInsert().put(p.getMilliseconds());
                return;
            }

            for(int i = x; i < x+ data.getWidth(); i++)
            {
                for(int j= z; j < z + data.getDepth(); j++)
                {
                    for(int k = 0; k < getEngine().getHeight(); k++)
                    {
                        BlockData d = getParallaxAccess().getBlock(i, k, j);

                        if(d != null)
                        {
                            data.set(i - x, k, j - z, d);
                        }
                    }
                }
            }

            getEngine().getMetrics().getParallaxInsert().put(p.getMilliseconds());
        }

        catch(Throwable e)
        {
            Iris.error("Failed to insert parallax at chunk " + (x>>4) + " " + (z>>4));
            e.printStackTrace();
        }
    }

    default void generateParallaxArea(int x, int z)
    {
        try
        {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            int s = (int) Math.ceil(getParallaxSize() / 2D);
            int i,j;
            BurstExecutor b = MultiBurst.burst.burst((s * 2) * (s * 2));

            for(i = -s; i <= s; i++)
            {
                int ii = i;
                for(j = -s; j <= s; j++)
                {
                    int jj = j;
                    b.queue(() -> generateParallaxLayer((ii*16)+x, (jj*16)+z));
                }
            }

            b.complete();
            getParallaxAccess().setChunkGenerated(x>>4, z>>4);
            p.end();
            getEngine().getMetrics().getParallax().put(p.getMilliseconds());
        }

        catch(Throwable e)
        {
            Iris.error("Failed to generate parallax in " + x + " " + z);
            e.printStackTrace();
        }
    }

    default void generateParallaxLayer(int x, int z, boolean force)
    {
        if(!force && getParallaxAccess().isParallaxGenerated(x >> 4, z >> 4))
        {
            return;
        }

        getParallaxAccess().setParallaxGenerated(x>>4, z>>4);
        RNG rng = new RNG(Cache.key(x, z)).nextParallelRNG(getEngine().getTarget().getWorld().getSeed());
        IrisRegion region = getComplex().getRegionStream().get(x+8, z+8);
        IrisBiome biome = getComplex().getTrueBiomeStream().get(x+8, z+8);
        generateParallaxSurface(rng, x, z, biome);
        generateParallaxMutations(rng, x, z);
        generateStructures(rng, x>>4, z>>4, region, biome);
    }
    default void generateParallaxLayer(int x, int z)
    {
       generateParallaxLayer(x, z, false);
    }

    default KList<PlacedObject> generateParallaxLayerObjects(int x, int z)
    {
        KList<PlacedObject> placedObjects = new KList<>();
        RNG rng = new RNG(Cache.key(x, z)).nextParallelRNG(getEngine().getTarget().getWorld().getSeed());
        IrisRegion region = getComplex().getRegionStream().get(x+8, z+8);
        IrisBiome biome = getComplex().getTrueBiomeStream().get(x+8, z+8);
        // generateParallaxJigsaw(rng, x, z, biome, placedObjects);
        generateParallaxSurface(rng, x, z, biome, placedObjects);
        generateParallaxMutations(rng, x, z, placedObjects);
        generateStructures(rng, x>>4, z>>4, region, biome, placedObjects);

        return placedObjects;
    }

    default void generateStructures(RNG rng, int x, int z, IrisRegion region, IrisBiome biome)
    {
        int g = 30265;
        for(IrisStructurePlacement k : region.getStructures())
        {
            if(k == null)
            {
                continue;
            }

            getStructureManager().placeStructure(k, rng.nextParallelRNG(2228 * 2 * g++), x, z);
        }

        for(IrisStructurePlacement k : biome.getStructures())
        {
            if(k == null)
            {
                continue;
            }

            getStructureManager().placeStructure(k, rng.nextParallelRNG(-22228 * 4 * g++), x, z);
        }
    }

    default void generateStructures(RNG rng, int x, int z, IrisRegion region, IrisBiome biome, KList<PlacedObject> objects)
    {
        int g = 30265;
        for(IrisStructurePlacement k : region.getStructures())
        {
            if(k == null)
            {
                continue;
            }

            getStructureManager().placeStructure(k, rng.nextParallelRNG(2228 * 2 * g++), x, z, objects);
        }

        for(IrisStructurePlacement k : biome.getStructures())
        {
            if(k == null)
            {
                continue;
            }

            getStructureManager().placeStructure(k, rng.nextParallelRNG(-22228 * 4 * g++), x, z, objects);
        }
    }

    default void generateParallaxSurface(RNG rng, int x, int z, IrisBiome biome) {
        for (IrisObjectPlacement i : biome.getSurfaceObjects())
        {
            if(rng.chance(i.getChance()))
            {
                place(rng, x, z, i);
            }
        }
    }

    default void generateParallaxSurface(RNG rng, int x, int z, IrisBiome biome, KList<PlacedObject> objects) {
        for (IrisObjectPlacement i : biome.getSurfaceObjects())
        {
            if(rng.chance(i.getChance()))
            {
                place(rng, x, z, i, objects);
            }
        }
    }

    default void generateParallaxMutations(RNG rng, int x, int z) {
        if(getEngine().getDimension().getMutations().isEmpty())
        {
            return;
        }

        searching: for(IrisBiomeMutation k : getEngine().getDimension().getMutations())
        {
            for(int l = 0; l < k.getChecks(); l++)
            {
                IrisBiome sa = getComplex().getTrueBiomeStream().get(((x * 16) + rng.nextInt(16)) + rng.i(-k.getRadius(), k.getRadius()), ((z * 16) + rng.nextInt(16)) + rng.i(-k.getRadius(), k.getRadius()));
                IrisBiome sb = getComplex().getTrueBiomeStream().get(((x * 16) + rng.nextInt(16)) + rng.i(-k.getRadius(), k.getRadius()), ((z * 16) + rng.nextInt(16)) + rng.i(-k.getRadius(), k.getRadius()));

                if(sa.getLoadKey().equals(sb.getLoadKey()))
                {
                    continue;
                }

                if(k.getRealSideA(this).contains(sa.getLoadKey()) && k.getRealSideB(this).contains(sb.getLoadKey()))
                {
                    for(IrisObjectPlacement m : k.getObjects())
                    {
                        place(rng.nextParallelRNG((34 * ((x * 30) + (z * 30)) * x * z) + x - z + 1569962), x, z, m);
                    }

                    continue searching;
                }
            }
        }
    }

    default void generateParallaxMutations(RNG rng, int x, int z, KList<PlacedObject> o) {
        if(getEngine().getDimension().getMutations().isEmpty())
        {
            return;
        }

        searching: for(IrisBiomeMutation k : getEngine().getDimension().getMutations())
        {
            for(int l = 0; l < k.getChecks(); l++)
            {
                IrisBiome sa = getComplex().getTrueBiomeStream().get(((x * 16) + rng.nextInt(16)) + rng.i(-k.getRadius(), k.getRadius()), ((z * 16) + rng.nextInt(16)) + rng.i(-k.getRadius(), k.getRadius()));
                IrisBiome sb = getComplex().getTrueBiomeStream().get(((x * 16) + rng.nextInt(16)) + rng.i(-k.getRadius(), k.getRadius()), ((z * 16) + rng.nextInt(16)) + rng.i(-k.getRadius(), k.getRadius()));

                if(sa.getLoadKey().equals(sb.getLoadKey()))
                {
                    continue;
                }

                if(k.getRealSideA(this).contains(sa.getLoadKey()) && k.getRealSideB(this).contains(sb.getLoadKey()))
                {
                    for(IrisObjectPlacement m : k.getObjects())
                    {
                        place(rng.nextParallelRNG((34 * ((x * 30) + (z * 30)) * x * z) + x - z + 1569962), x, z, m, o);
                    }

                    continue searching;
                }
            }
        }
    }

    default void place(RNG rng, int x, int z, IrisObjectPlacement objectPlacement)
    {
        place(rng, x,-1, z, objectPlacement);
    }

    default void place(RNG rng, int x, int z, IrisObjectPlacement objectPlacement, KList<PlacedObject> objects)
    {
        place(rng, x,-1, z, objectPlacement, objects);
    }

    default void place(RNG rng, int x, int forceY, int z, IrisObjectPlacement objectPlacement, KList<PlacedObject> objects)
    {
        for(int i = 0; i < objectPlacement.getDensity(); i++)
        {
            IrisObject v = objectPlacement.getSchematic(getComplex(), rng);
            int xx = rng.i(x, x+16);
            int zz = rng.i(z, z+16);
            int id = rng.i(0, Integer.MAX_VALUE);
            objects.add(new PlacedObject(objectPlacement, v, id, xx, zz));
        }
    }

    default void place(RNG rng, int x, int forceY, int z, IrisObjectPlacement objectPlacement)
    {
        for(int i = 0; i < objectPlacement.getDensity(); i++)
        {
            IrisObject v = objectPlacement.getSchematic(getComplex(), rng);
            int xx = rng.i(x, x+16);
            int zz = rng.i(z, z+16);
            int id = rng.i(0, Integer.MAX_VALUE);
            int maxf = 10000;
            AtomicBoolean pl = new AtomicBoolean(false);
            AtomicInteger max = new AtomicInteger(-1);
            AtomicInteger min = new AtomicInteger(maxf);
            v.place(xx, forceY, zz, this, objectPlacement, rng, (b) -> {
                int xf = b.getX();
                int yf = b.getY();
                int zf = b.getZ();
                getParallaxAccess().setObject(xf, yf, zf, v.getLoadKey() + "@" + id);
                ParallaxChunkMeta meta = getParallaxAccess().getMetaRW(xf>>4, zf>>4);
                meta.setObjects(true);
                meta.setMinObject(Math.min(Math.max(meta.getMinObject(), 0), yf));
                meta.setMaxObject(Math.max(Math.max(meta.getMaxObject(), 0), yf));

            }, null, getData());
        }
    }

    default void updateParallaxChunkObjectData(int minY, int maxY, int x, int z, IrisObject v)
    {
        ParallaxChunkMeta meta = getParallaxAccess().getMetaRW(x >> 4, z >> 4);
        meta.setObjects(true);
        meta.setMaxObject(Math.max(maxY, meta.getMaxObject()));
        meta.setMinObject(Math.min(minY, Math.max(meta.getMinObject(), 0)));
    }

    default int computeParallaxSize()
    {
        Iris.verbose("Calculating the Parallax Size in Parallel");
        AtomicInteger xg = new AtomicInteger(0);
        AtomicInteger zg = new AtomicInteger();
        xg.set(0);
        zg.set(0);

        KSet<String> objects = new KSet<>();
        KList<IrisRegion> r = getAllRegions();
        KList<IrisBiome> b = getAllBiomes();

        for(IrisBiome i : b)
        {
            for(IrisObjectPlacement j : i.getObjects())
            {
                objects.addAll(j.getPlace());
            }
        }

        Iris.verbose("Checking sizes for " + Form.f(objects.size()) + " referenced objects.");
        BurstExecutor e = MultiBurst.burst.burst(objects.size());
        for(String i : objects)
        {
            e.queue(() -> {
                try
                {
                    BlockVector bv = IrisObject.sampleSize(getData().getObjectLoader().findFile(i));
                    warn(i, bv);

                    synchronized (xg)
                    {
                        xg.getAndSet(Math.max(bv.getBlockX(), xg.get()));
                    }

                    synchronized (zg)
                    {
                        zg.getAndSet(Math.max(bv.getBlockZ(), zg.get()));
                    }
                }

                catch(Throwable ignored)
                {

                }
            });
        }

        e.complete();

        int x = xg.get();
        int z = zg.get();

        for(IrisDepositGenerator i : getEngine().getDimension().getDeposits())
        {
            int max = i.getMaxDimension();
            x = Math.max(max, x);
            z = Math.max(max, z);
        }

        for(IrisRegion v : r)
        {
            for(IrisDepositGenerator i : v.getDeposits())
            {
                int max = i.getMaxDimension();
                x = Math.max(max, x);
                z = Math.max(max, z);
            }
        }

        for(IrisBiome v : b)
        {
            for(IrisDepositGenerator i : v.getDeposits())
            {
                int max = i.getMaxDimension();
                x = Math.max(max, x);
                z = Math.max(max, z);
            }
        }

        x = Math.max(z, x);
        x = (Math.max(x, 16) + 16) >> 4;
        x = x % 2 == 0 ? x + 1 : x;
        return x;
    }

    default void warn(String ob, BlockVector bv)
    {
        if(Math.max(bv.getBlockX(), bv.getBlockZ()) > 128)
        {
            Iris.warn("Object " + ob + " has a large size (" + bv.toString() + ") and may increase memory usage!");
        }
    }

    @Override
    default int getHighest(int x, int z) {
        return getHighest(x,z,false);
    }

    @Override
    default int getHighest(int x, int z, boolean ignoreFluid) {
        return ignoreFluid ? trueHeight(x, z) : Math.max(trueHeight(x, z), getEngine().getDimension().getFluidHeight());
    }

    default int trueHeight(int x, int z)
    {
        return getComplex().getTrueHeightStream().get(x, z);
    }

    @Override
    default void set(int x, int y, int z, BlockData d) {
        getParallaxAccess().setBlock(x,y,z,d);
    }

    @Override
    default BlockData get(int x, int y, int z) {
        BlockData block = getParallaxAccess().getBlock(x,y,z);

        if(block == null)
        {
            return AIR;
        }

        return block;
    }

    @Override
    default boolean isPreventingDecay() {
        return getEngine().getDimension().isPreventLeafDecay();
    }

    @Override
    default boolean isSolid(int x, int y, int z) {
        return B.isSolid(get(x,y,z));
    }

    @Override
    default boolean isUnderwater(int x, int z) {
        return getHighest(x, z, true) <= getFluidHeight();
    }

    @Override
    default int getFluidHeight() {
        return getEngine().getDimension().getFluidHeight();
    }

    @Override
    default boolean isDebugSmartBore() {
        return getEngine().getDimension().isDebugSmartBore();
    }

    default void close()
    {

    }
}
