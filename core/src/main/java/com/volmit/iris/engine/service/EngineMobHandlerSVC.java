package com.volmit.iris.engine.service;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.engine.IrisWorldManager;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.BlockPosition;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterMarker;
import com.volmit.iris.util.parallel.Sync;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import io.papermc.lib.PaperLib;
import lombok.SneakyThrows;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EngineMobHandlerSVC extends IrisEngineService {
    private static final List<String> CAVE_TAGS = List.of("cave_floor", "cave_ceiling");
    private static final int SAFE_RADIUS = 16;
    private static final int MAX_RADIUS = 128;

    private final AtomicLong currentTick = new AtomicLong();
    private final Sync<Long> sync = new Sync<>();
    private final Set<Player> players = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private transient KList<Entity> entities = new KList<>();
    private transient Thread asyncTicker = null;
    private transient Thread entityCollector = null;
    private transient boolean charge = false;
    private transient int task = -1;

    public EngineMobHandlerSVC(Engine engine) {
        super(engine);
    }

    @Override
    public void onEnable(boolean hotload) {
        if (running.get()) {
            running.set(false);
            cancel(asyncTicker);
            cancel(entityCollector);
        }

        running.set(true);
        charge = hotload;
        asyncTicker = Thread.ofPlatform()
                .name("Iris Async Mob Spawning - " + engine.getWorld().name())
                .priority(9)
                .start(() -> {
                    while (mayLoop()) {
                        try {
                            asyncTick();
                        } catch (Throwable e) {
                            if (isInterrupted(e))
                                return;
                            Iris.error("Error in async tick for " + engine.getWorld().name());
                            e.printStackTrace();

                            J.sleep(100);
                        }
                    }
                });
        entityCollector = Thread.ofVirtual()
                .name("Iris Async Entity Collector - " + engine.getWorld().name())
                .start(() -> {
                    while (mayLoop()) {
                        try {
                            sync.next().get();
                            var world = engine.getWorld().realWorld();
                            if (world == null) continue;
                            J.s(() -> entities = new KList<>(world.getEntities()));
                        } catch (Throwable e) {
                            if (isInterrupted(e))
                                return;

                            Iris.error("Error in async tick for " + engine.getWorld().name());
                            e.printStackTrace();

                            J.sleep(100);
                        }
                    }
                });
        if (task != -1) J.csr(task);
        task = J.sr(() -> sync.advance(currentTick.getAndIncrement()), 0);
    }

    @Override
    public void onDisable(boolean hotload) {
        running.set(false);
        cancel(asyncTicker);
        cancel(entityCollector);
        if (!hotload) J.csr(task);
    }

    private void asyncTick() throws Throwable {
        long tick = sync.next().get();
        var manager = (IrisWorldManager) engine.getWorldManager();
        if (charge) {
            manager.chargeEnergy();
            charge = false;
        }

        var world = engine.getWorld().realWorld();
        if (world == null
                || noSpawning()
                || Boolean.FALSE.equals(world.getGameRuleValue(GameRule.DO_MOB_SPAWNING))
                || players.isEmpty()
                || manager.getEnergy() < 100)
            return;

        var p = PrecisionStopwatch.start();
        var entities = new KList<>(this.entities);

        var conditionCache = new KMap<UUID, KMap<String, Boolean>>();
        var data = engine.getData();
        var invalid = data.getSpawnerLoader()
                .streamAllPossible()
                .filter(Predicate.not(spawner -> spawner.canSpawn(engine)
                        && spawner.getConditions().check(conditionCache, entities)))
                .map(IrisSpawner::getLoadKey)
                .collect(Collectors.toSet());

        var centers = players.stream()
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .map(Player::getLocation)
                .map(BlockPosition::fromLocation)
                .collect(KList.collector())
                .shuffle();

        if (centers.isEmpty())
            return;

        AtomicDouble delta = new AtomicDouble();
        int actuallySpawned = 0;

        KMap<Position2, Pair<Entity[], ChunkSnapshot>> cache = new KMap<>();
        while (centers.isNotEmpty()) {
            var center = centers.pop();
            var spawned = trySpawn(world, invalid, cache, center, delta);
            if (spawned == 0 && p.getMilliseconds() < 1000)
                centers.add(center);
            actuallySpawned += spawned;
        }
        manager.setEnergy(manager.getEnergy() - delta.get());
        if (actuallySpawned > 0) {
            Iris.info("Async Mob Spawning " + world.getName() + " used " + delta + " energy and took " + Form.duration((long) p.getMilliseconds()));
        }
    }

    private int trySpawn(
            World world,
            Set<String> invalid,
            KMap<Position2, Pair<Entity[], ChunkSnapshot>> cache,
            BlockPosition center,
            AtomicDouble delta
    ) {
        var pos = center.randomPoint(MAX_RADIUS, SAFE_RADIUS);
        if (pos.getY() < world.getMinHeight() || pos.getY() >= world.getMaxHeight())
            return 0;

        var chunkPos = new Position2(center.getX() >> 4, center.getZ() >> 4);
        var pair = cache.computeIfAbsent(chunkPos, cPos -> {
            try {
                return PaperLib.getChunkAtAsync(world, cPos.getX(), cPos.getZ(), false)
                        .thenApply(c -> c != null ? new Pair<>(c.getEntities(), c.getChunkSnapshot(false, false, false)) : null)
                        .get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        if (pair == null)
            return 0;

        var spawners = spawnersAt(pair.getB(), pos, invalid);
        spawners.removeIf(i -> !i.canSpawn(engine, chunkPos.getX(), chunkPos.getZ()));

        if (spawners.isEmpty())
            return 0;

        IrisPosition irisPos = new IrisPosition(pos.getX(), pos.getY(), pos.getZ());
        for (var spawner : spawners) {
            var spawns = spawner.getSpawns().copy();
            spawns.removeIf(spawn -> !spawn.check(engine, irisPos, pair.getB()));

            var entity = IRare.pick(spawns, RNG.r.nextDouble());
            if (entity == null)
                continue;

            entity.setReferenceSpawner(spawner);
            entity.setReferenceMarker(spawner.getReferenceMarker());
            int spawned = entity.spawn(engine, irisPos, RNG.r);
            if (spawned == 0)
                continue;

            delta.addAndGet(spawned * ((entity.getEnergyMultiplier() * spawner.getEnergyMultiplier() * 1)));
            spawner.spawn(engine, chunkPos.getX(), chunkPos.getZ());
            if (!spawner.canSpawn(engine))
                invalid.add(spawner.getLoadKey());
            return spawned;
        }
        return 0;
    }

    private KSet<IrisSpawner> spawnersAt(ChunkSnapshot chunk, BlockPosition pos, Set<String> invalid) {
        KSet<IrisSpawner> spawners = markerAt(chunk, pos, invalid);

        var loader = engine.getData().getSpawnerLoader();
        int y = pos.getY() - engine.getWorld().minHeight();
        Stream.concat(engine.getRegion(pos.getX(), pos.getZ())
                                .getEntitySpawners()
                                .stream(),
                        engine.getBiomeOrMantle(pos.getX(), y, pos.getZ())
                                .getEntitySpawners()
                                .stream())
                .filter(Predicate.not(invalid::contains))
                .map(loader::load)
                .forEach(spawners::add);

        return spawners;
    }

    private KSet<IrisSpawner> markerAt(ChunkSnapshot chunk, BlockPosition pos, Set<String> invalid) {
        if (!IrisSettings.get().getWorld().isMarkerEntitySpawningSystem())
            return new KSet<>();

        int y = pos.getY() - engine.getWorld().minHeight();
        Mantle mantle = engine.getMantle().getMantle();
        MatterMarker matter = mantle.get(pos.getX(), y, pos.getZ(), MatterMarker.class);
        if (matter == null || CAVE_TAGS.contains(matter.getTag()))
            return new KSet<>();
        IrisData data = engine.getData();
        IrisMarker mark = data.getMarkerLoader().load(matter.getTag());
        if (mark == null)
            return new KSet<>();

        if (mark.isEmptyAbove()) {
            int x = pos.getX() & 15, z = pos.getZ() & 15;
            boolean remove = chunk.getBlockData(x, pos.getY() + 1, z).getMaterial().isSolid() || chunk.getBlockData(x, pos.getY() + 2, z).getMaterial().isSolid();
            if (remove) {
                mantle.remove(pos.getX(), y, pos.getZ(), MatterMarker.class);
                return new KSet<>();
            }
        }

        KSet<IrisSpawner> spawners = new KSet<>();
        for (String key : mark.getSpawners()) {
            if (invalid.contains(key))
                continue;

            IrisSpawner spawner = data.getSpawnerLoader().load(key);
            if (spawner == null) {
                Iris.error("Cannot load spawner: " + key + " for marker " + matter.getTag());
                continue;
            }

            spawner.setReferenceMarker(mark);
            spawners.add(spawner);
        }
        return spawners;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void on(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (player.getWorld() != engine.getWorld().realWorld())
            return;
        players.add(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void on(PlayerQuitEvent event) {
        players.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerChangedWorldEvent event) {
        var player = event.getPlayer();
        if (player.getWorld() == engine.getWorld().realWorld())
            players.add(player);
        else
            players.remove(player);
    }

    @SneakyThrows
    private static void cancel(Thread thread) {
        if (thread == null || !thread.isAlive()) return;
        thread.interrupt();
        thread.join();
    }

    private static boolean noSpawning() {
        var world = IrisSettings.get().getWorld();
        return !world.isMarkerEntitySpawningSystem() && !world.isAnbientEntitySpawningSystem();
    }

    private boolean mayLoop() {
        return !engine.isClosed() && running.get() && !Thread.interrupted();
    }

    private static boolean isInterrupted(Throwable e) {
        while (e != null) {
            if (e instanceof InterruptedException)
                return true;
            e = e.getCause();
        }
        return false;
    }
}
