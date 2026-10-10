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

import art.arcane.iris.builtin.spawn.EntityDefinition;
import art.arcane.iris.builtin.spawn.EntitySpawn;
import art.arcane.iris.builtin.spawn.SpawnMarker;
import art.arcane.iris.builtin.spawn.SpawnSurface;
import art.arcane.iris.builtin.spawn.Spawner;
import art.arcane.iris.builtin.spawn.SpawnerSettings;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.builtin.loot.LootResolver;
import art.arcane.iris.engine.loot.LootItems;
import art.arcane.iris.engine.object.value.IRare;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.spawn.SpawnResources.KeyedSpawner;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import com.volmit.config.Range;
import com.volmit.entity.EntitySpec;
import com.volmit.service.EntitySpawns;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;

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

    @Override
    public Optional<Plan> plan(PackWorld world, Site site) {
        Engine engine = engine(world);
        return switch (site) {
            case Chunk chunk -> planChunk(world, engine, chunk);
            case Marker marker -> planMarker(world, engine, marker);
        };
    }

    @Override
    public void spawned(PackWorld world, Plan plan, int count) {
        if (count <= 0) {
            return;
        }
        Spawner spawner = SpawnResources.spawner(engine(world).getData(), plan.spawner());
        if (spawner != null) {
            SpawnerGate.spawned(plan.spawner(), spawner, engine(world), plan.chunkX(), plan.chunkZ());
        }
    }

    @Override
    public Optional<EntitySpec> entity(PackWorld world, String key) {
        EntityDefinition entity = SpawnResources.entity(engine(world).getData(), key);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(resolution(world, engine(world)).resolve(entity, entityRandom(world).rng()));
    }

    @Override
    public Optional<MarkerRule> marker(PackWorld world, String tag) {
        if (tag.equals(CAVE_FLOOR_MARKER) || tag.equals(CAVE_CEILING_MARKER)) {
            return Optional.empty();
        }
        Engine engine = engine(world);
        SpawnMarker marker = SpawnResources.marker(engine.getData(), tag);
        if (marker == null) {
            return Optional.empty();
        }

        List<String> spawners = new ArrayList<>();
        for (String key : marker.spawners()) {
            if (SpawnResources.spawner(engine.getData(), key) == null) {
                world.log().error("Cannot load spawner: " + key + " for marker " + tag, null);
                continue;
            }
            spawners.add(key);
        }
        return Optional.of(new MarkerRule(spawners, marker.emptyAbove(), marker.removeOnChange()));
    }

    private Optional<Plan> planChunk(PackWorld world, Engine engine, Chunk chunk) {
        Predicate<KeyedSpawner> canSpawn = keyed -> SpawnerGate.canSpawn(keyed.key(), keyed.spawner(), engine, chunk.chunkX(), chunk.chunkZ());
        int blockX = PowerOfTwoCoordinates.chunkToBlock(chunk.chunkX());
        int blockZ = PowerOfTwoCoordinates.chunkToBlock(chunk.chunkZ());
        IrisBiome centreBiome = engine.getSurfaceBiome(blockX + BLOCKS_TO_CHUNK_CENTRE, blockZ + BLOCKS_TO_CHUNK_CENTRE);
        IrisRegion region = engine.getRegion(blockX, blockZ);
        IrisBiome biome = engine.getSurfaceBiome(blockX, blockZ);

        List<KeyedSpawner> candidates = Stream.concat(
                        shuffled(engine, engine.getDimension()).stream()
                                .filter(canSpawn).filter(keyed -> SpawnerGate.suits(keyed.spawner(), centreBiome)),
                        Stream.concat(
                                shuffled(engine, region).stream().filter(canSpawn),
                                shuffled(engine, biome).stream().filter(canSpawn)))
                .filter(keyed -> chunk.livingEntities() < keyed.spawner().maxEntitiesPerChunk())
                .toList();

        List<Candidate> spawns = candidates.stream().flatMap(keyed -> spawnsOf(keyed, chunk.initial())).collect(Collectors.toList());
        Candidate chosen = IRare.expandWeighted(spawns).getRandom();
        if (chosen == null) {
            return Optional.empty();
        }
        return planChunkSpawn(world, engine, chunk, chosen);
    }

    private Optional<Plan> planChunkSpawn(PackWorld world, Engine engine, Chunk chunk, Candidate chosen) {
        EntitySpawn spawn = chosen.spawn();
        int count = LootResolver.inclusive(RNG.r, spawn.minSpawns(), spawn.maxSpawns());
        List<Attempt> attempts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = (chunk.chunkX() * CHUNK_SIZE) + RNG.r.i(CHUNK_SIZE);
            int z = (chunk.chunkZ() * CHUNK_SIZE) + RNG.r.i(CHUNK_SIZE);
            BlockPosition at = spawnPosition(engine, chosen.spawner(), chunk.chunkX(), chunk.chunkZ(), x, z);
            if (at != null) {
                addAttempt(attempts, world, engine, chosen, at, true);
            }
        }
        return attempts.isEmpty() ? Optional.empty() : Optional.of(new Plan(chosen.key(), chunk.chunkX(), chunk.chunkZ(), false, attempts));
    }

    /** Where a spawner of its group puts an entity at the column; null when it has no place for one. */
    private static BlockPosition spawnPosition(Engine engine, Spawner spawner, int chunkX, int chunkZ, int x, int z) {
        int height = engine.getHeight(x, z, true);
        int heightWithFluid = engine.getHeight(x, z, false);
        return switch (spawner.group()) {
            case NORMAL -> new BlockPosition(x, heightWithFluid + 1, z);
            case CAVE -> caveSpawnPosition(engine, chunkX, chunkZ);
            case UNDERWATER, BEACH -> new BlockPosition(x, RNG.r.i(height + 1, heightWithFluid), z);
        };
    }

    static BlockPosition caveSpawnPosition(Engine engine, int chunkX, int chunkZ) {
        if (J.isFolia()) {
            return null;
        }
        KList<IrisPosition> floors = engine.getMantle().findMarkers(chunkX, chunkZ, MarkerMatter.CAVE_FLOOR);
        IrisPosition floor = floors.getRandom(RNG.r);
        return floor == null ? null : new BlockPosition(floor.getX(), floor.getY() - engine.getMinHeight() + AIR_ABOVE_MARKER, floor.getZ());
    }

    private Optional<Plan> planMarker(PackWorld world, Engine engine, Marker marker) {
        Spawner spawner = SpawnResources.spawner(engine.getData(), marker.spawner());
        SpawnMarker spawnMarker = SpawnResources.marker(engine.getData(), marker.marker());
        if (spawner == null) {
            return Optional.empty();
        }

        List<EntitySpawn> candidates = marker.initial() ? spawner.initialSpawns() : spawner.spawns();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        EntitySpawn spawn = IRare.expandWeighted(candidates.stream().map(RareSpawn::new).toList()).getRandom().spawn();

        BlockPosition position = marker.position();
        int chunkX = PowerOfTwoCoordinates.blockToChunkFloor(position.x());
        int chunkZ = PowerOfTwoCoordinates.blockToChunkFloor(position.z());
        if (!SpawnerGate.canSpawn(marker.spawner(), spawner, engine, chunkX, chunkZ)) {
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
            addAttempt(attempts, world, engine, new Candidate(marker.spawner(), spawner, spawn), above, false);
        }
        return attempts.isEmpty() ? Optional.empty() : Optional.of(new Plan(marker.spawner(), chunkX, chunkZ, exhaustsMarker, attempts));
    }

    private static boolean shouldExhaust(SpawnMarker marker) {
        return marker.exhaustionChance() > RNG.r.nextDouble();
    }

    private void addAttempt(List<Attempt> attempts, PackWorld world, Engine engine, Candidate candidate, BlockPosition at, boolean checkSurface) {
        EntityDefinition entity = SpawnResources.entity(engine.getData(), candidate.spawn().entity());
        if (entity == null) {
            world.log().debug("      You are trying to spawn an entity that does not exist!");
            return;
        }

        Range light = candidate.spawner().allowedLightLevels();
        EntitySpec spec = resolution(world, engine).resolve(entity, entityRandom(world).rng());
        attempts.add(new Attempt(at, spec, surfaceOf(entity.surface()),
                (int) Math.ceil(light.min()), (int) Math.floor(light.max()), checkSurface));
    }

    private static EntityResolution resolution(PackWorld world, Engine engine) {
        LootItems items = new LootItems(world.resources().items(), engine.isStudio());
        return new EntityResolution(items, engine.getWorld().platformWorld(), world.state(EffectTimers.class, ignored -> new EffectTimers()));
    }

    private static EntityRandom entityRandom(PackWorld world) {
        return world.state(EntityRandom.class, EntityRandom::new);
    }

    private static Surface surfaceOf(SpawnSurface surface) {
        return Surface.valueOf(surface.name());
    }

    /** The spawners an area lists, in a random order. */
    private static List<KeyedSpawner> shuffled(Engine engine, IrisRegistrant owner) {
        List<String> keys = owner.getModuleSettings().find(SpawnerSettings.class).map(SpawnerSettings::spawners).orElse(List.of());
        return new KList<>(SpawnResources.spawners(engine.getData(), keys)).shuffleCopy(RNG.r);
    }

    private static Stream<Candidate> spawnsOf(KeyedSpawner keyed, boolean initial) {
        List<EntitySpawn> spawns = initial ? keyed.spawner().initialSpawns() : keyed.spawner().spawns();
        return spawns.stream().map(spawn -> new Candidate(keyed.key(), keyed.spawner(), spawn));
    }

    /** A spawn of a spawner, which picks as often as the spawn's rarity says. */
    private record Candidate(String key, Spawner spawner, EntitySpawn spawn) implements IRare {
        @Override
        public int getRarity() {
            return spawn.rarity();
        }
    }

    /** A spawn that picks as often as its rarity says. */
    private record RareSpawn(EntitySpawn spawn) implements IRare {
        @Override
        public int getRarity() {
            return spawn.rarity();
        }
    }

    private static Engine engine(PackWorld world) {
        return IrisPackWorld.of(world).engine();
    }

    /** The random stream entities are made from, seeded from the world like Iris always seeded it; one per world. */
    private static final class EntityRandom {
        private final RNG rng;

        private EntityRandom(PackWorld world) {
            this.rng = new RNG(IrisPackWorld.of(world).engine().getSeedManager().getEntity());
        }

        private RNG rng() {
            return rng;
        }
    }
}
