package com.volmit.iris.scaffold.engine;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.manager.gui.Renderer;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisObjectPlacement;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.scaffold.parallax.ParallaxAccess;

public interface GeneratorAccess extends DataProvider, Renderer
{
    public IrisRegion getRegion(int x, int z);

    public ParallaxAccess getParallaxAccess();

    public IrisDataManager getData();

    public IrisBiome getCaveBiome(int x, int z);

    public IrisBiome getSurfaceBiome(int x, int z);

    public int getHeight(int x, int z);

    public default IrisBiome getBiome(int x, int y, int z)
    {
        if(y <= getHeight(x, z) - 2)
        {
            return getCaveBiome(x, z);
        }

        return getSurfaceBiome(x, z);
    }
    public default PlacedObject getObjectPlacement(int x, int y, int z)
    {
        String objectAt = getParallaxAccess().getObject(x, y, z);

        if(objectAt == null || objectAt.isEmpty())
        {
            return null;
        }

        String[] v = objectAt.split("\\Q@\\E");
        String object = v[0];
        int id = Integer.parseInt(v[1]);
        IrisRegion region = getRegion(x, z);

        for(IrisObjectPlacement i : region.getObjects())
        {
            if(i.getPlace().contains(object))
            {
                return new PlacedObject(i, getData().getObjectLoader().load(object), id, x, z);
            }
        }

        IrisBiome biome = getBiome(x, y, z);

        for(IrisObjectPlacement i : biome.getObjects())
        {
            if(i.getPlace().contains(object))
            {
                return new PlacedObject(i, getData().getObjectLoader().load(object), id, x, z);
            }
        }

        return new PlacedObject(null, getData().getObjectLoader().load(object), id, x, z);
    }

    public int getCacheID();
}
