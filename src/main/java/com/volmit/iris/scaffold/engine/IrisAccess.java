package com.volmit.iris.scaffold.engine;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisBiome;

public interface IrisAccess extends Hotloadable {

    public int getGenerated();

    public IrisBiome getBiome(int x, int y, int z);

    public IrisBiome getCaveBiome(int x, int y, int z);

    public IrisBiome getBiome(int x, int z);

    public IrisBiome getCaveBiome(int x, int z);

    public GeneratorAccess getEngineAccess(int y);

    public IrisDataManager getData();

    public int getHeight(int x, int y, int z);

    public IrisBiome getAbsoluteBiome(int x, int y, int z);

    public int getThreadCount();

    public void changeThreadCount(int m);

    public void regenerate(int x, int z);

    public void close();

    public boolean isClosed();

    public EngineTarget getTarget();

    public EngineCompound getCompound();

    public boolean isFailing();

    public boolean isStudio();
}
