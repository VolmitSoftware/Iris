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
import com.volmit.iris.engine.object.common.IRare;
import com.volmit.iris.engine.object.engine.IrisEngineData;
import com.volmit.iris.engine.object.engine.IrisEngineSpawnerCooldown;
import com.volmit.iris.engine.stream.convert.SelectionStream;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.reflect.V;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Chunk;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IrisWorldManager extends EngineAssignedWorldManager {
    private final int art;
    private final KMap<UUID, Long> spawnCooldowns;
    private int entityCount = 0;
    private final ChronoLatch cl;
    private int actuallySpawned = 0;

    public IrisWorldManager(Engine engine) {
        super(engine);
        cl = new ChronoLatch(5000);
        spawnCooldowns = new KMap<>();
        art = J.ar(this::onAsyncTick, 7);
    }

    private void onAsyncTick() {
        actuallySpawned = 0;
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

        int maxGroups = 3;
        int biomeBaseCooldownSeconds = 15;

        for(UUID i : spawnCooldowns.k())
        {
            if(M.ms() - spawnCooldowns.get(i) > TimeUnit.SECONDS.toMillis(biomeBaseCooldownSeconds))
            {
                spawnCooldowns.remove(i);
            }
        }

        KMap<UUID, KList<Chunk>> data = mapChunkBiomes();
        int spawnBuffer = 32;

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

            for(int ig = 0; ig < data.get(i).size() / 8; ig++)
            {
                spawnIn(data.get(i).getRandom(), i, maxGroups);
                spawnCooldowns.put(i, M.ms());
            }
        }

        if(actuallySpawned <= 0)
        {
            J.sleep(5000);
        }
    }

    private void spawnIn(Chunk c, UUID id, int max) {
        if(c.getEntities().length > 2)
        {
            return;
        }

        //@builder
        puffen(Stream.concat(getData().getSpawnerLoader().loadAll(getDimension().getEntitySpawners())
            .shuffleCopy(RNG.r).stream().filter(this::canSpawn)
            .flatMap(this::stream),
                Stream.concat(getData().getSpawnerLoader()
                    .loadAll(getEngine().getRegion(c.getX() << 4, c.getZ() << 4).getEntitySpawners())
                    .shuffleCopy(RNG.r).stream().filter(this::canSpawn)
                        .flatMap(this::stream),
                        getData().getSpawnerLoader()
                            .loadAll(getEngine().getSurfaceBiome(c.getX() << 4, c.getZ() << 4).getEntitySpawners())
                            .shuffleCopy(RNG.r).stream().filter(this::canSpawn)
                            .flatMap(this::stream)))
                .collect(Collectors.toList()))
            .popRandom(RNG.r, max).forEach((i) -> spawn(c, id, i));
        //@done
    }

    private void spawn(Chunk c, UUID id, IrisEntitySpawn i) {
        if(i.spawn(getEngine(), c, RNG.r))
        {
            actuallySpawned++;
            getCooldown(i.getReferenceSpawner()).spawn(getEngine());
        }
    }

    private Stream<IrisEntitySpawn> stream(IrisSpawner s)
    {
        for(IrisEntitySpawn i : s.getSpawns())
        {
            i.setReferenceSpawner(s);
        }

        return s.getSpawns().stream();
    }

    private KList<IrisEntitySpawn> puffen(List<IrisEntitySpawn> types)
    {
        KList<IrisEntitySpawn> rarityTypes = new KList<>();
        int totalRarity = 0;
        for (IrisEntitySpawn i : types) {
            totalRarity += IRare.get(i);
        }

        for (IrisEntitySpawn i : types) {
            rarityTypes.addMultiple(i, totalRarity / IRare.get(i));
        }

        return rarityTypes;
    }

    public boolean canSpawn(IrisSpawner i)
    {
        return i.isValid(getEngine().getWorld().realWorld()) && getCooldown(i).canSpawn(i.getMaximumRate());
    }

    private IrisEngineSpawnerCooldown getCooldown(IrisSpawner i)
    {
        IrisEngineData ed = getEngine().getEngineData();
        IrisEngineSpawnerCooldown cd = null;

        for (IrisEngineSpawnerCooldown j : ed.getSpawnerCooldowns()) {
            if (j.getSpawner().equals(i.getLoadKey()))
            {
                cd = j;
            }
        }

        if(cd == null)
        {
            cd = new IrisEngineSpawnerCooldown();
            cd.setSpawner(i.getLoadKey());
            cd.setLastSpawn(M.ms() - i.getMaximumRate().getInterval());
            ed.getSpawnerCooldowns().add(cd);
        }

        return cd;
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
