package com.volmit.iris.gen.v2.scaffold.parallax;

import com.volmit.iris.gen.v2.scaffold.hunk.Hunk;
import com.volmit.iris.gen.v2.scaffold.hunk.io.HunkCompoundRegion;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;

public class ParallaxWorld implements ParallaxAccess {
    private final KMap<Long, HunkCompoundRegion> loadedRegions;
    private final KList<Long> save;
    private final File folder;
    private final int height;

    public ParallaxWorld(int height, File folder)
    {
        this.height = height;
        this.folder = folder;
        save = new KList<>();
        loadedRegions = new KMap<>();
        folder.mkdirs();
    }

    public synchronized void close()
    {
        for(HunkCompoundRegion i : loadedRegions.v())
        {
            unload(i.getX(), i.getZ());
        }

        save.clear();
        loadedRegions.clear();
    }

    public synchronized void save(HunkCompoundRegion region)
    {
        try {
            region.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isLoaded(int x, int z)
    {
        return loadedRegions.containsKey(key(x,z));
    }

    public synchronized void save(int x, int z)
    {
        if(isLoaded(x,z))
        {
            save(getR(x,z));
        }
    }

    public synchronized void unload(int x, int z)
    {
        long key = key(x,z);

        if(isLoaded(x, z))
        {
            if(save.contains(key))
            {
                save(x,z);
                save.remove(key);
            }

            loadedRegions.remove(key);
        }
    }

    public synchronized HunkCompoundRegion load(int x, int z)
    {
        if(isLoaded(x,z))
        {
            return loadedRegions.get(key(x,z));
        }

        HunkCompoundRegion v = new HunkCompoundRegion(height, folder, x, z);
        loadedRegions.put(key(x,z), v);
        return v;
    }

    public HunkCompoundRegion getR(int x, int z)
    {
        long key = key(x,z);

        HunkCompoundRegion region = loadedRegions.get(key);

        if(region == null)
        {
            region = load(x, z);
        }

        return region;
    }

    public HunkCompoundRegion getRW(int x, int z)
    {
        save.addIfMissing(key(x,z));
        return getR(x,z);
    }

    private long key(int x, int z)
    {
        return (((long)x) << 32) | (((long)z) & 0xffffffffL);
    }

    @Override
    public Hunk<BlockData> getBlocksR(int x, int z) {
        return getR(x>>5, z>>5).getParallaxSlice().getR(x & 31,z & 31);
    }

    @Override
    public synchronized Hunk<BlockData> getBlocksRW(int x, int z) {
        return getRW(x>>5, z>>5).getParallaxSlice().getRW(x & 31,z & 31);
    }

    @Override
    public Hunk<String> getObjectsR(int x, int z) {
        return getR(x>>5, z>>5).getObjectSlice().getR(x & 31,z & 31);
    }

    @Override
    public synchronized Hunk<String> getObjectsRW(int x, int z) {
        return getRW(x>>5, z>>5).getObjectSlice().getRW(x & 31,z & 31);
    }

    @Override
    public Hunk<Boolean> getUpdatesR(int x, int z) {
        return getR(x>>5, z>>5).getUpdateSlice().getR(x & 31,z & 31);
    }

    @Override
    public synchronized Hunk<Boolean> getUpdatesRW(int x, int z) {
        return getRW(x>>5, z>>5).getUpdateSlice().getRW(x & 31,z & 31);
    }
}
