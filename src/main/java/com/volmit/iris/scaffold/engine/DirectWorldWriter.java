package com.volmit.iris.scaffold.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.nms.INMS;
import com.volmit.iris.scaffold.cache.Cache;
import com.volmit.iris.scaffold.parallel.BurstExecutor;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.B;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import net.querz.mca.Chunk;
import net.querz.mca.MCAFile;
import net.querz.mca.MCAUtil;
import net.querz.mca.Section;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.StringTag;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;

public class DirectWorldWriter {
    private final File worldFolder;
    private final KMap<Long, MCAFile> writeBuffer;

    public DirectWorldWriter(File worldFolder)
    {
        this.worldFolder = worldFolder;
        writeBuffer = new KMap<>();
        new File(worldFolder, "region").mkdirs();
    }

    public void flush()
    {
        BurstExecutor ex = MultiBurst.burst.burst(writeBuffer.size());
        writeBuffer.v().forEach((i) -> ex.queue(i::cleanupPalettesAndBlockStates));
        ex.complete();
        BurstExecutor ex2 = MultiBurst.burst.burst(writeBuffer.size());

        for(Long i : writeBuffer.k())
        {
            int x = Cache.keyX(i);
            int z = Cache.keyZ(i);
            try {
                File f = getMCAFile(x, z);

                if(!f.exists())
                {
                    f.getParentFile().mkdirs();
                    f.createNewFile();
                }

                MCAUtil.write(writeBuffer.get(i), f, true);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        writeBuffer.clear();
    }

    public File getMCAFile(int x, int z)
    {
        return new File(worldFolder, "region/r." + x + "." + z + ".mca");
    }

    public BlockData getBlockData(CompoundTag tag)
    {
        String p = tag.getString("Name");

        if(tag.containsKey("Properties"))
        {
            CompoundTag props = tag.getCompoundTag("Properties");
            p += "[";
            KList<String> m = new KList<>();

            for(String i : props.keySet())
            {
                m.add(i + "=" + props.getString(i));
            }

            p += m.toString(",") + "]";
        }

        return B.get(p);
    }

    public CompoundTag getCompound(BlockData blockData)
    {
        CompoundTag s = new CompoundTag();
        NamespacedKey key = blockData.getMaterial().getKey();
        s.putString("Name", key.getNamespace() + ":" + key.getKey());

        String data = blockData.getAsString(true);

        if(data.contains("["))
        {
            String raw = data.split("\\Q[\\E")[1].replaceAll("\\Q]\\E", "");
            CompoundTag props = new CompoundTag();
            if(raw.contains(","))
            {
                for(String i : raw.split("\\Q,\\E"))
                {
                    String[] m = i.split("\\Q=\\E");
                    String k = m[0];
                    String v = m[1];
                    props.put(k, new StringTag(v));
                }
            }

            else
            {
                String[] m = raw.split("\\Q=\\E");
                String k = m[0];
                String v = m[1];
                props.put(k, new StringTag(v));
            }
            s.put("Properties", props);
        }

        return s;
    }

    public BlockData getBlockData(int x, int y, int z)
    {
        try
        {
            CompoundTag tag = getChunkSection(x >> 4, y >> 4, z >> 4).getBlockStateAt(x & 15, y & 15, z & 15);

            if(tag == null)
            {
                return B.get("AIR");
            }

            return getBlockData(tag);
        }

        catch(Throwable e)
        {

        }
        return B.get("AIR");
    }

    public void setBlockData(int x, int y, int z, BlockData data)
    {
        getChunkSection(x >> 4, y >> 4, z >> 4).setBlockStateAt(x & 15, y & 15, z & 15, getCompound(data), false);
    }

    public void setBiome(int x, int y, int z, Biome biome)
    {
        getChunk(x>>4, z>>4).setBiomeAt(x&15, y, z &15, INMS.get().getBiomeId(biome));
    }

    public Section getChunkSection(int x, int y, int z)
    {
        Chunk c = getChunk(x, z);
        Section s = c.getSection(y);

        if(s == null)
        {
            s = Section.newSection();
            c.setSection(y, s);
        }

        return s;
    }

    public Chunk getChunk(int x, int z)
    {
        MCAFile mca = getMCA(x >> 5, z >> 5);
        Chunk c = mca.getChunk(x & 31, z & 31);

        if(c == null)
        {
            c = Chunk.newChunk();
            mca.setChunk(x&31, z&31, c);
        }

        return c;
    }

    public MCAFile getMCA(int x, int z)
    {
        long key = Cache.key(x, z);
        MCAFile mca = writeBuffer.get(key);

        if(mca != null)
        {
            return mca;
        }

        File f = getMCAFile(x, z);
        mca = new MCAFile(x, z);
        if(f.exists())
        {
            try {
                mca = MCAUtil.read(f);
            } catch (IOException e) {
                e.printStackTrace();
                Iris.warn("Failed to read RandomAccessFile " + f.getAbsolutePath() + ", assuming empty region!");
            }
        }

        writeBuffer.put(key, mca);
        return mca;
    }
}
