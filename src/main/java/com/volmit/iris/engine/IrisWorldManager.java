/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.engine.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedWorldManager;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;

public class IrisWorldManager extends EngineAssignedWorldManager {
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

    @ChunkCoordinates
    @Override
    public void spawnInitialEntities(Chunk c) {
        RNG rng = new RNG(Cache.key(c));

        getEngine().getParallaxAccess().getEntitiesR(c.getX(), c.getZ()).iterateSync((x, y, z, e) -> {
            if (e != null) {
                IrisEntity en = getData().getEntityLoader().load(e);

                if (en != null) {
                    en.spawn(getEngine(), new Location(c.getWorld(), x + (c.getX() << 4), y, z + (c.getZ() << 4)));
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
    public void onEntitySpawn(EntitySpawnEvent e) {
        if (getTarget().getWorld() == null || !e.getEntity().getWorld().equals(getTarget().getWorld().realWorld())) {
            return;
        }

        try {
            if (!IrisSettings.get().getGenerator().isSystemEntitySpawnOverrides()) {
                return;
            }

            int x = e.getEntity().getLocation().getBlockX();
            int y = e.getEntity().getLocation().getBlockY();
            int z = e.getEntity().getLocation().getBlockZ();

            J.a(() ->
            {
                if (spawnable) {
                    IrisDimension dim = getDimension();
                    IrisRegion region = getEngine().getRegion(x, z);
                    IrisBiome above = getEngine().getSurfaceBiome(x, z);
                    IrisBiome bbelow = getEngine().getBiome(x, y, z);
                    if (above.getLoadKey().equals(bbelow.getLoadKey())) {
                        bbelow = null;
                    }

                    IrisBiome below = bbelow;

                    J.s(() ->
                    {
                        if (below != null) {
                            if (trySpawn(below.getEntitySpawnOverrides(), e)) {
                                return;
                            }
                        }

                        if (trySpawn(above.getEntitySpawnOverrides(), e)) {
                            return;
                        }

                        if (trySpawn(region.getEntitySpawnOverrides(), e)) {
                            return;
                        }

                        if (trySpawn(dim.getEntitySpawnOverrides(), e)) {
                            return;
                        }
                    });
                }
            });
        } catch (Throwable ee) {
            Iris.reportError(ee);
        }
    }

    private boolean trySpawn(KList<IrisEntitySpawnOverride> s, EntitySpawnEvent e) {
        for (IrisEntitySpawnOverride i : s) {
            spawnable = false;

            if (i.on(getEngine(), e.getLocation(), e.getEntityType(), e) != null) {
                e.setCancelled(true);
                e.getEntity().remove();
                return true;
            } else {
                spawnable = true;
            }
        }

        return false;
    }

    @ChunkCoordinates
    private void trySpawn(KList<IrisEntityInitialSpawn> s, Chunk c, RNG rng) {
        for (IrisEntityInitialSpawn i : s) {
            i.spawn(getEngine(), c, rng);
        }
    }

    @Override
    public void onBlockBreak(BlockBreakEvent e) {
        if(e.getBlock().getWorld().equals(getTarget().getWorld().realWorld()) && getEngine().contains(e.getBlock().getLocation()))
        {
            KList<ItemStack> d = new KList<>();
            Runnable drop = () -> J.s(() -> d.forEach((i) -> e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), i)));
            IrisBiome b = getEngine().getBiome(e.getBlock().getLocation());

            for(IrisBlockDrops i : b.getBlockDrops())
            {
                if(i.shouldDropFor(e.getBlock().getBlockData(), getData()))
                {
                    if(i.isReplaceVanillaDrops())
                    {
                        e.setDropItems(false);
                    }

                    i.fillDrops(false, d);

                    if(i.isSkipParents())
                    {
                        drop.run();
                        return;
                    }
                }
            }

            IrisRegion r = getEngine().getRegion(e.getBlock().getLocation());

            for(IrisBlockDrops i : r.getBlockDrops())
            {
                if(i.shouldDropFor(e.getBlock().getBlockData(), getData()))
                {
                    if(i.isReplaceVanillaDrops())
                    {
                        e.setDropItems(false);
                    }

                    i.fillDrops(false, d);

                    if(i.isSkipParents())
                    {
                        drop.run();
                        return;
                    }
                }
            }

            for(IrisBlockDrops i : getEngine().getDimension().getBlockDrops())
            {
                if(i.shouldDropFor(e.getBlock().getBlockData(), getData()))
                {
                    if(i.isReplaceVanillaDrops())
                    {
                        e.setDropItems(false);
                    }

                    i.fillDrops(false, d);

                    if(i.isSkipParents())
                    {
                        drop.run();
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent e) {

    }
}
