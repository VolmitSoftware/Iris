package com.volmit.iris.scaffold.engine;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.*;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.util.*;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public interface IrisAccess extends Hotloadable, DataProvider {

    public int getGenerated();

    public double getGeneratedPerSecond();

    public void printMetrics(CommandSender sender);

    public IrisBiome getBiome(int x, int y, int z);

    public IrisBiome getCaveBiome(int x, int y, int z);

    public IrisBiome getBiome(int x, int z);

    public IrisBiome getCaveBiome(int x, int z);

    public GeneratorAccess getEngineAccess(int y);

    public IrisDataManager getData();

    public int getHeight(int x, int y, int z);

    public int getThreadCount();

    public void changeThreadCount(int m);

    public void regenerate(int x, int z);

    public void close();

    public boolean isClosed();

    public EngineTarget getTarget();

    public EngineCompound getCompound();

    public boolean isFailing();

    public boolean isStudio();

    public default Location lookForObject(IrisObject object, long timeout, Consumer<Integer> triesc)
    {
        ChronoLatch cl = new ChronoLatch(250, false);
        long s = M.ms();
        int cpus = 2+(Runtime.getRuntime().availableProcessors()/2);
        KList<Engine> engines = new KList<>();
        String key = object.getLoadKey();

        looking: for(int i = 0; i < getCompound().getSize(); i++)
        {
            Engine e = getCompound().getEngine(i);

            for(IrisBiome j : e.getDimension().getAllBiomes(e))
            {
                for(IrisObjectPlacement k : j.getObjects())
                {
                    if(k.getPlace().contains(key))
                    {
                        engines.add(e);
                        continue looking;
                    }
                }

                for(IrisStructurePlacement k : j.getStructures())
                {
                    for(IrisStructureTile l : k.getStructure(this).getTiles())
                    {
                        for(IrisRareObject m : l.getRareObjects())
                        {
                            if(m.getObject().equals(key))
                            {
                                engines.add(e);
                                continue looking;
                            }
                        }

                        if(l.getObjects().contains(key))
                        {
                            engines.add(e);
                            continue looking;
                        }
                    }
                }
            }

            for(IrisRegion j : e.getDimension().getAllRegions(e))
            {
                for(IrisObjectPlacement k : j.getObjects())
                {
                    if(k.getPlace().contains(key))
                    {
                        engines.add(e);
                        continue looking;
                    }
                }

                for(IrisStructurePlacement k : j.getStructures())
                {
                    for(IrisStructureTile l : k.getStructure(this).getTiles())
                    {
                        for(IrisRareObject m : l.getRareObjects())
                        {
                            if(m.getObject().equals(key))
                            {
                                engines.add(e);
                                continue looking;
                            }
                        }

                        if(l.getObjects().contains(key))
                        {
                            engines.add(e);
                            continue looking;
                        }
                    }
                }
            }

            for(IrisBiomeMutation k : e.getDimension().getMutations())
            {
                for(IrisObjectPlacement l : k.getObjects())
                {
                    if(l.getPlace().contains(key))
                    {
                        engines.add(e);
                        continue looking;
                    }
                }
            }
        }

        if(engines.isEmpty())
        {
            return null;
        }

        AtomicInteger tries = new AtomicInteger(0);
        AtomicBoolean found = new AtomicBoolean(false);
        AtomicReference<Location> location = new AtomicReference<>();

        for(int i = 0; i < cpus; i++)
        {
            J.a(() -> {
                try
                {
                    Engine e;
                    IrisBiome b;
                    int x,y,z;
                    String sf;

                    while(!found.get())
                    {
                        try {
                            synchronized (engines) {
                                e = engines.getRandom();
                            }

                            tries.getAndIncrement();
                            x = RNG.r.i(-29999970, 29999970);
                            y = RNG.r.i(0, e.getHeight()-1);
                            z = RNG.r.i(-29999970, 29999970);
                            KList<PlacedObject> p = e.getFramework().getEngineParallax().generateParallaxLayerObjects(Math.floorDiv(x, 16), Math.floorDiv(z, 16));

                            if(p == null || p.isEmpty())
                            {
                                continue;
                            }

                            for(PlacedObject j : p)
                            {
                                if(j.getObject().getLoadKey().equals(object.getLoadKey()))
                                {
                                    found.lazySet(true);
                                    location.lazySet(new Location(e.getWorld(), j.getXx(), e.getMinHeight() + e.getHeight(j.getXx(), j.getZz()), j.getZz(), 0f, -90f));
                                }
                            }
                        }

                        catch(Throwable ex)
                        {
                            ex.printStackTrace();
                            return;
                        }
                    }
                }

                catch(Throwable e)
                {
                    e.printStackTrace();
                }
            });
        }

        while(!found.get() || location.get() == null)
        {
            J.sleep(50);

            if(cl.flip())
            {
                triesc.accept(tries.get());
            }

            if(M.ms() - s > timeout)
            {
                return null;
            }
        }

        return location.get();
    }

    public default Location lookForBiome(IrisBiome biome, long timeout, Consumer<Integer> triesc)
    {
        ChronoLatch cl = new ChronoLatch(250, false);
        long s = M.ms();
        int cpus = 2+(Runtime.getRuntime().availableProcessors()/2);
        KList<Engine> engines = new KList<>();
        for(int i = 0; i < getCompound().getSize(); i++)
        {
            Engine e = getCompound().getEngine(i);
            if(e.getDimension().getAllBiomes(e).contains(biome))
            {
                engines.add(e);
            }
        }

        AtomicInteger tries = new AtomicInteger(0);
        AtomicBoolean found = new AtomicBoolean(false);
        AtomicReference<Location> location = new AtomicReference<>();

        for(int i = 0; i < cpus; i++)
        {
            J.a(() -> {
               try
               {
                   Engine e;
                   IrisBiome b;
                   int x,y,z;

                   while(!found.get())
                   {
                       try {
                           synchronized (engines) {
                               e = engines.getRandom();
                               x = RNG.r.i(-29999970, 29999970);
                               y = RNG.r.i(0, e.getHeight()-1);
                               z = RNG.r.i(-29999970, 29999970);

                               b = e.getBiome(x, y, z);
                           }

                           if(b != null && b.getLoadKey() == null)
                           {
                               continue;
                           }

                           if(b != null && b.getLoadKey().equals(biome.getLoadKey()))
                           {
                               found.lazySet(true);
                               location.lazySet(new Location(e.getWorld(), x,y,z));
                           }

                           tries.getAndIncrement();
                       }

                       catch(Throwable ex)
                       {
                           ex.printStackTrace();
                           return;
                       }
                   }
               }

               catch(Throwable e)
               {
                   e.printStackTrace();
               }
            });
        }

        while(!found.get() || location.get() == null)
        {
            J.sleep(50);

            if(cl.flip())
            {
                triesc.accept(tries.get());
            }

            if(M.ms() - s > timeout)
            {
                return null;
            }
        }

        return location.get();
    }

    public default Location lookForRegion(IrisRegion reg, long timeout, Consumer<Integer> triesc)
    {
        ChronoLatch cl = new ChronoLatch(3000, false);
        long s = M.ms();
        int cpus = 2+(Runtime.getRuntime().availableProcessors()/2);
        KList<Engine> engines = new KList<>();
        for(int i = 0; i < getCompound().getSize(); i++)
        {
            Engine e = getCompound().getEngine(i);
            if(e.getDimension().getRegions().contains(reg.getLoadKey()))
            {
                engines.add(e);
            }
        }

        if(engines.isEmpty())
        {
            return null;
        }

        AtomicInteger tries = new AtomicInteger(0);
        AtomicBoolean found = new AtomicBoolean(false);
        AtomicReference<Location> location = new AtomicReference<>();

        for(int i = 0; i < cpus; i++)
        {
            J.a(() -> {
                Engine e;
                IrisRegion b;
                int x,z;

                while(!found.get())
                {
                    try {
                        e = engines.getRandom();
                        x = RNG.r.i(-29999970, 29999970);
                        z = RNG.r.i(-29999970, 29999970);
                        b = e.getRegion(x, z);

                        if(b != null && b.getLoadKey() != null && b.getLoadKey().equals(reg.getLoadKey()))
                        {
                            found.lazySet(true);
                            location.lazySet(new Location(e.getWorld(), x, e.getHeight(x, z) + e.getMinHeight() ,z));
                        }

                        tries.getAndIncrement();
                    }

                    catch(Throwable xe)
                    {
                        xe.printStackTrace();
                        return;
                    }
                }
            });
        }

        while(!found.get() || location.get() != null)
        {
            J.sleep(50);

            if(cl.flip())
            {
                triesc.accept(tries.get());
            }

            if(M.ms() - s > timeout)
            {
                triesc.accept(tries.get());
                return null;
            }
        }

        triesc.accept(tries.get());
        return location.get();
    }

    public void clearRegeneratedLists(int x, int z);

    void precache(World world, int x, int z);

    int getPrecacheSize();

    Chunk generatePaper(World world, int cx, int cz);
}
