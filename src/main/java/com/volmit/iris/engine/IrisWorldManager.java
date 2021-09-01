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
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedWorldManager;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.plugin.Chunks;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Looper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EqualsAndHashCode(callSuper = true)
@Data
public class IrisWorldManager extends EngineAssignedWorldManager {
    private final Looper looper;
    private final int id;
    private final KMap<Long, Long> chunkCooldowns;
    private final KList<Runnable> updateQueue = new KList<>();
    private double energy = 25;
    private int entityCount = 0;
    private final ChronoLatch cl;
    private final ChronoLatch clw;
    private final ChronoLatch ecl;
    private final ChronoLatch cln;
    private final ChronoLatch chunkUpdater;
    private long charge = 0;
    private int actuallySpawned = 0;
    private int cooldown = 0;
    private List<Entity> precount = new KList<>();

    public IrisWorldManager() {
        super(null);
        cl = null;
        ecl = null;
        cln = null;
        clw = null;
        chunkCooldowns = null;
        looper = null;
        chunkUpdater = null;
        id = -1;
    }

    public IrisWorldManager(Engine engine) {
        super(engine);
        chunkUpdater = new ChronoLatch(1000);
        cln = new ChronoLatch(60000);
        cl = new ChronoLatch(3000);
        ecl = new ChronoLatch(250);
        clw = new ChronoLatch(1000, true);
        chunkCooldowns = new KMap<>();
        id = engine.getCacheID();
        energy = 25;
        looper = new Looper() {
            @Override
            protected long loop() {
                if (getEngine().isClosed() || getEngine().getCacheID() != id) {
                    interrupt();
                }

                if (!getEngine().getWorld().hasRealWorld() && clw.flip()) {
                    getEngine().getWorld().tryGetRealWorld();
                }

                if (getEngine().getWorld().hasRealWorld()) {
                    if (chunkUpdater.flip()) {
                        updateChunks();
                    }

                    if (getDimension().isInfiniteEnergy()) {
                        energy += 1000;
                        fixEnergy();
                    }

                    if (M.ms() < charge) {
                        energy += 70;
                        fixEnergy();
                    }

                    if (cln.flip()) {
                        engine.getEngineData().cleanup(getEngine());
                    }

                    if (precount != null) {
                        entityCount = 0;
                        for (Entity i : precount) {
                            if (i instanceof LivingEntity) {
                                if (!i.isDead()) {
                                    entityCount++;
                                }
                            }
                        }

                        precount = null;
                    }

                    if (energy < 650) {
                        if (ecl.flip()) {
                            energy *= 1 + (0.02 * M.clip((1D - getEntitySaturation()), 0D, 1D));
                            fixEnergy();
                        }
                    }

                    onAsyncTick();
                }

                return 250;
            }
        };
        looper.setPriority(Thread.MIN_PRIORITY);
        looper.setName("Iris World Manager");
        looper.start();
    }

    private void updateChunks() {
        for (Player i : getEngine().getWorld().realWorld().getPlayers()) {
            int r = 2;

            Chunk c = i.getLocation().getChunk();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (c.getWorld().isChunkLoaded(c.getX() + x, c.getZ() + z)) {
                        getEngine().updateChunk(c.getWorld().getChunkAt(c.getX() + x, c.getZ() + z));
                    }
                }
            }
        }
    }

    private boolean onAsyncTick() {
        if (getEngine().isClosed()) {
            return false;
        }

        actuallySpawned = 0;

        if (energy < 100) {
            J.sleep(200);
            return false;
        }

        if (!getEngine().getWorld().hasRealWorld()) {
            Iris.debug("Can't spawn. No real world");
            J.sleep(10000);
            return false;
        }

        double epx = getEntitySaturation();
        if (epx > 1) {
            Iris.debug("Can't spawn. The entity per chunk ratio is at " + Form.pc(epx, 2) + " > 100% (total entities " + entityCount + ")");
            J.sleep(5000);
            return false;
        }

        if (cl.flip()) {
            try {
                J.s(() -> precount = getEngine().getWorld().realWorld().getEntities());
            } catch (Throwable e) {
                close();
            }
        }

        int chunkCooldownSeconds = 60;

        for (Long i : chunkCooldowns.k()) {
            if (M.ms() - chunkCooldowns.get(i) > TimeUnit.SECONDS.toMillis(chunkCooldownSeconds)) {
                chunkCooldowns.remove(i);
            }
        }

        int spawnBuffer = RNG.r.i(2, 12);

        Chunk[] cc = getEngine().getWorld().realWorld().getLoadedChunks();
        while (spawnBuffer-- > 0) {
            if (cc.length == 0) {
                Iris.debug("Can't spawn. No chunks!");
                return false;
            }

            Chunk c = cc[RNG.r.nextInt(cc.length)];

            if (!c.isLoaded() || !Chunks.isSafe(c.getWorld(), c.getX(), c.getZ())) {
                continue;
            }

            spawnIn(c, false);
            chunkCooldowns.put(Cache.key(c), M.ms());
        }

        energy -= (actuallySpawned / 2D);
        return actuallySpawned > 0;
    }

    private void fixEnergy() {
        energy = M.clip(energy, 1D, 1000D);
    }

    private void spawnIn(Chunk c, boolean initial) {
        if (initial) {
            energy += 1.2;
        }

        IrisBiome biome = getEngine().getSurfaceBiome(c);
        IrisRegion region = getEngine().getRegion(c);
        //@builder
        IrisEntitySpawn v = spawnRandomly(Stream.concat(Stream.concat(
                                        getData().getSpawnerLoader()
                                                .loadAll(getDimension().getEntitySpawners())
                                                .shuffleCopy(RNG.r).stream()
                                                .filter(this::canSpawn),
                                        getData().getSpawnerLoader().streamAll(getEngine().getMantle()
                                                        .forEachFeature(c).stream()
                                                        .flatMap((o) -> o.getFeature().getEntitySpawners().stream()))
                                                .filter(this::canSpawn))
                                .filter((i) -> i.isValid(biome))
                                .flatMap((i) -> stream(i, initial)),
                        Stream.concat(getData().getSpawnerLoader()
                                        .loadAll(getEngine().getRegion(c.getX() << 4, c.getZ() << 4).getEntitySpawners())
                                        .shuffleCopy(RNG.r).stream().filter(this::canSpawn)
                                        .flatMap((i) -> stream(i, initial)),
                                getData().getSpawnerLoader()
                                        .loadAll(getEngine().getSurfaceBiome(c.getX() << 4, c.getZ() << 4).getEntitySpawners())
                                        .shuffleCopy(RNG.r).stream().filter(this::canSpawn)
                                        .flatMap((i) -> stream(i, initial))))
                .collect(Collectors.toList()))
                .popRandom(RNG.r);

        if (v != null && v.getReferenceSpawner() != null) {
            int maxEntCount = v.getReferenceSpawner().getMaxEntitiesPerChunk();

            for (Entity i : c.getEntities()) {
                if (i instanceof LivingEntity) {
                    if (-maxEntCount <= 0) {
                        return;
                    }
                }
            }

            try {
                spawn(c, v);
            } catch (Throwable e) {
                J.s(() -> spawn(c, v));
            }
        }
        //@done
    }

    private void spawn(Chunk c, IrisEntitySpawn i) {
        boolean allow = true;

        if (!i.getReferenceSpawner().getMaximumRatePerChunk().isInfinite()) {
            allow = false;
            IrisEngineChunkData cd = getEngine().getEngineData().getChunk(c.getX(), c.getZ());
            IrisEngineSpawnerCooldown sc = null;
            for (IrisEngineSpawnerCooldown j : cd.getCooldowns()) {
                if (j.getSpawner().equals(i.getReferenceSpawner().getLoadKey())) {
                    sc = j;
                    break;
                }
            }

            if (sc == null) {
                sc = new IrisEngineSpawnerCooldown();
                sc.setSpawner(i.getReferenceSpawner().getLoadKey());
                cd.getCooldowns().add(sc);
            }

            if (sc.canSpawn(i.getReferenceSpawner().getMaximumRatePerChunk())) {
                sc.spawn(getEngine());
                allow = true;
            }
        }

        if (allow) {
            int s = i.spawn(getEngine(), c, RNG.r);
            actuallySpawned += s;
            if (s > 0) {
                getCooldown(i.getReferenceSpawner()).spawn(getEngine());
                energy -= s * ((i.getEnergyMultiplier() * i.getReferenceSpawner().getEnergyMultiplier() * 1));
            }
        }
    }

    private Stream<IrisEntitySpawn> stream(IrisSpawner s, boolean initial) {
        for (IrisEntitySpawn i : initial ? s.getInitialSpawns() : s.getSpawns()) {
            i.setReferenceSpawner(s);
        }

        return (initial ? s.getInitialSpawns() : s.getSpawns()).stream();
    }

    private KList<IrisEntitySpawn> spawnRandomly(List<IrisEntitySpawn> types) {
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

    public boolean canSpawn(IrisSpawner i) {
        return i.isValid(getEngine().getWorld().realWorld())
                && getCooldown(i).canSpawn(i.getMaximumRate());
    }

    private IrisEngineSpawnerCooldown getCooldown(IrisSpawner i) {
        IrisEngineData ed = getEngine().getEngineData();
        IrisEngineSpawnerCooldown cd = null;

        for (IrisEngineSpawnerCooldown j : ed.getSpawnerCooldowns()) {
            if (j.getSpawner().equals(i.getLoadKey())) {
                cd = j;
            }
        }

        if (cd == null) {
            cd = new IrisEngineSpawnerCooldown();
            cd.setSpawner(i.getLoadKey());
            cd.setLastSpawn(M.ms() - i.getMaximumRate().getInterval());
            ed.getSpawnerCooldowns().add(cd);
        }

        return cd;
    }

    @Override
    public void onTick() {

    }

    @Override
    public void onSave() {
        getEngine().getMantle().save();
    }

    @Override
    public void onChunkLoad(Chunk e, boolean generated) {
        J.a(() -> getMantle().raiseFlag(e.getX(), e.getZ(), MantleFlag.INITIAL_SPAWNED,
                () -> J.a(() -> spawnIn(e, true), RNG.r.i(5, 200))));
        energy += 0.3;
        fixEnergy();
        if (!getMantle().hasFlag(e.getX(), e.getZ(), MantleFlag.UPDATE)) {
            J.a(() -> getEngine().updateChunk(e), 20);
        }
    }

    public Mantle getMantle() {
        return getEngine().getMantle().getMantle();
    }

    @Override
    public void chargeEnergy() {
        charge = M.ms() + 3000;
    }

    @Override
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.getBlock().getWorld().equals(getTarget().getWorld().realWorld())) {
            KList<ItemStack> d = new KList<>();
            Runnable drop = () -> J.s(() -> d.forEach((i) -> e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), i)));
            IrisBiome b = getEngine().getBiome(e.getBlock().getLocation());

            if (dropItems(e, d, drop, b.getBlockDrops(), b)) {
                return;
            }

            IrisRegion r = getEngine().getRegion(e.getBlock().getLocation());

            if (dropItems(e, d, drop, r.getBlockDrops(), b)) {
                return;
            }

            for (IrisBlockDrops i : getEngine().getDimension().getBlockDrops()) {
                if (i.shouldDropFor(e.getBlock().getBlockData(), getData())) {
                    if (i.isReplaceVanillaDrops()) {
                        e.setDropItems(false);
                    }

                    i.fillDrops(false, d);

                    if (i.isSkipParents()) {
                        drop.run();
                        return;
                    }
                }
            }
        }
    }

    private boolean dropItems(BlockBreakEvent e, KList<ItemStack> d, Runnable drop, KList<IrisBlockDrops> blockDrops, IrisBiome b) {
        for (IrisBlockDrops i : blockDrops) {
            if (i.shouldDropFor(e.getBlock().getBlockData(), getData())) {
                if (i.isReplaceVanillaDrops()) {
                    e.setDropItems(false);
                }

                i.fillDrops(false, d);

                if (i.isSkipParents()) {
                    drop.run();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent e) {

    }

    @Override
    public void close() {
        super.close();
        looper.interrupt();
    }

    @Override
    public int getChunkCount() {
        return getEngine().getWorld().realWorld().getLoadedChunks().length;
    }

    @Override
    public double getEntitySaturation() {
        if (!getEngine().getWorld().hasRealWorld()) {
            return 1;
        }

        return (double) entityCount / (getEngine().getWorld().realWorld().getLoadedChunks().length + 1) * 1.28;
    }
}
