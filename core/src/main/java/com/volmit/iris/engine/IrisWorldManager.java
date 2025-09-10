/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.service.ExternalDataSVC;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedWorldManager;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.mantle.flag.MantleFlag;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterMarker;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.Chunks;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Looper;
import com.volmit.iris.util.scheduling.jobs.QueueJob;
import io.papermc.lib.PaperLib;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EqualsAndHashCode(callSuper = true)
@Data
public class IrisWorldManager extends EngineAssignedWorldManager {
    private final Looper looper;
    private final int id;
    private final KList<Runnable> updateQueue = new KList<>();
    private final ChronoLatch cl;
    private final ChronoLatch clw;
    private final ChronoLatch ecl;
    private final ChronoLatch cln;
    private final ChronoLatch chunkUpdater;
    private final ChronoLatch chunkDiscovery;
    private double energy = 25;
    private int entityCount = 0;
    private long charge = 0;
    private int actuallySpawned = 0;
    private int cooldown = 0;
    private List<Entity> precount = new KList<>();
    private KSet<Position2> injectBiomes = new KSet<>();

    public IrisWorldManager() {
        super(null);
        cl = null;
        ecl = null;
        cln = null;
        clw = null;
        looper = null;
        chunkUpdater = null;
        chunkDiscovery = null;
        id = -1;
    }

    public IrisWorldManager(Engine engine) {
        super(engine);
        chunkUpdater = new ChronoLatch(3000);
        chunkDiscovery = new ChronoLatch(5000);
        cln = new ChronoLatch(60000);
        cl = new ChronoLatch(3000);
        ecl = new ChronoLatch(250);
        clw = new ChronoLatch(1000, true);
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

                if (!IrisSettings.get().getWorld().isMarkerEntitySpawningSystem() && !IrisSettings.get().getWorld().isAnbientEntitySpawningSystem()) {
                    return 3000;
                }

                if (getEngine().getWorld().hasRealWorld()) {
                    if (getEngine().getWorld().getPlayers().isEmpty()) {
                        return 5000;
                    }

                    if (chunkUpdater.flip()) {
                        updateChunks();
                    }

                    if (chunkDiscovery.flip()) {
                        discoverChunks();
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

                return IrisSettings.get().getWorld().getAsyncTickIntervalMS();
            }
        };
        looper.setPriority(Thread.MIN_PRIORITY);
        looper.setName("Iris World Manager");
        looper.start();
    }

    private void discoverChunks() {
        var mantle = getEngine().getMantle().getMantle();
        for (Player i : getEngine().getWorld().realWorld().getPlayers()) {
            int r = 1;

            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    mantle.getChunk(i.getLocation().getChunk()).flag(MantleFlag.DISCOVERED, true);
                }
            }
        }
    }

    private void updateChunks() {
        for (Player i : getEngine().getWorld().realWorld().getPlayers()) {
            int r = 1;

            Chunk c = i.getLocation().getChunk();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (c.getWorld().isChunkLoaded(c.getX() + x, c.getZ() + z) && Chunks.isSafe(getEngine().getWorld().realWorld(), c.getX() + x, c.getZ() + z)) {

                        if (IrisSettings.get().getWorld().isPostLoadBlockUpdates()) {
                            getEngine().updateChunk(c.getWorld().getChunkAt(c.getX() + x, c.getZ() + z));
                        }

                        if (IrisSettings.get().getWorld().isMarkerEntitySpawningSystem()) {
                            Chunk cx = getEngine().getWorld().realWorld().getChunkAt(c.getX() + x, c.getZ() + z);
                            int finalX = c.getX() + x;
                            int finalZ = c.getZ() + z;
                            J.a(() -> getMantle().raiseFlag(finalX, finalZ, MantleFlag.INITIAL_SPAWNED_MARKER,
                                    () -> {
                                        J.a(() -> spawnIn(cx, true), RNG.r.i(5, 200));
                                        getSpawnersFromMarkers(cx).forEach((blockf, spawners) -> {
                                            if (spawners.isEmpty()) {
                                                return;
                                            }

                                            IrisPosition block = new IrisPosition(blockf.getX(), blockf.getY() + getEngine().getWorld().minHeight(), blockf.getZ());
                                            IrisSpawner s = new KList<>(spawners).getRandom();
                                            spawn(block, s, true);
                                        });
                                    }));
                        }
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
            J.sleep(5000);
            return false;
        }

        double epx = getEntitySaturation();
        if (epx > IrisSettings.get().getWorld().getTargetSpawnEntitiesPerChunk()) {
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
        }

        energy -= (actuallySpawned / 2D);
        return actuallySpawned > 0;
    }

    private void fixEnergy() {
        energy = M.clip(energy, 1D, getDimension().getMaximumEnergy());
    }

    private void spawnIn(Chunk c, boolean initial) {
        if (getEngine().isClosed()) {
            return;
        }

        if (initial) {
            energy += 1.2;
        }

        if (IrisSettings.get().getWorld().isMarkerEntitySpawningSystem()) {
            getSpawnersFromMarkers(c).forEach((blockf, spawners) -> {
                if (spawners.isEmpty()) {
                    return;
                }

                IrisPosition block = new IrisPosition(blockf.getX(), blockf.getY() + getEngine().getWorld().minHeight(), blockf.getZ());
                IrisSpawner s = new KList<>(spawners).getRandom();
                spawn(block, s, false);
                J.a(() -> getMantle().raiseFlag(c.getX(), c.getZ(), MantleFlag.INITIAL_SPAWNED_MARKER,
                        () -> spawn(block, s, true)));
            });
        }

        if (!IrisSettings.get().getWorld().isAnbientEntitySpawningSystem()) {
            return;
        }

        //@builder
        Predicate<IrisSpawner> filter = i -> i.canSpawn(getEngine(), c.getX(), c.getZ());
        ChunkCounter counter = new ChunkCounter(c.getEntities());

        IrisBiome biome = getEngine().getSurfaceBiome(c);
        IrisEntitySpawn v = spawnRandomly(Stream.concat(getData().getSpawnerLoader()
                                .loadAll(getDimension().getEntitySpawners())
                                .shuffleCopy(RNG.r)
                                .stream()
                                .filter(filter)
                                .filter((i) -> i.isValid(biome)),
                        Stream.concat(getData()
                                        .getSpawnerLoader()
                                        .loadAll(getEngine().getRegion(c.getX() << 4, c.getZ() << 4).getEntitySpawners())
                                        .shuffleCopy(RNG.r)
                                        .stream()
                                        .filter(filter),
                                getData().getSpawnerLoader()
                                        .loadAll(getEngine().getSurfaceBiome(c.getX() << 4, c.getZ() << 4).getEntitySpawners())
                                        .shuffleCopy(RNG.r)
                                        .stream()
                                        .filter(filter)))
                .filter(counter)
                .flatMap((i) -> stream(i, initial))
                .collect(Collectors.toList()))
                .getRandom();
        //@done
        if (v == null || v.getReferenceSpawner() == null)
            return;

        try {
            spawn(c, v);
        } catch (Throwable e) {
            J.s(() -> spawn(c, v));
        }
    }

    private void spawn(Chunk c, IrisEntitySpawn i) {
        IrisSpawner ref = i.getReferenceSpawner();
        int s = i.spawn(getEngine(), c, RNG.r);
        actuallySpawned += s;
        if (s > 0) {
            ref.spawn(getEngine(), c.getX(), c.getZ());
            energy -= s * ((i.getEnergyMultiplier() * ref.getEnergyMultiplier() * 1));
        }
    }

    private void spawn(IrisPosition pos, IrisEntitySpawn i) {
        IrisSpawner ref = i.getReferenceSpawner();
        if (!ref.canSpawn(getEngine(), pos.getX() >> 4, pos.getZ() >> 4))
            return;

        int s = i.spawn(getEngine(), pos, RNG.r);
        actuallySpawned += s;
        if (s > 0) {
            ref.spawn(getEngine(), pos.getX() >> 4, pos.getZ() >> 4);
            energy -= s * ((i.getEnergyMultiplier() * ref.getEnergyMultiplier() * 1));
        }
    }

    private Stream<IrisEntitySpawn> stream(IrisSpawner s, boolean initial) {
        for (IrisEntitySpawn i : initial ? s.getInitialSpawns() : s.getSpawns()) {
            i.setReferenceSpawner(s);
            i.setReferenceMarker(s.getReferenceMarker());
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

    @Override
    public void onTick() {

    }

    @Override
    public void onSave() {
        getEngine().getMantle().save();
    }

    public void requestBiomeInject(Position2 p) {
        injectBiomes.add(p);
    }

    @Override
    public void onChunkLoad(Chunk e, boolean generated) {
        if (getEngine().isClosed()) {
            return;
        }

        var ref = new WeakReference<>(e.getWorld());
        int cX = e.getX(), cZ = e.getZ();
        J.s(() -> {
            World world = ref.get();
            if (world == null || !world.isChunkLoaded(cX, cZ))
                return;
            energy += 0.3;
            fixEnergy();
            getEngine().cleanupMantleChunk(cX, cZ);
        }, IrisSettings.get().getPerformance().mantleCleanupDelay);

        if (generated) {
            //INMS.get().injectBiomesFromMantle(e, getMantle());

            if (!IrisSettings.get().getGenerator().earlyCustomBlocks) return;
            e.addPluginChunkTicket(Iris.instance);
            J.s(() -> {
                var chunk = getMantle().getChunk(e).use();
                int minY = getTarget().getWorld().minHeight();
                try {
                    chunk.raiseFlagUnchecked(MantleFlag.CUSTOM, () -> {
                        chunk.iterate(Identifier.class, (x, y, z, v) -> {
                            Iris.service(ExternalDataSVC.class).processUpdate(getEngine(), e.getBlock(x & 15, y + minY, z & 15), v);
                        });
                    });
                } finally {
                    chunk.release();
                    e.removePluginChunkTicket(Iris.instance);
                }
            }, RNG.r.i(20, 60));
        }
    }

    private void spawn(IrisPosition block, IrisSpawner spawner, boolean initial) {
        if (getEngine().isClosed()) {
            return;
        }

        if (spawner == null) {
            return;
        }

        KList<IrisEntitySpawn> s = initial ? spawner.getInitialSpawns() : spawner.getSpawns();
        if (s.isEmpty()) {
            return;
        }

        IrisEntitySpawn ss = spawnRandomly(s).getRandom();
        ss.setReferenceSpawner(spawner);
        ss.setReferenceMarker(spawner.getReferenceMarker());
        spawn(block, ss);
    }

    public Mantle getMantle() {
        return getEngine().getMantle().getMantle();
    }

    @Override
    public void chargeEnergy() {
        charge = M.ms() + 3000;
    }

    @Override
    public void teleportAsync(PlayerTeleportEvent e) {
        if (IrisSettings.get().getWorld().getAsyncTeleport().isEnabled()) {
            e.setCancelled(true);
            warmupAreaAsync(e.getPlayer(), e.getTo(), () -> J.s(() -> {
                ignoreTP.set(true);
                e.getPlayer().teleport(e.getTo(), e.getCause());
                ignoreTP.set(false);
            }));
        }
    }

    private void warmupAreaAsync(Player player, Location to, Runnable r) {
        J.a(() -> {
            int viewDistance = IrisSettings.get().getWorld().getAsyncTeleport().getLoadViewDistance();
            KList<Future<Chunk>> futures = new KList<>();
            for (int i = -viewDistance; i <= viewDistance; i++) {
                for (int j = -viewDistance; j <= viewDistance; j++) {
                    int finalJ = j;
                    int finalI = i;

                    if (to.getWorld().isChunkLoaded((to.getBlockX() >> 4) + i, (to.getBlockZ() >> 4) + j)) {
                        futures.add(CompletableFuture.completedFuture(null));
                        continue;
                    }

                    futures.add(MultiBurst.burst.completeValue(()
                            -> PaperLib.getChunkAtAsync(to.getWorld(),
                            (to.getBlockX() >> 4) + finalI,
                            (to.getBlockZ() >> 4) + finalJ,
                            true, IrisSettings.get().getWorld().getAsyncTeleport().isUrgent()).get()));
                }
            }

            new QueueJob<Future<Chunk>>() {
                @Override
                public void execute(Future<Chunk> chunkFuture) {
                    try {
                        chunkFuture.get();
                    } catch (InterruptedException | ExecutionException ignored) {

                    }
                }

                @Override
                public String getName() {
                    return "Loading Chunks";
                }
            }.queue(futures).execute(new VolmitSender(player), true, r);
        });
    }

    public Map<IrisPosition, KSet<IrisSpawner>> getSpawnersFromMarkers(Chunk c) {
        Map<IrisPosition, KSet<IrisSpawner>> p = new KMap<>();
        Set<IrisPosition> b = new KSet<>();
        getMantle().iterateChunk(c.getX(), c.getZ(), MatterMarker.class, (x, y, z, t) -> {
            if (t.getTag().equals("cave_floor") || t.getTag().equals("cave_ceiling")) {
                return;
            }

            IrisMarker mark = getData().getMarkerLoader().load(t.getTag());
            IrisPosition pos = new IrisPosition((c.getX() << 4) + x, y, (c.getZ() << 4) + z);

            if (mark.isEmptyAbove()) {
                AtomicBoolean remove = new AtomicBoolean(false);

                try {
                    J.sfut(() -> {
                        if (c.getBlock(x, y + 1, z).getBlockData().getMaterial().isSolid() || c.getBlock(x, y + 2, z).getBlockData().getMaterial().isSolid()) {
                            remove.set(true);
                        }
                    }).get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }

                if (remove.get()) {
                    b.add(pos);
                    return;
                }
            }

            for (String i : mark.getSpawners()) {
                IrisSpawner m = getData().getSpawnerLoader().load(i);
                if (m == null) {
                    Iris.error("Cannot load spawner: " + i + " for marker on " + getName());
                    continue;
                }
                m.setReferenceMarker(mark);

                // This is so fucking incorrect its a joke
                //noinspection ConstantConditions
                if (m != null) {
                    p.computeIfAbsent(pos, (k) -> new KSet<>()).add(m);
                }
            }
        });

        for (IrisPosition i : b) {
            getEngine().getMantle().getMantle().remove(i.getX(), i.getY(), i.getZ(), MatterMarker.class);
        }

        return p;
    }

    @Override
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.getBlock().getWorld().equals(getTarget().getWorld().realWorld())) {
            J.a(() -> {
                MatterMarker marker = getMantle().get(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ(), MatterMarker.class);

                if (marker != null) {
                    if (marker.getTag().equals("cave_floor") || marker.getTag().equals("cave_ceiling")) {
                        return;
                    }

                    IrisMarker mark = getData().getMarkerLoader().load(marker.getTag());

                    if (mark == null || mark.isRemoveOnChange()) {
                        getMantle().remove(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ(), MatterMarker.class);
                    }
                }
            });

            KList<ItemStack> d = new KList<>();
            IrisBiome b = getEngine().getBiome(e.getBlock().getLocation().clone().subtract(0, getEngine().getWorld().minHeight(), 0));
            List<IrisBlockDrops> dropProviders = filterDrops(b.getBlockDrops(), e, getData());

            if (dropProviders.stream().noneMatch(IrisBlockDrops::isSkipParents)) {
                IrisRegion r = getEngine().getRegion(e.getBlock().getLocation());
                dropProviders.addAll(filterDrops(r.getBlockDrops(), e, getData()));
                dropProviders.addAll(filterDrops(getEngine().getDimension().getBlockDrops(), e, getData()));
            }

            dropProviders.forEach(provider -> provider.fillDrops(false, d));

            if (dropProviders.stream().anyMatch(IrisBlockDrops::isReplaceVanillaDrops)) {
                e.setDropItems(false);
            }

            if (d.isNotEmpty()) {
                World w = e.getBlock().getWorld();
                J.s(() -> d.forEach(item -> w.dropItemNaturally(e.getBlock().getLocation().clone().add(.5, .5, .5), item)));
            }
        }
    }

    private List<IrisBlockDrops> filterDrops(KList<IrisBlockDrops> drops, BlockBreakEvent e, IrisData data) {
        return new KList<>(drops.stream().filter(d -> d.shouldDropFor(e.getBlock().getBlockData(), data)).toList());
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

    @Data
    private static class ChunkCounter implements Predicate<IrisSpawner> {
        private final Entity[] entities;
        private transient int index = 0;
        private transient int count = 0;

        @Override
        public boolean test(IrisSpawner spawner) {
            int max = spawner.getMaxEntitiesPerChunk();
            if (max <= count)
                return false;

            while (index < entities.length) {
                if (entities[index++] instanceof LivingEntity) {
                    if (++count >= max)
                        return false;
                }
            }

            return true;
        }
    }
}
