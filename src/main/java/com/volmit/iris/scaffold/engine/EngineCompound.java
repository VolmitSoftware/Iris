package com.volmit.iris.scaffold.engine;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisPosition;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.generator.BlockPopulator;

import java.util.List;

public interface EngineCompound extends Listener, Hotloadable, DataProvider
{
    public IrisDimension getRootDimension();

    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<BlockData> postblocks, Hunk<Biome> biomes);

    public World getWorld();

    public List<IrisPosition> getStrongholdPositions();

    public void printMetrics(CommandSender sender);

    public int getSize();

    public default int getHeight()
    {
        // TODO: WARNING HEIGHT
        return 256;
    }

    public Engine getEngine(int index);

    public MultiBurst getBurster();

    public EngineData getEngineMetadata();

    public void saveEngineMetadata();

    public KList<BlockPopulator> getPopulators();

    default Engine getEngineForHeight(int height)
    {
        if(getSize() == 1)
        {
            return getEngine(0);
        }

        int buf = 0;

        for(int i = 0; i < getSize(); i++)
        {
            Engine e = getEngine(i);
            buf += e.getHeight();

            if(buf >= height)
            {
                return e;
            }
        }

        return getEngine(getSize() - 1);
    }

    public default void recycle()
    {
        for(int i = 0; i < getSize(); i++)
        {
            getEngine(i).recycle();
        }
    }

    public default void save()
    {
        saveEngineMetadata();
        for(int i = 0; i < getSize(); i++)
        {
            getEngine(i).save();
        }
    }

    public default void saveNOW()
    {
        saveEngineMetadata();
        for(int i = 0; i < getSize(); i++)
        {
            getEngine(i).saveNow();
        }
    }

    public IrisDataManager getData(int height);

    public default IrisDataManager getData()
    {
        return getData(0);
    }

    public default void close()
    {
        for(int i = 0; i < getSize(); i++)
        {
            getEngine(i).close();
        }
    }

    public boolean isFailing();

    public int getThreadCount();

    public boolean isStudio();

    public void setStudio(boolean std);

    public default void clean()
    {
        for(int i = 0; i < getSize(); i++)
        {
            getEngine(i).clean();
        }
    }

    public Engine getDefaultEngine();

    public default KList<IrisBiome> getAllBiomes()
    {
        KMap<String, IrisBiome> v = new KMap<>();

        IrisDimension dim = getRootDimension();
        dim.getAllBiomes(this).forEach((i) -> v.put(i.getLoadKey(), i));

       try
       {
           dim.getDimensionalComposite().forEach((m) -> getData().getDimensionLoader().load(m.getDimension()).getAllBiomes(this).forEach((i) -> v.put(i.getLoadKey(), i)));
       }

       catch(Throwable e)
       {

       }

        return v.v();
    }
}
