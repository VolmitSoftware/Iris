package com.volmit.iris.generator;

import com.volmit.iris.IrisSettings;
import com.volmit.iris.object.*;
import com.volmit.iris.scaffold.cache.Cache;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineAssignedWorldManager;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

public class IrisWorldManager  extends EngineAssignedWorldManager {
    private boolean spawnable;

    public IrisWorldManager(Engine engine) {
        super(engine);
        spawnable = true;
    }

    @Override
    public void onTick() {

    }

    @Override
    public void onSave() {
        getEngine().getParallax().saveAll();
    }

    @Override
    public void spawnInitialEntities(Chunk c) {
        RNG rng = new RNG(Cache.key(c));

        getEngine().getParallaxAccess().getEntitiesR(c.getX(), c.getZ()).iterateSync((x,y,z,e) -> {
            if(e != null)
            {
                IrisEntity en = getData().getEntityLoader().load(e);

                if(en != null){
                    en.spawn(getEngine(), new Location(c.getWorld(), x+(c.getX()<<4),y,z+(c.getZ()<<4)));
                }
            }
        });

        int x = (c.getX() * 16) + rng.nextInt(15);
        int z = (c.getZ() * 16) + rng.nextInt(15);
        int y = getEngine().getHeight(x, z) + 1;
        IrisDimension dim = getDimension();
        IrisRegion region = getEngine().getRegion(x, z);
        IrisBiome above = getEngine().getSurfaceBiome(x, z);
        trySpawn(above.getEntityInitialSpawns(), c, rng);
        trySpawn(region.getEntityInitialSpawns(), c, rng);
        trySpawn(dim.getEntityInitialSpawns(), c, rng);


    }

    @Override
    public void onEntitySpawn(EntitySpawnEvent e)
    {
        if(getTarget().getWorld() == null || !getTarget().getWorld().equals(e.getEntity().getWorld()))
        {
            return;
        }

        try
        {
            if(!IrisSettings.get().getGenerator().isSystemEntitySpawnOverrides())
            {
                return;
            }

            int x = e.getEntity().getLocation().getBlockX();
            int y = e.getEntity().getLocation().getBlockY();
            int z = e.getEntity().getLocation().getBlockZ();

            J.a(() ->
            {
                if(spawnable)
                {
                    IrisDimension dim = getDimension();
                    IrisRegion region = getEngine().getRegion(x, z);
                    IrisBiome above = getEngine().getSurfaceBiome(x, z);
                    IrisBiome bbelow = getEngine().getBiome(x, y, z);
                    if(above.getLoadKey().equals(bbelow.getLoadKey()))
                    {
                        bbelow = null;
                    }

                    IrisBiome below = bbelow;

                    J.s(() ->
                    {
                        if(below != null)
                        {
                            if(trySpawn(below.getEntitySpawnOverrides(), e))
                            {
                                return;
                            }
                        }

                        if(trySpawn(above.getEntitySpawnOverrides(), e))
                        {
                            return;
                        }

                        if(trySpawn(region.getEntitySpawnOverrides(), e))
                        {
                            return;
                        }

                        if(trySpawn(dim.getEntitySpawnOverrides(), e))
                        {
                            return;
                        }
                    });
                }
            });
        }

        catch(Throwable xe)
        {

        }
    }

    private boolean trySpawn(KList<IrisEntitySpawnOverride> s, EntitySpawnEvent e)
    {
        for(IrisEntitySpawnOverride i : s)
        {
            spawnable = false;

            if(i.on(getEngine(), e.getLocation(), e.getEntityType(), e) != null)
            {
                e.setCancelled(true);
                e.getEntity().remove();
                return true;
            }

            else
            {
                spawnable = true;
            }
        }

        return false;
    }

    private void trySpawn(KList<IrisEntityInitialSpawn> s, Chunk c, RNG rng)
    {
        for(IrisEntityInitialSpawn i : s)
        {
            i.spawn(getEngine(), c, rng);
        }
    }

    @Override
    public void onBlockBreak(BlockBreakEvent e) {

    }

    @Override
    public void onBlockPlace(BlockPlaceEvent e) {

    }
}
