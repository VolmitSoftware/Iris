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
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedWorldManager;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Chunk;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class IrisWorldManager extends EngineAssignedWorldManager {
    private boolean spawnable;
    private final int art;
    private final KMap<UUID, Long> spawnCooldowns;
    private int entityCount = 0;
    private ChronoLatch cl = new ChronoLatch(5000);

    public IrisWorldManager(Engine engine) {
        super(engine);
        spawnCooldowns = new KMap<>();
        spawnable = true;
        art = J.ar(this::onAsyncTick, 7);
    }

    private void onAsyncTick() {
        if (!getEngine().getWorld().hasRealWorld()) {
            return;
        }

        if ((double) entityCount / (getEngine().getWorld().realWorld().getLoadedChunks().length+1) > 1)
        {
            return;
        }

        if(cl.flip())
        {
            J.s(() -> entityCount = getEngine().getWorld().realWorld().getEntities().size());
        }

        int biomeBaseCooldownSeconds = 1;
        int biomeSpawnedCooldownSeconds = 0;
        int biomeNotSpawnedCooldownSeconds = 1;
        int actuallySpawned = 0;

        for(UUID i : spawnCooldowns.k())
        {
            if(M.ms() - spawnCooldowns.get(i) > TimeUnit.SECONDS.toMillis(biomeBaseCooldownSeconds))
            {
                spawnCooldowns.remove(i);
                Iris.debug("Biome " + i.toString() + " is off cooldown");
            }
        }

        KMap<UUID, KList<Chunk>> data = mapChunkBiomes();
        int spawnBuffer = 32;

        Iris.debug("Checking " + data.size() + " Loaded Biomes for new spawns...");

        for(UUID i : data.k().shuffleCopy(RNG.r))
        {
            if(spawnCooldowns.containsKey(i))
            {
                continue;
            }

            if(spawnBuffer-- < 0)
            {
                break;
            }

            Iris.debug("  Spawning for " + i.toString());

            for(int ig = 0; ig < data.get(i).size() / 8; ig++)
            {
                boolean g = spawnIn(data.get(i).getRandom(), i);
                spawnCooldowns.put(i, g ?
                        (M.ms() + TimeUnit.SECONDS.toMillis(biomeSpawnedCooldownSeconds)) :
                        (M.ms() + TimeUnit.SECONDS.toMillis(biomeNotSpawnedCooldownSeconds)));

                if(g)
                {
                    actuallySpawned++;
                }
            }
        }

        if(actuallySpawned <= 0)
        {
            J.sleep(5000);
        }
    }

    private boolean spawnIn(Chunk c, UUID id) {
        if(c.getEntities().length > 2)
        {
            Iris.debug("    Not spawning in " + id.toString() + " (" + c.getX() + ", " + c.getZ() + "). More than 2 entities in this chunk.");
            return false;
        }

        return new KList<Supplier<Boolean>>(() -> {
            IrisBiome biome = getEngine().getSurfaceBiome(c.getX() << 4, c.getZ() << 4);

            for(IrisSpawner i : getData().getSpawnerLoader().loadAll(biome.getEntitySpawners()).shuffleCopy(RNG.r))
            {
                if(i.spawnInChunk(getEngine(), c))
                {
                    Iris.debug("  Spawning Biome Entities in Chunk " + c.getX() + "," + c.getZ() + " Biome ID: " + id);
                    return true;
                }
            }

            return false;
        }, () -> {
            IrisRegion region = getEngine().getRegion(c.getX() << 4, c.getZ() << 4);

            for(IrisSpawner i : getData().getSpawnerLoader().loadAll(region.getEntitySpawners()).shuffleCopy(RNG.r))
            {
                if(i.spawnInChunk(getEngine(), c))
                {
                    Iris.debug("  Spawning Region Entities in Chunk " + c.getX() + "," + c.getZ() + " Biome ID: " + id);
                    return true;
                }
            }

            return false;
        }, () -> {
            for(IrisSpawner i : getData().getSpawnerLoader().loadAll(getDimension().getEntitySpawners()).shuffleCopy(RNG.r))
            {
                if(i.spawnInChunk(getEngine(), c))
                {
                    Iris.debug("    Spawning Dimension Entities in Chunk " + c.getX() + "," + c.getZ() + " Biome ID: " + id);
                    return true;
                }
            }

            return false;
        }).getRandom().get();
    }

    public KMap<UUID, KList<Chunk>> mapChunkBiomes()
    {
        KMap<UUID, KList<Chunk>> data = new KMap<>();

        for(Chunk i : getEngine().getWorld().realWorld().getLoadedChunks())
        {
            data.compute(getEngine().getBiomeID(i.getX() << 4, i.getZ() << 4),
                    (k,v) -> v != null ? v : new KList<>()).add(i);
        }

        return data;
    }

    @Override
    public void onTick() {

    }

    @Override
    public void onSave() {
        getEngine().getParallax().saveAll();
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
    private void trySpawn(KList<IrisEntitySpawn> s, Chunk c, RNG rng) {
        for (IrisEntitySpawn i : s) {
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

    @Override
    public void close()
    {
        super.close();
        J.car(art);
    }
}
