/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.spawn;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.builtin.loot.LootResolver;
import art.arcane.iris.builtin.spawn.EntityDefinition;
import art.arcane.iris.builtin.spawn.EntitySpawn;
import art.arcane.iris.builtin.spawn.SpawnMarker;
import art.arcane.iris.builtin.spawn.SpawnSurface;
import art.arcane.iris.builtin.spawn.Spawner;
import art.arcane.iris.modules.spawn.SpawnerSettings;
import art.arcane.iris.engine.loot.LootItems;
import art.arcane.iris.engine.spawn.SpawnResources.KeyedSpawner;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import com.volmit.config.Level;
import com.volmit.config.Range;
import com.volmit.config.SettingsStack;
import com.volmit.entity.EntitySpec;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.service.EntitySpawns;
import com.volmit.geometry.BlockPosition;
import com.volmit.world.BiomeKind;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * How {@code iris:spawn} spawns entities: the spawners of the dimension, region and surface biome that may fire in a
 * chunk, or the spawners of a marker, each with the entities it spawns and the places it picks for them. What the
 * platform decides (light, the surface, free space, the entity itself) is the host's, the plan only names it.
 */
public final class EntitySpawning implements EntitySpawns {
    private static final int BLOCKS_TO_CHUNK_CENTRE = 8;
    private static final int CHUNK_SIZE = 16;
    private static final String CAVE_FLOOR_MARKER = "cave_floor";
    private static final String CAVE_CEILING_MARKER = "cave_ceiling";
    private static final int AIR_ABOVE_MARKER = 1;
    private static final long COOLDOWN_CLEANUP_INTERVAL_MILLISECONDS = 60_000L;

    @Override
    public Optional<Plan> plan(PackWorld world, Site site) {
        forgetExpiredCooldowns(world);
        return switch (site) {
            case Chunk chunk -> planChunk(world, chunk);
            case Marker marker -> planMarker(world, marker);
        };
    }

    @Override
    public void spawned(PackWorld world, Plan plan, int count) {
        if (count <= 0) {
            return;
        }
        String spawnerKey = plan.spawner().path();
        Spawner spawner = SpawnResources.spawner(world.resources(), spawnerKey);
        if (spawner != null) {
            SpawnerGate.of(world).spawned(spawnerKey, spawner, plan.chunkX(), plan.chunkZ());
        }
    }

    @Override
    public Optional<EntitySpec> entity(PackWorld world, ResourceId id) {
        EntityDefinition entity = SpawnResources.entity(world.resources(), id.path());
        if (entity == null) {
            return Optional.empty();
        }
        return resolution(world).resolve(entity, entityRandom(world).rng());
    }

    @Override
    public Optional<MarkerRule> marker(PackWorld world, ResourceId tag) {
        if (tag.path().equals(CAVE_FLOOR_MARKER) || tag.path().equals(CAVE_CEILING_MARKER)) {
            return Optional.empty();
        }
        SpawnMarker marker = SpawnResources.marker(world.resources(), tag.path());
        if (marker == null) {
            return Optional.empty();
        }

        List<ResourceId> spawners = new ArrayList<>();
        for (String key : marker.spawners()) {
            if (SpawnResources.spawner(world.resources(), key) == null) {
                world.log().error("Cannot load spawner: " + key + " for marker " + tag, null);
                continue;
            }
            spawners.add(world.resources().idOf(key));
        }
        return Optional.of(new MarkerRule(spawners, marker.emptyAbove(), marker.removeOnChange()));
    }

    /** Drops the cooldowns that ran out, now and then: the world saves them across restarts. */
    private static void forgetExpiredCooldowns(PackWorld world) {
        CooldownCleanup cleanup = world.state(CooldownCleanup.class, ignored -> new CooldownCleanup());
        if (!cleanup.due()) {
            return;
        }
        PackResources resources = world.resources();
        SpawnCooldownBook.of(world).forgetExpired(key -> {
            Spawner spawner = SpawnResources.spawner(resources, key);
            return spawner == null ? -1L : spawner.maximumRate().interval();
        });
    }

    private Optional<Plan> planChunk(PackWorld world, Chunk chunk) {
        RunningWorld running = world.running().orElseThrow();
        SpawnerGate gate = SpawnerGate.of(world);
        Predicate<KeyedSpawner> canSpawn = keyed -> gate.canSpawn(keyed.key(), keyed.spawner(), chunk.chunkX(), chunk.chunkZ());
        int blockX = PowerOfTwoCoordinates.chunkToBlock(chunk.chunkX());
        int blockZ = PowerOfTwoCoordinates.chunkToBlock(chunk.chunkZ());
        BiomeKind centreKind = running.layout().surfaceKind(blockX + BLOCKS_TO_CHUNK_CENTRE, blockZ + BLOCKS_TO_CHUNK_CENTRE);
        SettingsStack<SpawnerSettings> column = running.settings().at(SpawnerSettings.class, blockX, blockZ);

        List<KeyedSpawner> candidates = Stream.concat(
                        shuffled(world, column.dimension()).stream()
                                .filter(canSpawn).filter(keyed -> SpawnerGate.suits(keyed.spawner(), centreKind)),
                        Stream.concat(
                                shuffled(world, column.level(Level.REGION)).stream().filter(canSpawn),
                                shuffled(world, column.level(Level.BIOME)).stream().filter(canSpawn)))
                .filter(keyed -> chunk.livingEntities() < keyed.spawner().maxEntitiesPerChunk())
                .toList();

        List<Candidate> spawns = candidates.stream().flatMap(keyed -> spawnsOf(keyed, chunk.initial())).collect(Collectors.toList());
        Candidate chosen = RarityWeighting.expand(spawns, Candidate::rarity).getRandom();
        if (chosen == null) {
            return Optional.empty();
        }
        return planChunkSpawn(world, running, chunk, chosen);
    }

    private Optional<Plan> planChunkSpawn(PackWorld world, RunningWorld running, Chunk chunk, Candidate chosen) {
        EntitySpawn spawn = chosen.spawn();
        int count = LootResolver.inclusive(RNG.r, spawn.minSpawns(), spawn.maxSpawns());
        List<Attempt> attempts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = (chunk.chunkX() * CHUNK_SIZE) + RNG.r.i(CHUNK_SIZE);
            int z = (chunk.chunkZ() * CHUNK_SIZE) + RNG.r.i(CHUNK_SIZE);
            BlockPosition at = spawnPosition(running, chosen.spawner(), chunk.chunkX(), chunk.chunkZ(), x, z);
            if (at != null) {
                addAttempt(attempts, world, chosen, at, true);
            }
        }
        return attempts.isEmpty() ? Optional.empty() : Optional.of(new Plan(world.resources().idOf(chosen.key()), chunk.chunkX(), chunk.chunkZ(), false, attempts));
    }

    /** Where a spawner of its group puts an entity at the column; null when it has no place for one. */
    private static BlockPosition spawnPosition(RunningWorld running, Spawner spawner, int chunkX, int chunkZ, int x, int z) {
        int height = running.highest(x, z, true);
        int heightWithFluid = running.highest(x, z, false);
        return switch (spawner.group()) {
            case NORMAL -> new BlockPosition(x, heightWithFluid + 1, z);
            case CAVE -> caveSpawnPosition(running, chunkX, chunkZ);
            case UNDERWATER, BEACH -> new BlockPosition(x, RNG.r.i(height + 1, heightWithFluid), z);
        };
    }

    static BlockPosition caveSpawnPosition(RunningWorld running, int chunkX, int chunkZ) {
        KList<BlockPosition> floors = new KList<>(running.caveFloors(chunkX, chunkZ));
        BlockPosition floor = floors.getRandom(RNG.r);
        return floor == null ? null : new BlockPosition(floor.x(), floor.y() + AIR_ABOVE_MARKER, floor.z());
    }

    private Optional<Plan> planMarker(PackWorld world, Marker marker) {
        String spawnerKey = marker.spawner().path();
        Spawner spawner = SpawnResources.spawner(world.resources(), spawnerKey);
        SpawnMarker spawnMarker = SpawnResources.marker(world.resources(), marker.marker().path());
        if (spawner == null) {
            return Optional.empty();
        }

        List<EntitySpawn> candidates = marker.initial() ? spawner.initialSpawns() : spawner.spawns();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        EntitySpawn spawn = RarityWeighting.expand(candidates, EntitySpawn::rarity).getRandom();

        BlockPosition position = marker.position();
        int chunkX = PowerOfTwoCoordinates.blockToChunkFloor(position.x());
        int chunkZ = PowerOfTwoCoordinates.blockToChunkFloor(position.z());
        if (!SpawnerGate.of(world).canSpawn(spawnerKey, spawner, chunkX, chunkZ)) {
            return Optional.empty();
        }

        int count = LootResolver.inclusive(RNG.r, spawn.minSpawns(), spawn.maxSpawns());
        if (count <= 0) {
            return Optional.empty();
        }
        boolean exhaustsMarker = spawnMarker != null && shouldExhaust(spawnMarker);
        List<Attempt> attempts = new ArrayList<>();
        BlockPosition above = new BlockPosition(position.x(), position.y() + AIR_ABOVE_MARKER, position.z());
        for (int i = 0; i < count; i++) {
            addAttempt(attempts, world, new Candidate(spawnerKey, spawner, spawn), above, false);
        }
        return attempts.isEmpty() ? Optional.empty() : Optional.of(new Plan(marker.spawner(), chunkX, chunkZ, exhaustsMarker, attempts));
    }

    private static boolean shouldExhaust(SpawnMarker marker) {
        return marker.exhaustionChance() > RNG.r.nextDouble();
    }

    private void addAttempt(List<Attempt> attempts, PackWorld world, Candidate candidate, BlockPosition at, boolean checkSurface) {
        EntityDefinition entity = SpawnResources.entity(world.resources(), candidate.spawn().entity());
        if (entity == null) {
            world.log().debug("      You are trying to spawn an entity that does not exist!");
            return;
        }

        Range light = candidate.spawner().allowedLightLevels();
        resolution(world).resolve(entity, entityRandom(world).rng()).ifPresent(spec -> attempts.add(new Attempt(at, spec,
                surfaceOf(entity.surface()), (int) Math.ceil(light.min()), (int) Math.floor(light.max()), checkSurface)));
    }

    private static EntityResolution resolution(PackWorld world) {
        RunningWorld running = world.running().orElseThrow();
        LootItems items = new LootItems(world.resources().items(), running.debug(), world.log());
        return new EntityResolution(world.resources(), items, running.sky(), world.state(EffectTimers.class, ignored -> new EffectTimers()),
                world.log());
    }

    private static EntityRandom entityRandom(PackWorld world) {
        return world.state(EntityRandom.class, EntityRandom::new);
    }

    private static Surface surfaceOf(SpawnSurface surface) {
        return Surface.valueOf(surface.name());
    }

    /** The spawners the dimension, region or surface biome at the column lists, in a random order. */
    private static List<KeyedSpawner> shuffled(PackWorld world, Optional<SpawnerSettings> declared) {
        List<String> keys = declared.map(SpawnerSettings::spawners).orElse(List.of());
        return new KList<>(SpawnResources.spawners(world.resources(), keys)).shuffleCopy(RNG.r);
    }

    private static Stream<Candidate> spawnsOf(KeyedSpawner keyed, boolean initial) {
        List<EntitySpawn> spawns = initial ? keyed.spawner().initialSpawns() : keyed.spawner().spawns();
        return spawns.stream().map(spawn -> new Candidate(keyed.key(), keyed.spawner(), spawn));
    }

    /** A spawn of a spawner, which picks as often as the spawn's rarity says. */
    private record Candidate(String key, Spawner spawner, EntitySpawn spawn) {
        int rarity() {
            return spawn.rarity();
        }
    }

    /** The random stream entities are made from, seeded from the world like Iris always seeded it; one per world. */
    private static final class EntityRandom {
        private final RNG rng;

        private EntityRandom(PackWorld world) {
            this.rng = new RNG(world.seed(IrisSeedPurposes.ENTITY));
        }

        private RNG rng() {
            return rng;
        }
    }

    /** When the world's spawner cooldowns were last tidied. */
    private static final class CooldownCleanup {
        private final ChronoLatch latch = new ChronoLatch(COOLDOWN_CLEANUP_INTERVAL_MILLISECONDS);

        private boolean due() {
            return latch.flip();
        }
    }
}
