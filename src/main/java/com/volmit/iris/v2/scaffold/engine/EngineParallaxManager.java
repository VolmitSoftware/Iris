package com.volmit.iris.v2.scaffold.engine;

import java.util.concurrent.atomic.AtomicInteger;

import com.volmit.iris.object.*;
import com.volmit.iris.util.*;
import com.volmit.iris.v2.generator.actuator.IrisTerrainActuator;
import com.volmit.iris.v2.generator.modifier.IrisCaveModifier;
import com.volmit.iris.v2.scaffold.parallax.ParallaxChunkMeta;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.v2.generator.IrisComplex;
import com.volmit.iris.v2.scaffold.cache.Cache;
import com.volmit.iris.v2.scaffold.data.DataProvider;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.v2.scaffold.parallax.ParallaxAccess;
import com.volmit.iris.v2.scaffold.parallel.BurstExecutor;
import com.volmit.iris.v2.scaffold.parallel.MultiBurst;

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
        ParallaxChunkMeta meta = getParallaxAccess().getMetaR(x>>4, z>>4);

        if(!meta.isObjects()) {
            return;
        }

        for(int i = x; i < x+ data.getWidth(); i++)
        {
            for(int j= z; j < z + data.getDepth(); j++)
            {
                for(int k = 0; k < data.getHeight(); k++)
                {
                    BlockData d = getParallaxAccess().getBlock(i, k, j);

                    if(d != null)
                    {
                        data.set(i - x, k, j - z, d);
                    }
                }
            }
        }
    }

    default void generateParallaxArea(int x, int z)
    {
        int s = (int) Math.ceil(getParallaxSize() / 2D);
        int j;
        BurstExecutor e = MultiBurst.burst.burst(getParallaxSize() * getParallaxSize());

        for(int i = -s; i <= s; i++)
        {
            int ii = i;

            for(j = -s; j <= s; j++)
            {
                int jj = j;
                e.queue(() -> generateParallaxLayer((ii*16)+x, (jj*16)+z));
            }
        }

        e.complete();

        getParallaxAccess().setChunkGenerated(x>>4, z>>4);
    }

    default void generateParallaxLayer(int x, int z)
    {
        if(getParallaxAccess().isParallaxGenerated(x >> 4, z >> 4))
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

    default void generateParallaxSurface(RNG rng, int x, int z, IrisBiome biome) {
        for (IrisObjectPlacement i : biome.getSurfaceObjects())
        {
            if(rng.chance(i.getChance()))
            {
                place(rng, x, z, i);
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

    default void place(RNG rng, int x, int z, IrisObjectPlacement objectPlacement)
    {
        place(rng, x,-1, z, objectPlacement);
    }

    default void place(RNG rng, int x, int forceY, int z, IrisObjectPlacement objectPlacement)
    {
        for(int i = 0; i < objectPlacement.getDensity(); i++)
        {
            IrisObject v = objectPlacement.getSchematic(getComplex(), rng);
            int xx = rng.i(x, x+16);
            int zz = rng.i(z, z+16);
            int id = rng.i(0, Integer.MAX_VALUE);
            v.place(xx, forceY, zz, this, objectPlacement, rng, (b) -> {
                getParallaxAccess().setObject(b.getX(), b.getY(), b.getZ(), v.getLoadKey() + "@" + id);
                ParallaxChunkMeta meta = getParallaxAccess().getMetaRW(b.getX() >> 4, b.getZ() >> 4);
                meta.setObjects(true);
                meta.setMaxObject(Math.max(b.getY(), meta.getMaxObject()));
                meta.setMinObject(Math.min(b.getY(), Math.max(meta.getMinObject(), 0)));
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

        for(IrisTextPlacement i : getEngine().getDimension().getText())
        {
            int max = i.maxDimension();
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

            for(IrisTextPlacement i : v.getText())
            {
                int max = i.maxDimension();
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

            for(IrisTextPlacement i : v.getText())
            {
                int max = i.maxDimension();
                x = Math.max(max, x);
                z = Math.max(max, z);
            }
        }

        x = (Math.max(x, 16) + 16) >> 4;
        z = (Math.max(z, 16) + 16) >> 4;
        x = x % 2 == 0 ? x + 1 : x;
        z = z % 2 == 0 ? z + 1 : z;
        x = Math.max(x, z);
        z = x;
        Iris.verbose(getEngine().getDimension().getName() + " Parallax Size: " + x + ", " + z);
        return x;
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
        int rx = (int) Math.round(getEngine().modifyX(x));
        int rz = (int) Math.round(getEngine().modifyZ(z));
        int height = (int) Math.round(getComplex().getHeightStream().get(rx, rz));
        int m = height;

        if(getEngine().getDimension().isCarving())
        {
            if(getEngine().getDimension().isCarved(rx, m, rz, ((IrisTerrainActuator)getFramework().getTerrainActuator()).getRng(), height))
            {
                m--;

                while(getEngine().getDimension().isCarved(rx, m, rz, ((IrisTerrainActuator)getFramework().getTerrainActuator()).getRng(), height))
                {
                    m--;
                }
            }
        }

        if(getEngine().getDimension().isCaves())
        {
            KList<CaveResult> caves = ((IrisCaveModifier)getFramework().getCaveModifier()).genCaves(rx, rz, 0, 0, null);
            boolean again = true;

            while(again)
            {
                again = false;
                for(CaveResult i : caves)
                {
                    if(i.getCeiling() > m && i.getFloor() < m)
                    {
                        m = i.getFloor();
                        again = true;
                    }
                }
            }
        }

        return m;
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
